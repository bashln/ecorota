package app.ecorota.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ecorota.R
import app.ecorota.core.model.TripListItem
import app.ecorota.di.AppContainer
import app.ecorota.ui.components.EmptyState
import app.ecorota.ui.components.KeyValueRow
import app.ecorota.ui.components.LoadingState
import app.ecorota.ui.components.ScoreBadge
import app.ecorota.ui.components.ScreenHeader
import app.ecorota.ui.components.SectionCard
import app.ecorota.ui.components.StatGrid
import app.ecorota.ui.format.DateFormats
import app.ecorota.ui.format.Formatters
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryRoute(
    container: AppContainer,
    onOpenTrip: (String) -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onOpenTrip = onOpenTrip,
        onOpenPaywall = onOpenPaywall,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    onOpenTrip: (String) -> Unit,
    onOpenPaywall: () -> Unit,
) {
    if (state.loading) {
        LoadingState(label = stringResource(R.string.common_loading))
        return
    }
    val locale = LocalConfiguration.current.locales[0]

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.history_subtitle),
            )
        }

        if (state.totals != null && state.totals.tripCount > 0) {
            item {
                TotalsCard(state = state, locale = locale)
            }
        }

        if (state.totals != null && state.totals.tripCount > 0) {
            item {
                if (state.isPremium) {
                    TrendCard(state = state, locale = locale)
                } else {
                    LockedTrendCard(onOpenPaywall = onOpenPaywall)
                }
            }
        }

        if (state.days.isEmpty() && !state.loading) {
            item {
                SectionCard {
                    EmptyState(
                        icon = Icons.Filled.History,
                        title = stringResource(R.string.history_empty_title),
                        body = stringResource(R.string.history_empty_body),
                    )
                }
            }
        }

        state.days.forEach { day ->
            item(key = "day-${day.epochDay}") {
                DayHeader(day = day, currency = state.currency, locale = locale)
            }
            items(items = day.trips, key = { trip -> trip.id }) { trip ->
                TripRow(
                    trip = trip,
                    locale = locale,
                    onClick = { onOpenTrip(trip.id) },
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun TotalsCard(state: HistoryUiState, locale: Locale) {
    val totals = state.totals ?: return
    SectionCard(title = stringResource(R.string.history_totals_title)) {
        StatGrid(
            items = listOf(
                stringResource(R.string.history_total_trips) to
                    Formatters.integer(totals.tripCount, locale),
                stringResource(R.string.history_total_distance) to
                    Formatters.distance(totals.distanceMeters, locale),
                stringResource(R.string.history_total_average_score) to
                    Formatters.integer(totals.averageScore.roundToInt(), locale),
                stringResource(R.string.history_total_avoidable_cost) to
                    Formatters.money(totals.avoidableCost, state.currency, locale),
            ),
        )
        Spacer(Modifier.height(8.dp))
        KeyValueRow(
            label = stringResource(R.string.history_avoidable_fuel),
            value = Formatters.liters(totals.avoidableLiters, locale),
        )
    }
}

@Composable
private fun TrendCard(state: HistoryUiState, locale: Locale) {
    SectionCard(title = stringResource(R.string.history_trend_title)) {
        val hasTrips = state.trend.any { it.tripCount > 0 }
        if (hasTrips) {
            TrendChart(
                points = state.trend,
                locale = locale,
                contentDescription = stringResource(R.string.history_chart_description),
            )
            Spacer(Modifier.height(12.dp))
            state.bestDay?.let { best ->
                TrendDayRow(
                    label = stringResource(R.string.history_best_day),
                    point = best,
                    currency = state.currency,
                    locale = locale,
                )
            }
            state.worstDay?.let { worst ->
                TrendDayRow(
                    label = stringResource(R.string.history_worst_day),
                    point = worst,
                    currency = state.currency,
                    locale = locale,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.history_trend_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendDayRow(
    label: String,
    point: TrendPoint,
    currency: String,
    locale: Locale,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = DateFormats.monthDay(point.epochDay, locale),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                R.string.history_day_value,
                point.averageScore?.roundToInt() ?: 0,
                Formatters.money(point.avoidableCost, currency, locale),
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LockedTrendCard(onOpenPaywall: () -> Unit) {
    Card(
        onClick = onOpenPaywall,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.history_trend_locked_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.history_trend_locked_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.history_trend_locked_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DayHeader(day: HistoryDay, currency: String, locale: Locale) {
    val tripCount = pluralStringResource(
        R.plurals.history_trips_count,
        day.totals.tripCount,
        day.totals.tripCount,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DateFormats.dayHeader(day.epochDay, locale),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.history_day_summary,
                    tripCount,
                    Formatters.distance(day.totals.distanceMeters, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Formatters.money(day.totals.avoidableCost, currency, locale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TripRow(
    trip: TripListItem,
    locale: Locale,
    onClick: () -> Unit,
) {
    val events = pluralStringResource(R.plurals.history_events_count, trip.eventCount, trip.eventCount)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreBadge(
                score = trip.score,
                contentDescription = stringResource(
                    R.string.score_badge_semantics,
                    trip.score,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateFormats.time(trip.startedAtMs, locale),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.history_trip_summary,
                        Formatters.distance(trip.distanceMeters, locale),
                        Formatters.duration(trip.durationMs, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = events,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.money(trip.avoidableCost, trip.currency, locale),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.history_trip_cost_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
