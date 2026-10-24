package app.ecorota.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ecorota.core.model.AppSettings
import app.ecorota.data.Entitlement
import app.ecorota.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val entitlement: Entitlement = Entitlement(),
)

/** Root state: theme, onboarding gate and premium status. */
class AppViewModel(container: AppContainer) : ViewModel() {

    val state: StateFlow<AppUiState> = combine(
        container.settingsRepository.settings,
        container.entitlementRepository.entitlement,
    ) { settings, entitlement ->
        AppUiState(loading = false, settings = settings, entitlement = entitlement)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
