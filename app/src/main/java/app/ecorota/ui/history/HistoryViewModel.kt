package app.ecorota.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ecorota.core.analysis.DailyTripTotals
import app.ecorota.core.analysis.TripStatistics
import app.ecorota.core.analysis.TripTotals
import app.ecorota.core.model.TripListItem
import app.ecorota.core.model.VehicleProfile
import app.ecorota.di.AppContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryDay(
    val epochDay: Long,
    val trips: List<TripListItem>,
    val totals: DailyTripTotals,
)

data class TrendPoint(
    val epochDay: Long,
    val avoidableCost: Double,
    val averageScore: Double?,
    val tripCount: Int,
)

data class HistoryUiState(
    val loading: Boolean = true,
    val trips: List<TripListItem> = emptyList(),
    val totals: TripTotals? = null,
    val days: List<HistoryDay> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val bestDay: TrendPoint? = null,
    val worstDay: TrendPoint? = null,
    val isPremium: Boolean = false,
    val currency: String = VehicleProfile.DEFAULT_CURRENCY,
)

class HistoryViewModel(container: AppContainer) : ViewModel() {

    private val trips = container.tripRepository.observeTrips(limit = 500)

    private val entitlements = combine(
        container.entitlementRepository.entitlement,
        container.settingsRepository.settings,
    ) { entitlement, settings -> entitlement.isPremium to settings.currency }

    val state: StateFlow<HistoryUiState> = combine(trips, entitlements) { tripList, extras ->
        val (isPremium, settingsCurrency) = extras
        buildState(tripList, isPremium, settingsCurrency)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    private fun buildState(
        tripList: List<TripListItem>,
        isPremium: Boolean,
        settingsCurrency: String,
    ): HistoryUiState {
        val zone = ZoneId.systemDefault()
        val offsetMinutes = zone.rules.getOffset(Instant.now()).totalSeconds / 60
        val sortedTrips = tripList.sortedByDescending { it.startedAtMs }
        val dailyTotals = TripStatistics.byLocalDay(sortedTrips, offsetMinutes)
            .associateBy { it.epochDay }
        val tripsByDay = sortedTrips.groupBy { trip ->
            Instant.ofEpochMilli(trip.startedAtMs).atZone(zone).toLocalDate().toEpochDay()
        }
        val days = tripsByDay.entries
            .sortedByDescending { it.key }
            .mapNotNull { (epochDay, dayTrips) ->
                dailyTotals[epochDay]?.let { totals ->
                    HistoryDay(epochDay = epochDay, trips = dayTrips, totals = totals)
                }
            }

        val today = LocalDate.now(zone).toEpochDay()
        val trend = (6 downTo 0).map { daysAgo ->
            val epochDay = today - daysAgo
            val totals = dailyTotals[epochDay]
            TrendPoint(
                epochDay = epochDay,
                avoidableCost = totals?.avoidableCost ?: 0.0,
                averageScore = totals?.averageScore,
                tripCount = totals?.tripCount ?: 0,
            )
        }
        val scoredDays = trend.filter { it.averageScore != null && it.tripCount > 0 }

        return HistoryUiState(
            loading = false,
            trips = sortedTrips,
            totals = TripStatistics.totals(sortedTrips),
            days = days,
            trend = trend,
            bestDay = if (isPremium) scoredDays.maxByOrNull { it.averageScore ?: 0.0 } else null,
            worstDay = if (isPremium) scoredDays.minByOrNull { it.averageScore ?: 0.0 } else null,
            isPremium = isPremium,
            currency = sortedTrips.firstOrNull()?.currency ?: settingsCurrency,
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container) }
        }
    }
}
