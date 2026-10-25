package app.ecorota.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ecorota.core.analysis.DrivingTips
import app.ecorota.core.analysis.LifetimeTripStats
import app.ecorota.core.analysis.SelectedTip
import app.ecorota.core.model.SpeedUnit
import app.ecorota.core.model.TripRecord
import app.ecorota.core.model.VehicleProfile
import app.ecorota.data.Entitlement
import app.ecorota.di.AppContainer
import app.ecorota.tracking.LiveTripState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val live: LiveTripState = LiveTripState(),
    val lastTrip: TripRecord? = null,
    val lifetime: LifetimeTripStats? = null,
    val tips: List<SelectedTip> = emptyList(),
    val currency: String = VehicleProfile.DEFAULT_CURRENCY,
    val speedUnit: SpeedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
    val isPremium: Boolean = false,
    val autoDetectionEnabled: Boolean = false,
    val monitoring: Boolean = false,
    val busy: Boolean = false,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val lastTrip = MutableStateFlow<TripRecord?>(null)
    private val busy = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            container.tripRepository.observeTrips(limit = 1)
                .map { trips -> trips.firstOrNull()?.id }
                .distinctUntilChanged()
                .collectLatest { id ->
                    lastTrip.value = id?.let { tripId ->
                        runCatching { container.tripRepository.getTrip(tripId) }.getOrNull()
                    }
                }
        }
    }

    private val homeExtras = combine(
        container.vehicleRepository.observeDefaultVehicle(),
        container.entitlementRepository.entitlement,
        busy,
        container.tripRepository.observeLifetimeStats(),
    ) { vehicle, entitlement, isBusy, lifetime ->
        HomeExtras(vehicle, entitlement, isBusy, lifetime)
    }

    val state: StateFlow<HomeUiState> = combine(
        container.settingsRepository.settings,
        container.trackingController.state,
        container.trackingController.monitoring,
        lastTrip,
        homeExtras,
    ) { settings, live, monitoring, trip, extra ->
        HomeUiState(
            loading = false,
            live = live,
            lastTrip = trip,
            lifetime = extra.lifetime,
            tips = trip?.let { DrivingTips.forTrip(it.breakdown) }.orEmpty(),
            currency = live.fuel?.currency
                ?: extra.vehicle?.currency
                ?: settings.currency,
            speedUnit = settings.speedUnit,
            isPremium = extra.entitlement.isPremium,
            autoDetectionEnabled = settings.autoDetectionEnabled,
            monitoring = monitoring,
            busy = extra.busy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    private data class HomeExtras(
        val vehicle: VehicleProfile?,
        val entitlement: Entitlement,
        val busy: Boolean,
        val lifetime: LifetimeTripStats,
    )

    fun startRecording() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            container.trackingController.startRecording(manual = true)
            busy.value = false
        }
    }

    fun stopRecording() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            container.trackingController.stopRecording()
            busy.value = false
        }
    }

    fun setAutoDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.trackingController.setAutoDetectionEnabled(enabled)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}
