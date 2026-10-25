package app.ecorota.update

import android.content.Context
import app.ecorota.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Play flavor: the Play Store owns updates, so the app never checks or installs. */
fun createUpdateController(
    context: Context,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    currentVersionCode: Int,
): UpdateController = NoOpUpdateController()

private class NoOpUpdateController : UpdateController {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val supported: Boolean = false
    override val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    override fun start() = Unit
    override fun check(manual: Boolean) = Unit
    override fun download() = Unit
    override fun install(context: Context) = Unit
    override fun openInstallPermission(context: Context) = Unit
    override fun canInstall(context: Context): Boolean = false
    override fun dismiss() = Unit
}
