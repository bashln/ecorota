package app.ecorota.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ecorota.billing.PurchaseResult
import app.ecorota.core.model.AppSettings
import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.core.model.ThemeMode
import app.ecorota.core.model.VehicleProfile
import app.ecorota.core.scoring.ScoringConfig
import app.ecorota.data.Entitlement
import app.ecorota.di.AppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SettingsMessage {
    PURCHASE_SUCCESS,
    PURCHASE_CANCELLED,
    PURCHASE_UNAVAILABLE,
    PURCHASE_FAILED,
    RESTORE_SUCCESS,
    RESTORE_NOTHING,
    VEHICLE_SAVED,
    VEHICLE_DELETED,
    DEFAULT_VEHICLE_SET,
    VEHICLE_DELETE_DEFAULT_BLOCKED,
    TRIPS_DELETED,
    TRIPS_DELETE_FAILED,
    SAVE_FAILED,
}

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val vehicles: List<VehicleProfile> = emptyList(),
    val entitlement: Entitlement = Entitlement(),
    val billingAvailable: Boolean = false,
    val working: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val entitlementAndWorking = combine(
        container.entitlementRepository.entitlement,
        working,
    ) { entitlement, isWorking -> entitlement to isWorking }

    private val messageChannel = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settings,
        container.vehicleRepository.observeVehicles(),
        entitlementAndWorking,
    ) { settings, vehicles, extra ->
        val (entitlement, isWorking) = extra
        SettingsUiState(
            loading = false,
            settings = settings,
            vehicles = vehicles,
            entitlement = entitlement,
            billingAvailable = container.billingProvider.available,
            working = isWorking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }

    fun setKeepScreenOn(enabled: Boolean) = updateSettings {
        it.copy(keepScreenOnDuringTrip = enabled)
    }

    fun setMapTileDownloads(enabled: Boolean) = updateSettings {
        it.copy(allowMapTileDownloads = enabled)
    }

    fun setRetentionDays(days: Int) = updateSettings {
        it.copy(dataRetentionDays = days.coerceIn(0, 3650))
    }

    fun setAutoDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { container.trackingController.setAutoDetectionEnabled(enabled) }
        }
    }

    fun saveVehicle(vehicle: VehicleProfile, unit: ConsumptionUnit) {
        viewModelScope.launch {
            val result = runCatching {
                container.vehicleRepository.upsert(vehicle)
                container.settingsRepository.update { settings ->
                    settings.copy(consumptionUnit = unit, currency = vehicle.currency)
                }
            }
            messageChannel.send(
                if (result.isSuccess) SettingsMessage.VEHICLE_SAVED else SettingsMessage.SAVE_FAILED,
            )
        }
    }

    fun deleteVehicle(vehicle: VehicleProfile) {
        viewModelScope.launch {
            if (vehicle.isDefault) {
                messageChannel.send(SettingsMessage.VEHICLE_DELETE_DEFAULT_BLOCKED)
                return@launch
            }
            val success = runCatching { container.vehicleRepository.delete(vehicle.id) }.isSuccess
            messageChannel.send(
                if (success) SettingsMessage.VEHICLE_DELETED else SettingsMessage.SAVE_FAILED,
            )
        }
    }

    fun setDefaultVehicle(vehicleId: String) {
        viewModelScope.launch {
            val success = runCatching {
                container.vehicleRepository.setDefault(vehicleId)
            }.isSuccess
            messageChannel.send(
                if (success) SettingsMessage.DEFAULT_VEHICLE_SET else SettingsMessage.SAVE_FAILED,
            )
        }
    }

    fun saveScoringConfig(config: ScoringConfig) {
        updateSettings { it.copy(scoringConfig = config.sanitized()) }
    }

    fun resetScoringConfig() {
        updateSettings { it.copy(scoringConfig = ScoringConfig()) }
    }

    fun deleteAllTrips() {
        viewModelScope.launch {
            val success = runCatching { container.tripRepository.deleteAll() }.isSuccess
            messageChannel.send(
                if (success) SettingsMessage.TRIPS_DELETED else SettingsMessage.TRIPS_DELETE_FAILED,
            )
        }
    }

    fun purchase(activity: Activity?) {
        if (!container.billingProvider.available) {
            viewModelScope.launch { messageChannel.send(SettingsMessage.PURCHASE_UNAVAILABLE) }
            return
        }
        if (activity == null) {
            viewModelScope.launch { messageChannel.send(SettingsMessage.PURCHASE_FAILED) }
            return
        }
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            val result = runCatching { container.billingProvider.launchPurchase(activity) }
                .getOrElse { PurchaseResult.Failed(it.message.orEmpty()) }
            working.value = false
            messageChannel.send(
                when (result) {
                    PurchaseResult.Success, PurchaseResult.AlreadyOwned ->
                        SettingsMessage.PURCHASE_SUCCESS
                    PurchaseResult.Cancelled -> SettingsMessage.PURCHASE_CANCELLED
                    PurchaseResult.Unavailable -> SettingsMessage.PURCHASE_UNAVAILABLE
                    is PurchaseResult.Failed -> SettingsMessage.PURCHASE_FAILED
                },
            )
        }
    }

    fun restorePurchase() {
        if (!container.billingProvider.available) {
            viewModelScope.launch { messageChannel.send(SettingsMessage.PURCHASE_UNAVAILABLE) }
            return
        }
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            val restored = runCatching {
                container.billingProvider.restorePurchase()
            }.getOrDefault(false)
            working.value = false
            messageChannel.send(
                if (restored) SettingsMessage.RESTORE_SUCCESS else SettingsMessage.RESTORE_NOTHING,
            )
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val success = runCatching {
                container.settingsRepository.update(transform)
            }.isSuccess
            if (!success) messageChannel.send(SettingsMessage.SAVE_FAILED)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
