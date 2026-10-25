package app.ecorota.update

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/** A newer release found on the project's GitHub releases page. */
data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val assetName: String,
    val downloadUrl: String,
    val releaseNotes: String?,
) {
    val releasePage: String get() = "https://github.com/bashln/ecorota/releases/tag/v$versionName"
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(
        val update: AppUpdate,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : UpdateState
    data class Ready(val update: AppUpdate) : UpdateState
    data class Failed(val reason: UpdateFailure) : UpdateState
}

enum class UpdateFailure {
    NETWORK,
    NO_RELEASE,
    BAD_RESPONSE,
    DOWNLOAD,
    CHECKSUM,
    SIGNATURE,
    INSTALL,
}

/**
 * Self-update boundary. The libre build implements it against GitHub Releases;
 * the Play build is a no-op because Play Store owns updates there.
 *
 * Downloads are verified twice before the installer is opened: SHA-256 against
 * the release checksum file, and the APK signing certificate against the
 * installed app.
 */
interface UpdateController {
    val supported: Boolean
    val state: StateFlow<UpdateState>

    /** Starts optional automatic checks according to the user setting. */
    fun start()

    fun check(manual: Boolean = false)

    fun download()

    fun install(context: Context)

    fun openInstallPermission(context: Context)

    fun canInstall(context: Context): Boolean

    fun dismiss()
}
