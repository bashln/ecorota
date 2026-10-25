package app.ecorota.ui.trip

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ecorota.core.model.SpeedUnit
import app.ecorota.core.model.TripRecord
import app.ecorota.data.PremiumFeature
import app.ecorota.di.AppContainer
import app.ecorota.export.TripExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TripDetailEvent {
    data object GpxExported : TripDetailEvent
    data object CsvExported : TripDetailEvent
    data object ExportFailed : TripDetailEvent
    data object Deleted : TripDetailEvent
    data object DeleteFailed : TripDetailEvent
}

data class TripDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val trip: TripRecord? = null,
    val vehicleName: String? = null,
    val isPremium: Boolean = false,
    val canExport: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
    val allowMapTiles: Boolean = false,
    val working: Boolean = false,
)

class TripDetailViewModel(
    private val container: AppContainer,
    private val tripId: String,
) : ViewModel() {

    private val exporter: TripExporter = container.tripExporter
    private val trip = MutableStateFlow<TripRecord?>(null)
    private val status = MutableStateFlow(Status())

    private val eventsChannel = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val record = if (tripId.isBlank()) {
                null
            } else {
                runCatching { container.tripRepository.getTrip(tripId) }.getOrNull()
            }
            trip.value = record
            status.update { it.copy(loading = false, missing = record == null) }
        }
    }

    private val details = combine(
        trip,
        status,
        container.entitlementRepository.entitlement,
        container.settingsRepository.settings,
    ) { trip, status, entitlement, settings ->
        TripDetailUiState(
            loading = status.loading,
            missing = status.missing,
            trip = trip,
            isPremium = entitlement.isPremium,
            canExport = entitlement.canUse(PremiumFeature.DATA_EXPORT),
            speedUnit = settings.speedUnit,
            allowMapTiles = settings.allowMapTileDownloads,
            working = status.working,
        )
    }

    private val nameAndDetails = combine(
        container.vehicleRepository.observeVehicles(),
        details,
    ) { vehicles, detailState ->
        detailState.copy(
            vehicleName = detailState.trip?.let { record ->
                vehicles.firstOrNull { it.id == record.vehicleId }?.name?.takeIf { it.isNotBlank() }
            },
        )
    }

    val state: StateFlow<TripDetailUiState> = nameAndDetails.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripDetailUiState(),
    )

    fun setMapTileDownloads(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(allowMapTileDownloads = enabled) }
        }
    }

    fun exportGpx(resolver: ContentResolver, uri: Uri) {
        val record = trip.value ?: return
        if (status.value.working) return
        status.update { it.copy(working = true) }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri)?.use { output ->
                        exporter.writeGpx(record, output)
                    } ?: error("No output stream for $uri")
                }.isSuccess
            }
            status.update { it.copy(working = false) }
            eventsChannel.send(
                if (success) TripDetailEvent.GpxExported else TripDetailEvent.ExportFailed,
            )
        }
    }

    fun exportCsv(resolver: ContentResolver, uri: Uri) {
        if (status.value.working) return
        status.update { it.copy(working = true) }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val allTrips = container.tripRepository.getAllTrips()
                    resolver.openOutputStream(uri)?.use { output ->
                        exporter.writeCsv(allTrips, output)
                    } ?: error("No output stream for $uri")
                }.isSuccess
            }
            status.update { it.copy(working = false) }
            eventsChannel.send(
                if (success) TripDetailEvent.CsvExported else TripDetailEvent.ExportFailed,
            )
        }
    }

    fun deleteTrip() {
        if (status.value.working) return
        status.update { it.copy(working = true) }
        viewModelScope.launch {
            val success = runCatching {
                container.tripRepository.deleteTrip(tripId)
            }.isSuccess
            status.update { it.copy(working = false) }
            eventsChannel.send(
                if (success) TripDetailEvent.Deleted else TripDetailEvent.DeleteFailed,
            )
        }
    }

    private data class Status(
        val loading: Boolean = true,
        val missing: Boolean = false,
        val working: Boolean = false,
    )

    companion object {
        fun factory(
            container: AppContainer,
            tripId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { TripDetailViewModel(container, tripId) }
        }
    }
}
