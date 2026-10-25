package app.ecorota.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ecorota.R
import app.ecorota.core.analysis.SelectedTip
import app.ecorota.core.model.TripRecord
import app.ecorota.di.AppContainer
import app.ecorota.ui.components.EmptyState
import app.ecorota.ui.components.LoadingState
import app.ecorota.ui.components.NoticeBanner
import app.ecorota.ui.components.ScoreBadge
import app.ecorota.ui.components.ScoreRing
import app.ecorota.ui.components.SectionCard
import app.ecorota.ui.components.ScreenHeader
import app.ecorota.ui.components.StatGrid
import app.ecorota.ui.components.SwitchRow
import app.ecorota.ui.components.scoreBandLabel
import app.ecorota.ui.components.tipText
import app.ecorota.ui.format.DateFormats
import app.ecorota.ui.format.Formatters
import app.ecorota.ui.permissions.AppPermissionsState
import app.ecorota.ui.permissions.rememberAppPermissions
import app.ecorota.ui.permissions.rememberAutoDetectionOptIn
import app.ecorota.update.UpdateState
import java.util.Locale

@Composable
fun HomeRoute(
    container: AppContainer,
    onOpenTrip: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateState by container.updateController.state.collectAsStateWithLifecycle()
    val permissions = rememberAppPermissions()
    val requestAutoDetection = rememberAutoDetectionOptIn(
        onEnable = { viewModel.setAutoDetectionEnabled(true) },
    )

    HomeScreen(
        state = state,
        permissions = permissions,
        updateState = updateState,
        onStart = viewModel::startRecording,
        onStop = viewModel::stopRecording,
        onAutoDetectionChange = { enabled ->
            if (enabled) requestAutoDetection() else viewModel.setAutoDetectionEnabled(false)
        },
        onOpenTrip = onOpenTrip,
        onOpenHistory = onOpenHistory,
        onOpenPaywall = onOpenPaywall,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    permissions: AppPermissionsState,
    updateState: UpdateState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAutoDetectionChange: (Boolean) -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (state.loading) {
        LoadingState(label = stringResource(R.string.common_loading))
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val live = state.live
    val score = if (live.recording) live.score else state.lastTrip?.score
    val band = scoreBandLabel(score)
    val scoreLabel = band ?: stringResource(
        if (live.recording) R.string.home_score_calculating else R.string.home_score_no_trips,
    )
    val scoreDescription = if (score != null && band != null) {
        stringResource(R.string.home_score_semantics, score, band)
    } else {
        stringResource(R.string.home_score_semantics_empty)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.home_subtitle),
            )
        }

        if (!permissions.hasFineLocation) {
            item {
                NoticeBanner(
                    text = stringResource(R.string.home_permission_banner),
                    actionLabel = stringResource(R.string.home_permission_action),
                    onAction = permissions.requestForegroundLocation,
                    icon = Icons.Filled.LocationOn,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        val availableUpdate = when (updateState) {
            is UpdateState.Available -> updateState.update
            is UpdateState.Ready -> updateState.update
            else -> null
        }
        if (availableUpdate != null) {
            item {
                NoticeBanner(
                    text = stringResource(
                        R.string.home_update_available,
                        availableUpdate.versionName,
                    ),
                    actionLabel = stringResource(R.string.home_update_action),
                    onAction = onOpenSettings,
                    icon = Icons.Filled.Refresh,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            when {
                                live.recording -> R.string.home_score_label_current
                                state.lastTrip != null -> R.string.home_score_label_last
                                else -> R.string.home_score_label_none
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    ScoreRing(
                        score = score,
                        label = scoreLabel,
                        contentDescription = scoreDescription,
                    )
                    if (live.recording && live.automatic) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.home_automatic_trip),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (live.recording) {
            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.home_live_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatGrid(
                        items = listOf(
                            stringResource(R.string.home_live_distance) to
                                Formatters.distance(live.distanceMeters, locale),
                            stringResource(R.string.home_live_duration) to
                                Formatters.duration(live.durationMs, locale),
                            stringResource(R.string.home_live_speed) to
                                Formatters.speed(live.currentSpeedKmh, state.speedUnit, locale),
                            stringResource(R.string.home_live_average_speed) to
                                Formatters.speed(live.averageSpeedKmh, state.speedUnit, locale),
                            stringResource(R.string.home_live_events) to
                                Formatters.integer(live.eventCount, locale),
                            stringResource(R.string.home_live_avoidable_cost) to
                                Formatters.money(
                                    amount = live.fuel?.avoidableCost ?: 0.0,
                                    currencyCode = state.currency,
                                    locale = locale,
                                ),
                        ),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onStop,
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_stop_trip))
                    }
                }
            }
        } else {
            item {
                SectionCard {
                    Button(
                        onClick = {
                            if (permissions.hasFineLocation) {
                                onStart()
                            } else {
                                permissions.requestForegroundLocation()
                            }
                        },
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_start_trip))
                    }
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = stringResource(R.string.home_auto_detection_title),
                        description = stringResource(R.string.home_auto_detection_body),
                        checked = state.autoDetectionEnabled,
                        onCheckedChange = onAutoDetectionChange,
                        enabled = !live.recording,
                    )
                }
            }

            if (state.monitoring) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.home_monitoring_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = stringResource(R.string.home_monitoring_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            val trip = state.lastTrip
            if (trip == null) {
                SectionCard {
                    EmptyState(
                        title = stringResource(R.string.home_no_trips_title),
                        body = stringResource(R.string.home_no_trips_body),
                        icon = Icons.Filled.PlayArrow,
                    )
                }
            } else {
                LastTripCard(
                    trip = trip,
                    locale = locale,
                    onClick = { onOpenTrip(trip.id) },
                )
            }
        }

        if (state.tips.isNotEmpty() && state.lastTrip != null) {
            item {
                SectionCard(title = stringResource(R.string.home_tips_title)) {
                    state.tips.forEachIndexed { index, tip ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        TipRow(tip = tip, locale = locale)
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.home_tips_history_action))
                    }
                }
            }
        }

        if (!state.isPremium) {
            item {
                PremiumCard(onOpenPaywall = onOpenPaywall)
            }
        }
    }
}

@Composable
private fun LastTripCard(
    trip: TripRecord,
    locale: Locale,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_last_trip_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = DateFormats.dateTime(trip.startedAtMs, locale),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_last_trip_summary,
                            Formatters.distance(trip.distanceMeters, locale),
                            Formatters.duration(trip.durationMs, locale),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.money(
                            trip.fuel.avoidableCost,
                            trip.fuel.currency,
                            locale,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.home_live_avoidable_cost),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun TipRow(tip: SelectedTip, locale: Locale) {
    val text = tipText(tip.tip)
    Column {
        Text(text = text.title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = text.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tip.lostPoints > 0.0) {
            Text(
                text = stringResource(
                    R.string.home_tip_lost_points,
                    Formatters.points(tip.lostPoints, locale),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PremiumCard(onOpenPaywall: () -> Unit) {
    Card(
        onClick = onOpenPaywall,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_premium_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.home_premium_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_premium_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
