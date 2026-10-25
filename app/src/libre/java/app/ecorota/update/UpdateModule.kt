package app.ecorota.update

import android.content.Context
import app.ecorota.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/** Community flavor: updates come from the project's signed GitHub releases. */
fun createUpdateController(
    context: Context,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    currentVersionCode: Int,
): UpdateController = GitHubUpdateController(
    context = context,
    settingsRepository = settingsRepository,
    scope = scope,
    currentVersionCode = currentVersionCode,
)
