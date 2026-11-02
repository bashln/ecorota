package app.ecorota.update

import android.content.Context
import app.ecorota.BuildConfig
import app.ecorota.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Community flavor: updates come from the project's signed GitHub releases unless
 * disabled at build time for store repositories such as F-Droid (-PfdroidBuild=true).
 */
fun createUpdateController(
    context: Context,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    currentVersionCode: Int,
): UpdateController = if (BuildConfig.UPDATER_ENABLED) {
    GitHubUpdateController(
        context = context,
        settingsRepository = settingsRepository,
        scope = scope,
        currentVersionCode = currentVersionCode,
    )
} else {
    NoOpUpdateController()
}
