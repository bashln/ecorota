package app.ecorota.update

import android.content.Context
import app.ecorota.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/** Play flavor: the Play Store owns updates, so the app never checks or installs. */
fun createUpdateController(
    context: Context,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    currentVersionCode: Int,
): UpdateController = NoOpUpdateController()
