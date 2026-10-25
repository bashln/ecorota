package app.ecorota.ui.trip

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ecorota.R
import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.EventSeverity
import app.ecorota.core.model.ScoreCategory
import app.ecorota.core.model.ScoreComponent
import app.ecorota.core.model.TripRecord
import app.ecorota.di.AppContainer
import app.ecorota.ui.components.EmptyState
import app.ecorota.ui.components.KeyValueRow
import app.ecorota.ui.components.LoadingState
import app.ecorota.ui.components.PremiumNotice
import app.ecorota.ui.components.ScoreRing
import app.ecorota.ui.components.SectionCard
import app.ecorota.ui.components.StatGrid
import app.ecorota.ui.components.scoreBandLabel
import app.ecorota.ui.components.tipText
import app.ecorota.ui.format.DateFormats
import app.ecorota.ui.format.Formatters
import app.ecorota.ui.theme.ScoreBand
import app.ecorota.ui.theme.color
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TripDetailRoute(
    container: AppContainer,
    tripId: String,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-$tripId",
        factory = TripDetailViewModel.factory(container, tripId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val gpxExported = stringResource(R.string.detail_gpx_success)
    val csvExported = stringResource(R.string.detail_csv_success)
    val exportFailed = stringResource(R.string.detail_export_failed)
    val deleteFailed = stringResource(R.string.detail_delete_failed)

    val gpxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        if (uri != null) viewModel.exportGpx(context.contentResolver, uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) viewModel.exportCsv(context.contentResolver, uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TripDetailEvent.GpxExported -> snackbarHostState.showSnackbar(gpxExported)
                TripDetailEvent.CsvExported -> snackbarHostState.showSnackbar(csvExported)
                TripDetailEvent.ExportFailed -> snackbarHostState.showSnackbar(exportFailed)
                TripDetailEvent.Deleted -> onBack()
                TripDetailEvent.DeleteFailed -> snackbarHostState.showSnackbar(deleteFailed)
            }
        }
    }

    TripDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenPaywall = onOpenPaywall,
        onSetMapTiles = viewModel::setMapTileDownloads,
        onExportGpx = {
            state.trip?.let { trip ->
                gpxLauncher.launch(container.tripExporter.gpxFileName(trip))
            }
        },
        onExportCsv = {
            csvLauncher.launch(
                container.tripExporter.csvFileName(System.currentTimeMillis()),
            )
        },
        onDelete = viewModel::deleteTrip,
    )
}

@Composable
private fun TripDetailScreen(
    state: TripDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    onSetMapTiles: (Boolean) -> Unit,
    onExportGpx: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMapConsentDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(onBack = onBack)
            when {
                state.loading -> LoadingState(label = stringResource(R.string.common_loading))
                state.missing || state.trip == null -> EmptyState(
                    icon = Icons.Filled.Map,
                    title = stringResource(R.string.detail_missing_title),
                    body = stringResource(R.string.detail_missing_body),
                )
                else -> TripDetailContent(
                    state = state,
                    trip = state.trip,
                    locale = locale,
                    onOpenPaywall = onOpenPaywall,
                    onRequestEnableMapTiles = { showMapConsentDialog = true },
                    onExportGpx = onExportGpx,
                    onExportCsv = onExportCsv,
                    onDelete = { showDeleteDialog = true },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showMapConsentDialog) {
        AlertDialog(
            onDismissRequest = { showMapConsentDialog = false },
            title = { Text(stringResource(R.string.settings_map_confirm_title)) },
            text = { Text(stringResource(R.string.settings_map_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMapConsentDialog = false
                        onSetMapTiles(true)
                    },
                ) {
                    Text(stringResource(R.string.settings_map_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMapConsentDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.detail_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        Text(
            text = stringResource(R.string.detail_title),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun TripDetailContent(
    state: TripDetailUiState,
    trip: TripRecord,
    locale: Locale,
    onOpenPaywall: () -> Unit,
    onRequestEnableMapTiles: () -> Unit,
    onExportGpx: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit,
) {
    val band = scoreBandLabel(trip.score)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderCard(trip = trip, vehicleName = state.vehicleName, locale = locale, band = band)
        }
        item {
            MapCard(
                trip = trip,
                allowMapTiles = state.allowMapTiles,
                onRequestEnableTiles = onRequestEnableMapTiles,
            )
        }
        item {
            StatsCard(trip = trip, locale = locale)
        }
        item {
            BreakdownCard(trip = trip, locale = locale)
        }
        item {
            TipsCard(trip = trip, locale = locale)
        }
        item {
            EventsCard(
                trip = trip,
                isPremium = state.isPremium,
                locale = locale,
                onOpenPaywall = onOpenPaywall,
            )
        }
        item {
            ActionsCard(
                isPremium = state.isPremium,
                canExport = state.canExport,
                working = state.working,
                onExportGpx = {
                    if (state.canExport) onExportGpx() else onOpenPaywall()
                },
                onExportCsv = {
                    if (state.canExport) onExportCsv() else onOpenPaywall()
                },
                onDelete = onDelete,
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HeaderCard(
    trip: TripRecord,
    vehicleName: String?,
    locale: Locale,
    band: String?,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(
                score = trip.score,
                label = band.orEmpty(),
                contentDescription = if (band != null) {
                    stringResource(R.string.score_semantics, trip.score, band)
                } else {
                    stringResource(R.string.score_badge_semantics, trip.score)
                },
                size = 132.dp,
                strokeWidth = 12.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateFormats.dateTime(trip.startedAtMs, locale),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (vehicleName != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.detail_vehicle, vehicleName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (trip.manualStart) {
                            R.string.detail_manual_trip
                        } else {
                            R.string.detail_automatic_trip
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MapCard(
    trip: TripRecord,
    allowMapTiles: Boolean,
    onRequestEnableTiles: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.detail_map_title)) {
        if (trip.route.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.detail_map_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.detail_map_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                TripMap(
                    route = trip.route,
                    events = trip.events,
                    allowTileDownloads = allowMapTiles,
                    contentDescription = stringResource(R.string.detail_map_description),
                    modifier = Modifier.matchParentSize(),
                )
                if (!allowMapTiles) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.detail_tiles_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = onRequestEnableTiles,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.detail_map_enable))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsCard(trip: TripRecord, locale: Locale) {
    SectionCard(title = stringResource(R.string.detail_stats_title)) {
        StatGrid(
            items = listOf(
                stringResource(R.string.detail_stat_distance) to
                    Formatters.distance(trip.distanceMeters, locale),
                stringResource(R.string.detail_stat_duration) to
                    Formatters.duration(trip.durationMs, locale),
                stringResource(R.string.detail_stat_moving_time) to
                    Formatters.duration(trip.movingDurationMs, locale),
                stringResource(R.string.detail_stat_average_speed) to
                    Formatters.speed(trip.averageSpeedKmh, locale),
                stringResource(R.string.detail_stat_max_speed) to
                    Formatters.speed(trip.maxSpeedKmh, locale),
                stringResource(R.string.detail_stat_fuel) to
                    Formatters.liters(trip.fuel.estimatedLiters, locale),
                stringResource(R.string.detail_stat_avoidable_fuel) to
                    Formatters.liters(trip.fuel.avoidableLiters, locale),
                stringResource(R.string.detail_stat_avoidable_cost) to
                    Formatters.money(trip.fuel.avoidableCost, trip.fuel.currency, locale),
            ),
        )
        Spacer(Modifier.height(8.dp))
        KeyValueRow(
            label = stringResource(R.string.detail_stat_idle_time),
            value = Formatters.duration(trip.idleDurationMs, locale),
        )
        KeyValueRow(
            label = stringResource(R.string.detail_stat_avoidable_share),
            value = Formatters.percent(trip.fuel.avoidablePercent, locale),
        )
    }
}

@Composable
private fun BreakdownCard(trip: TripRecord, locale: Locale) {
    val breakdown = trip.breakdown
    SectionCard(title = stringResource(R.string.detail_score_title)) {
        Text(
            text = stringResource(R.string.detail_score_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        breakdown.biggestLosses().forEachIndexed { index, component ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            ComponentRow(
                component = component,
                trip = trip,
                locale = locale,
            )
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.detail_fuel_blend_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.detail_fuel_blend_behavior,
                        breakdown.behaviorScore,
                        Formatters.percent((1.0 - breakdown.fuelWeight) * 100.0, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.detail_fuel_blend_fuel,
                        breakdown.fuelScore,
                        Formatters.percent(breakdown.fuelWeight * 100.0, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detail_fuel_blend_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ComponentRow(
    component: ScoreComponent,
    trip: TripRecord,
    locale: Locale,
) {
    val band = ScoreBand.from(component.score)
    val barColor = band?.color() ?: MaterialTheme.colorScheme.outline
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(component.category.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.detail_component_score,
                    component.score,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = barColor,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((component.score / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(barColor),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.detail_component_meta,
                Formatters.percent(component.weight * 100.0, locale),
                Formatters.points(component.lostPoints, locale),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = componentDetail(component, trip, locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun componentDetail(
    component: ScoreComponent,
    trip: TripRecord,
    locale: Locale,
): String {
    val duration = trip.durationMs
    return when (component.category) {
        ScoreCategory.IDLING -> {
            val share = if (duration > 0) trip.idleDurationMs.toDouble() / duration * 100.0 else 0.0
            stringResource(R.string.detail_idle_share, Formatters.percent(share, locale))
        }
        ScoreCategory.SPEED -> {
            val overspeedMs = trip.events
                .filter { it.type == DrivingEventType.OVERSPEED }
                .sumOf { it.durationMs }
            val share = if (duration > 0) overspeedMs.toDouble() / duration * 100.0 else 0.0
            stringResource(R.string.detail_overspeed_share, Formatters.percent(share, locale))
        }
        else -> pluralStringResource(
            R.plurals.history_events_count,
            component.eventCount,
            component.eventCount,
        )
    }
}

private fun ScoreCategory.labelRes(): Int = when (this) {
    ScoreCategory.ACCELERATION -> R.string.detail_component_acceleration
    ScoreCategory.BRAKING -> R.string.detail_component_braking
    ScoreCategory.CORNERING -> R.string.detail_component_cornering
    ScoreCategory.SPEED -> R.string.detail_component_speed
    ScoreCategory.IDLING -> R.string.detail_component_idling
}

@Composable
private fun TipsCard(trip: TripRecord, locale: Locale) {
    val tips = app.ecorota.core.analysis.DrivingTips.forTrip(trip.breakdown)
    SectionCard(title = stringResource(R.string.detail_tips_title)) {
        tips.forEachIndexed { index, tip ->
            if (index > 0) Spacer(Modifier.height(10.dp))
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
    }
}

@Composable
private fun EventsCard(
    trip: TripRecord,
    isPremium: Boolean,
    locale: Locale,
    onOpenPaywall: () -> Unit,
) {
    val events = trip.events.sortedBy { it.startedAtMs }
    SectionCard(title = stringResource(R.string.detail_events_title)) {
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_events_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            events.forEachIndexed { index, event ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                EventRow(event = event, isPremium = isPremium, locale = locale)
            }
            if (!isPremium) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.detail_events_locked),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenPaywall) {
                            Text(stringResource(R.string.detail_events_locked_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: DrivingEvent, isPremium: Boolean, locale: Locale) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = event.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(event.type.labelRes()),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.detail_event_meta,
                    DateFormats.time(event.startedAtMs, locale),
                    Formatters.duration(event.durationMs, locale),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = eventValue(event, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isPremium) {
                Text(
                    text = stringResource(
                        R.string.detail_event_severity,
                        stringResource(event.severity.labelRes()),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.detail_event_penalty,
                        Formatters.points(event.penaltyPoints, locale),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun eventValue(event: DrivingEvent, locale: Locale): String = when (event.type) {
    DrivingEventType.HARD_ACCELERATION,
    DrivingEventType.HARD_BRAKING,
    DrivingEventType.SHARP_CORNERING,
    -> stringResource(
        R.string.detail_event_peak_accel,
        String.format(locale, "%.1f", event.peakValue),
    )
    DrivingEventType.OVERSPEED -> stringResource(
        R.string.detail_event_peak_speed,
        Formatters.speed(event.peakValue, locale),
    )
    DrivingEventType.IDLING -> stringResource(
        R.string.detail_event_idle_duration,
        Formatters.duration(event.durationMs, locale),
    )
}

private fun DrivingEventType.labelRes(): Int = when (this) {
    DrivingEventType.HARD_ACCELERATION -> R.string.event_hard_acceleration
    DrivingEventType.HARD_BRAKING -> R.string.event_hard_braking
    DrivingEventType.SHARP_CORNERING -> R.string.event_sharp_cornering
    DrivingEventType.OVERSPEED -> R.string.event_overspeed
    DrivingEventType.IDLING -> R.string.event_idling
}

private fun DrivingEventType.icon(): ImageVector = when (this) {
    DrivingEventType.HARD_ACCELERATION -> Icons.AutoMirrored.Filled.TrendingUp
    DrivingEventType.HARD_BRAKING -> Icons.AutoMirrored.Filled.TrendingDown
    DrivingEventType.SHARP_CORNERING -> Icons.AutoMirrored.Filled.RotateRight
    DrivingEventType.OVERSPEED -> Icons.Filled.Speed
    DrivingEventType.IDLING -> Icons.Filled.Timer
}

private fun EventSeverity.labelRes(): Int = when (this) {
    EventSeverity.MILD -> R.string.severity_mild
    EventSeverity.MODERATE -> R.string.severity_moderate
    EventSeverity.SEVERE -> R.string.severity_severe
}

@Composable
private fun ActionsCard(
    isPremium: Boolean,
    canExport: Boolean,
    working: Boolean,
    onExportGpx: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.detail_actions_title)) {
        if (working) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        if (!canExport) {
            PremiumNotice(text = stringResource(R.string.detail_export_requires_premium))
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(
            onClick = onExportGpx,
            enabled = !working,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.detail_export_gpx))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExportCsv,
            enabled = !working,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.detail_export_csv))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDelete,
            enabled = !working,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.detail_delete))
        }
    }
}
