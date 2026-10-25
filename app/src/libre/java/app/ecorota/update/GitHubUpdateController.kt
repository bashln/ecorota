package app.ecorota.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.ecorota.BuildConfig
import app.ecorota.data.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
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
                        clearDownload()
                        mutableState.value = UpdateState.UpToDate
                    } else {
                        clearDownload()
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
        clearDownload()
        mutableState.value = UpdateState.Idle
    }

    private fun fetchLatest(): FetchedUpdate {
        val json = JSONObject(getText(API_LATEST))
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            throw NoReleaseException()
        }
        val versionName = json.optString("tag_name").removePrefix("v")
        val match = VERSION_REGEX.matchEntire(versionName) ?: throw BadResponseException()
        val major = match.groupValues[1].toIntOrNull() ?: throw BadResponseException()
        val minor = match.groupValues[2].toIntOrNull() ?: throw BadResponseException()
        val patch = match.groupValues[3].toIntOrNull() ?: throw BadResponseException()
        if (minor > 99 || patch > 99) throw BadResponseException()
        val versionCode = major * 10_000 + minor * 100 + patch

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
        if (apkUrl.isNullOrBlank()) throw BadResponseException()
        if (checksums.isNullOrBlank()) throw BadResponseException()

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
        val file = File(directory, update.assetName.substringAfterLast('/'))
        if (!file.name.endsWith(".apk")) throw DownloadException()
        if (file.exists() && !file.delete()) throw DownloadException()

        val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent())
            connection.setRequestProperty("Accept", "application/octet-stream")
            if (connection.responseCode !in 200..299) throw DownloadException()

            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (downloaded - reported >= PROGRESS_STEP) {
                            reported = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)
            val actual = digest.digest().joinToString("") { byte ->
                "%02x".format(Locale.ROOT, byte)
            }
            if (!actual.equals(expected, ignoreCase = true)) {
                file.delete()
                throw ChecksumException()
            }
            if (!verifySignature(file)) {
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

    private fun expectedChecksum(checksumsUrl: String, assetName: String): String {
        val text = getText(checksumsUrl)
        val expected = text.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(WHITESPACE)
            val name = parts.lastOrNull()?.removePrefix("./")?.removePrefix("*")
            if (parts.size >= 2 && name == assetName) {
                parts.first()
            } else {
                null
            }
        }.firstOrNull()
        return expected ?: throw ChecksumException()
    }

    @Suppress("DEPRECATION")
    private fun verifySignature(file: File): Boolean {
        val packageManager = appContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        if (archive.packageName != appContext.packageName) return false
        val current = packageManager.getPackageInfo(appContext.packageName, flags)
        val archiveSigners = signers(archive)
        val currentSigners = signers(current)
        return archiveSigners.isNotEmpty() &&
            archiveSigners.all { candidate ->
                currentSigners.any { it.contentEquals(candidate) }
            }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent())
            connection.setRequestProperty(
                "Accept",
                if (url.startsWith("https://api.github.com/")) {
                    "application/vnd.github+json"
                } else {
                    "*/*"
                },
            )
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw NoReleaseException()
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun clearDownload() {
        downloadedFile?.delete()
        downloadedFile = null
        checksumUrl = null
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

    private companion object {
        const val UPDATE_DIRECTORY = "updates"
        const val CHECKSUM_ASSET = "SHA256SUMS.txt"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_STEP = 128L * 1024L
        const val MAX_NOTES_LENGTH = 2_000
        const val API_LATEST = "https://api.github.com/repos/bashln/ecorota/releases/latest"
        val VERSION_REGEX = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")
        val WHITESPACE = Regex("\\s+")
    }
}
