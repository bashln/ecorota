package app.ecorota.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import app.ecorota.BuildConfig
import app.ecorota.data.SettingsRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Libre self-updater backed by the project's public GitHub releases. */
class GitHubUpdateController(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) : UpdateController {

    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = mutableState.asStateFlow()
    override val supported: Boolean = true

    private var started = false
    private var job: Job? = null
    private var checksumUrl: String? = null
    private var downloadedFile: File? = null

    override fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            pruneCachedApks()
        }
        scope.launch {
            var enabled = false
            settingsRepository.settings.collect { settings ->
                if (settings.checkForUpdates && !enabled) {
                    check(manual = false)
                }
                enabled = settings.checkForUpdates
            }
        }
    }

    override fun check(manual: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            mutableState.value = UpdateState.Checking
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchLatest() }
            }
            result.fold(
                onSuccess = { fetched ->
                    if (fetched.update.versionCode <= currentVersionCode) {
                        clearDownload(resetChecksum = true)
                        mutableState.value = UpdateState.UpToDate
                    } else {
                        clearDownload(resetChecksum = true)
                        checksumUrl = fetched.checksumUrl
                        mutableState.value = UpdateState.Available(fetched.update)
                    }
                },
                onFailure = { error ->
                    mutableState.value = if (manual) {
                        UpdateState.Failed(classify(error))
                    } else {
                        UpdateState.Idle
                    }
                },
            )
        }
    }

    override fun download() {
        val update = (mutableState.value as? UpdateState.Available)?.update ?: return
        val checksums = checksumUrl ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            mutableState.value = UpdateState.Downloading(update, 0, null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    downloadApk(update, checksums) { downloaded, total ->
                        mutableState.value = UpdateState.Downloading(update, downloaded, total)
                    }
                }
            }
            result.fold(
                onSuccess = { file ->
                    downloadedFile = file
                    mutableState.value = UpdateState.Ready(update)
                },
                onFailure = { error ->
                    mutableState.value = UpdateState.Failed(classify(error))
                },
            )
        }
    }

    override fun install(context: Context) {
        val file = downloadedFile ?: return
        if (!file.isFile) {
            clearDownload(resetChecksum = false)
            mutableState.value = UpdateState.Failed(UpdateFailure.DOWNLOAD)
            return
        }
        if (!canInstall(context)) {
            openInstallPermission(context)
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            mutableState.value = UpdateState.Failed(UpdateFailure.INSTALL)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { mutableState.value = UpdateState.Failed(UpdateFailure.INSTALL) }
    }

    override fun openInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { mutableState.value = UpdateState.Failed(UpdateFailure.INSTALL) }
    }

    override fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun dismiss() {
        job?.cancel()
        clearDownload(resetChecksum = true)
        mutableState.value = UpdateState.Idle
    }

    private fun fetchLatest(): FetchedUpdate {
        val json = JSONObject(getText(API_LATEST))
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            throw NoReleaseException()
        }
        val versionName = json.optString("tag_name").removePrefix("v")
        val versionCode = parseVersionCode(versionName) ?: throw BadResponseException()

        val assets = json.optJSONArray("assets") ?: throw BadResponseException()
        val apkName = "ecorota-libre-$versionName.apk"
        var apkUrl: String? = null
        var checksums: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            when (asset.optString("name")) {
                apkName -> apkUrl = asset.optString("browser_download_url")
                CHECKSUM_ASSET -> checksums = asset.optString("browser_download_url")
            }
        }
        if (apkUrl.isNullOrBlank() || !isAllowedHttpsUrl(apkUrl)) throw BadResponseException()
        if (checksums.isNullOrBlank() || !isAllowedHttpsUrl(checksums)) throw BadResponseException()

        return FetchedUpdate(
            update = AppUpdate(
                versionName = versionName,
                versionCode = versionCode,
                assetName = apkName,
                downloadUrl = apkUrl,
                releaseNotes = json.optString("body")
                    .take(MAX_NOTES_LENGTH)
                    .ifBlank { null },
            ),
            checksumUrl = checksums,
        )
    }

    private fun downloadApk(
        update: AppUpdate,
        checksumsUrl: String,
        onProgress: (Long, Long?) -> Unit,
    ): File {
        val expected = expectedChecksum(checksumsUrl, update.assetName)
        val directory = File(appContext.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
        pruneCachedApks()
        val safeName = update.assetName.substringAfterLast('/')
        if (!safeName.endsWith(".apk") || safeName.contains("..")) throw DownloadException()
        val file = File(directory, safeName)
        if (file.exists() && !file.delete()) throw DownloadException()

        val connection = openVerifiedHttpsConnection(
            initialUrl = update.downloadUrl,
            accept = "application/octet-stream",
        )
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw DownloadException()

            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_APK_BYTES) throw DownloadException()
            val total = declaredLength.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            throw DownloadException()
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        if (downloaded - reported >= PROGRESS_STEP) {
                            reported = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            if (total != null && downloaded != total) {
                throw DownloadException()
            }
            onProgress(downloaded, total)
            val actual = digest.digest().joinToString("") { byte ->
                "%02x".format(Locale.ROOT, byte)
            }
            if (!actual.equals(expected, ignoreCase = true)) {
                file.delete()
                throw ChecksumException()
            }
            if (!verifyArchive(file, update.versionCode)) {
                file.delete()
                throw SignatureException()
            }
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun expectedChecksum(checksumsUrl: String, assetName: String): String =
        parseChecksumForAsset(getText(checksumsUrl), assetName) ?: throw ChecksumException()

    @Suppress("DEPRECATION")
    private fun verifyArchive(file: File, expectedVersionCode: Int): Boolean {
        val packageManager = appContext.packageManager
        // AOSP API 28-33 leaves archive.signingInfo null unless GET_SIGNATURES is
        // passed alongside GET_SIGNING_CERTIFICATES (b/136784843).
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        if (archive.packageName != appContext.packageName) return false
        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archive)
        if (archiveVersionCode != expectedVersionCode.toLong()) return false
        if (archiveVersionCode <= currentVersionCode.toLong()) return false

        val current = runCatching {
            packageManager.getPackageInfo(appContext.packageName, flags)
        }.getOrNull() ?: return false

        val archiveSigners = signers(archive)
        val currentSigners = signers(current)
        if (archiveSigners.isEmpty() || archiveSigners.size != currentSigners.size) return false
        return archiveSigners.all { candidate ->
            currentSigners.any { it.contentEquals(candidate) }
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): List<ByteArray> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo != null) {
                val certs = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.apkContentsSigners
                        ?.takeIf { it.isNotEmpty() }
                        ?: signingInfo.signingCertificateHistory
                }
                val bytes = certs?.map { it.toByteArray() }.orEmpty()
                if (bytes.isNotEmpty()) return bytes
            }
        }
        return info.signatures?.map { it.toByteArray() }.orEmpty()
    }

    private fun getText(url: String): String {
        val accept = if (url.startsWith("https://api.github.com/")) {
            "application/vnd.github+json"
        } else {
            "text/plain, */*"
        }
        val connection = openVerifiedHttpsConnection(initialUrl = url, accept = accept)
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw NoReleaseException()
            if (code !in 200..299) throw IOException("HTTP $code")
            if (connection.contentLengthLong > MAX_TEXT_BYTES) throw BadResponseException()

            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_TEXT_BYTES) throw BadResponseException()
                    out.write(buffer, 0, read)
                }
            }
            return out.toString(StandardCharsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun openVerifiedHttpsConnection(
        initialUrl: String,
        accept: String,
    ): HttpsURLConnection {
        var currentUrl = URL(initialUrl)
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowedHttpsUrl(currentUrl)) {
                throw BadResponseException()
            }
            val connection = (currentUrl.openConnection() as? HttpsURLConnection)
                ?: throw BadResponseException()
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", userAgent())
            connection.setRequestProperty("Accept", accept)

            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw BadResponseException()
                currentUrl = URL(currentUrl, location)
            } else {
                return connection
            }
        }
        throw IOException("Too many redirects")
    }

    private fun pruneCachedApks() {
        val directory = File(appContext.cacheDir, UPDATE_DIRECTORY)
        if (!directory.isDirectory) return
        val keep = downloadedFile?.canonicalPath
        directory.listFiles()?.forEach { candidate ->
            if (candidate.isFile && runCatching { candidate.canonicalPath }.getOrNull() != keep) {
                candidate.delete()
            }
        }
    }

    private fun clearDownload(resetChecksum: Boolean) {
        downloadedFile?.delete()
        downloadedFile = null
        if (resetChecksum) {
            checksumUrl = null
        }
        pruneCachedApks()
    }

    private fun classify(error: Throwable): UpdateFailure = when (error) {
        is NoReleaseException -> UpdateFailure.NO_RELEASE
        is ChecksumException -> UpdateFailure.CHECKSUM
        is SignatureException -> UpdateFailure.SIGNATURE
        is DownloadException -> UpdateFailure.DOWNLOAD
        is BadResponseException, is JSONException -> UpdateFailure.BAD_RESPONSE
        else -> UpdateFailure.NETWORK
    }

    private fun userAgent(): String = "EcoRota/${BuildConfig.VERSION_NAME} (${appContext.packageName})"

    private data class FetchedUpdate(val update: AppUpdate, val checksumUrl: String)

    private class NoReleaseException : IOException()
    private class BadResponseException : IOException()
    private class ChecksumException : IOException()
    private class SignatureException : IOException()
    private class DownloadException : IOException()

    internal companion object {
        const val UPDATE_DIRECTORY = "updates"
        const val CHECKSUM_ASSET = "SHA256SUMS.txt"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_STEP = 128L * 1024L
        const val MAX_NOTES_LENGTH = 2_000
        const val MAX_TEXT_BYTES = 512 * 1024
        const val MAX_APK_BYTES = 100L * 1024L * 1024L
        const val MAX_REDIRECTS = 5
        const val API_LATEST = "https://api.github.com/repos/bashln/ecorota/releases/latest"

        private val VERSION_REGEX = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")
        private val WHITESPACE = Regex("\\s+")
        private val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val ALLOWED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        internal fun isAllowedHttpsUrl(rawUrl: String): Boolean =
            runCatching { isAllowedHttpsUrl(URL(rawUrl)) }.getOrDefault(false)

        internal fun isAllowedHttpsUrl(url: URL): Boolean =
            url.protocol.equals("https", ignoreCase = true) &&
                url.userInfo == null &&
                url.host.lowercase(Locale.ROOT) in ALLOWED_HOSTS

        internal fun parseVersionCode(versionName: String): Int? {
            val match = VERSION_REGEX.matchEntire(versionName) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            if (major > 200_000 || minor > 99 || patch > 99) return null
            val code = major * 10_000 + minor * 100 + patch
            return code.takeIf { it >= 1 }
        }

        internal fun parseChecksumForAsset(checksumsText: String, assetName: String): String? =
            checksumsText.lineSequence().mapNotNull { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
                val parts = line.split(WHITESPACE, limit = 2)
                if (parts.size < 2) return@mapNotNull null
                val hash = parts[0]
                if (!SHA256_HEX_REGEX.matches(hash)) return@mapNotNull null
                val rawTarget = parts[1].trim()
                val normalizedName = normalizeAssetTarget(rawTarget)
                if (normalizedName == assetName) hash else null
            }.firstOrNull()

        private fun normalizeAssetTarget(target: String): String {
            var current = target
            while (current.startsWith("*") || current.startsWith("./")) {
                current = when {
                    current.startsWith("*") -> current.removePrefix("*")
                    current.startsWith("./") -> current.removePrefix("./")
                    else -> current
                }
            }
            return current
        }
    }
}
