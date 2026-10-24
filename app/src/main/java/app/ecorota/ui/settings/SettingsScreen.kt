package app.ecorota.ui.settings

import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ecorota.BuildConfig
import app.ecorota.R
import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.core.model.ThemeMode
import app.ecorota.core.model.VehicleProfile
import app.ecorota.core.scoring.ScoringConfig
import app.ecorota.data.PremiumSource
import app.ecorota.di.AppContainer
import app.ecorota.tracking.LiveTripState
import app.ecorota.ui.components.KeyValueRow
import app.ecorota.ui.components.LoadingState
import app.ecorota.ui.components.NavigationRow
import app.ecorota.ui.components.NoticeBanner
import app.ecorota.ui.components.PremiumNotice
import app.ecorota.ui.components.ScreenHeader
import app.ecorota.ui.components.SectionCard
import app.ecorota.ui.components.SwitchRow
import app.ecorota.ui.format.Formatters
import app.ecorota.ui.permissions.AppPermissionsState
import app.ecorota.ui.permissions.openAppSettings
import app.ecorota.ui.permissions.rememberAppPermissions
import app.ecorota.ui.permissions.rememberAutoDetectionOptIn
import app.ecorota.ui.util.findActivity
import app.ecorota.ui.util.openExternalUrl
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Stable
data class SettingsActions(
    val setThemeMode: (ThemeMode) -> Unit,
    val setKeepScreenOn: (Boolean) -> Unit,
    val setMapTileDownloads: (Boolean) -> Unit,
    val setAutoDetection: (Boolean) -> Unit,
    val setRetentionDays: (Int) -> Unit,
    val deleteAllTrips: () -> Unit,
    val saveVehicle: (VehicleProfile, ConsumptionUnit) -> Unit,
    val deleteVehicle: (VehicleProfile) -> Unit,
    val setDefaultVehicle: (String) -> Unit,
    val saveScoringConfig: (ScoringConfig) -> Unit,
    val resetScoringConfig: () -> Unit,
    val purchase: () -> Unit,
    val restorePurchase: () -> Unit,
    val tip: () -> Unit,
    val openSupport: () -> Unit,
    val openSource: () -> Unit,
)

@Composable
fun SettingsRoute(container: AppContainer, onOpenPaywall: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val live by container.trackingController.state.collectAsStateWithLifecycle()
    val permissions = rememberAppPermissions()
    val requestAutoDetection = rememberAutoDetectionOptIn(
        onEnable = { viewModel.setAutoDetectionEnabled(true) },
    )
    val context = LocalContext.current
    val activity: Activity? = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }

    val messageTexts = mapOf(
        SettingsMessage.PURCHASE_SUCCESS to stringResource(R.string.settings_msg_purchase_success),
        SettingsMessage.PURCHASE_CANCELLED to stringResource(R.string.settings_msg_purchase_cancelled),
        SettingsMessage.PURCHASE_UNAVAILABLE to
            stringResource(R.string.settings_msg_purchase_unavailable),
        SettingsMessage.PURCHASE_FAILED to stringResource(R.string.settings_msg_purchase_failed),
        SettingsMessage.RESTORE_SUCCESS to stringResource(R.string.settings_msg_restore_success),
        SettingsMessage.RESTORE_NOTHING to stringResource(R.string.settings_msg_restore_nothing),
        SettingsMessage.VEHICLE_SAVED to stringResource(R.string.settings_msg_vehicle_saved),
        SettingsMessage.VEHICLE_DELETED to stringResource(R.string.settings_msg_vehicle_deleted),
        SettingsMessage.DEFAULT_VEHICLE_SET to stringResource(R.string.settings_msg_default_set),
        SettingsMessage.VEHICLE_DELETE_DEFAULT_BLOCKED to
            stringResource(R.string.settings_msg_delete_default_blocked),
        SettingsMessage.TRIPS_DELETED to stringResource(R.string.settings_msg_trips_deleted),
        SettingsMessage.TRIPS_DELETE_FAILED to
            stringResource(R.string.settings_msg_trips_delete_failed),
        SettingsMessage.SAVE_FAILED to stringResource(R.string.settings_msg_save_failed),
        SettingsMessage.TIP_SUCCESS to stringResource(R.string.settings_msg_tip_success),
        SettingsMessage.TIP_FAILED to stringResource(R.string.settings_msg_tip_failed),
    )

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(messageTexts[message].orEmpty())
        }
    }

    val actions = SettingsActions(
        setThemeMode = viewModel::setThemeMode,
        setKeepScreenOn = viewModel::setKeepScreenOn,
        setMapTileDownloads = viewModel::setMapTileDownloads,
        setAutoDetection = viewModel::setAutoDetectionEnabled,
        setRetentionDays = viewModel::setRetentionDays,
        deleteAllTrips = viewModel::deleteAllTrips,
        saveVehicle = viewModel::saveVehicle,
        deleteVehicle = viewModel::deleteVehicle,
        setDefaultVehicle = viewModel::setDefaultVehicle,
        saveScoringConfig = viewModel::saveScoringConfig,
        resetScoringConfig = viewModel::resetScoringConfig,
        purchase = { viewModel.purchase(activity) },
        restorePurchase = viewModel::restorePurchase,
        tip = { viewModel.tip(activity) },
        openSupport = {
            context.openExternalUrl(context.getString(R.string.settings_support_url))
        },
        openSource = {
            context.openExternalUrl(context.getString(R.string.settings_source_url))
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            live = live,
            permissions = permissions,
            actions = actions,
            onRequestAutoDetection = requestAutoDetection,
            onOpenPaywall = onOpenPaywall,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    live: LiveTripState,
    permissions: AppPermissionsState,
    actions: SettingsActions,
    onRequestAutoDetection: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    if (state.loading) {
        LoadingState(label = stringResource(R.string.common_loading))
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    var editorRequest by remember { mutableStateOf<VehicleEditorRequest?>(null) }
    var showMapConsentDialog by remember { mutableStateOf(false) }
    var showDeleteTripsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
            )
        }
        item {
            VehicleSection(
                state = state,
                locale = locale,
                onEdit = { vehicle, isNew -> editorRequest = VehicleEditorRequest(vehicle, isNew) },
                actions = actions,
                onOpenPaywall = onOpenPaywall,
            )
        }
        item {
            TrackingSection(
                state = state,
                live = live,
                permissions = permissions,
                actions = actions,
                onRequestAutoDetection = onRequestAutoDetection,
            )
        }
        item {
            MapSection(
                allowDownloads = state.settings.allowMapTileDownloads,
                onChange = { enabled ->
                    if (enabled) showMapConsentDialog = true else actions.setMapTileDownloads(false)
                },
            )
        }
        item {
            AppearanceSection(
                themeMode = state.settings.themeMode,
                onThemeMode = actions.setThemeMode,
            )
        }
        item {
            DataSection(
                retentionDays = state.settings.dataRetentionDays,
                onRetentionDays = actions.setRetentionDays,
                onDeleteAll = { showDeleteTripsDialog = true },
            )
        }
        item {
            ScoringSection(
                config = state.settings.scoringConfig,
                onChange = actions.saveScoringConfig,
                onReset = actions.resetScoringConfig,
                locale = locale,
            )
        }
        item {
            PremiumSection(
                state = state,
                actions = actions,
            )
        }
        item { AboutSection(actions = actions) }
        item { Spacer(Modifier.height(8.dp)) }
    }

    editorRequest?.let { request ->
        VehicleEditorDialog(
            initial = request.vehicle,
            unit = state.settings.consumptionUnit,
            isNew = request.isNew,
            onDismiss = { editorRequest = null },
            onSave = { vehicle, unit ->
                actions.saveVehicle(vehicle, unit)
                editorRequest = null
            },
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
                        actions.setMapTileDownloads(true)
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

    if (showDeleteTripsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteTripsDialog = false },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteTripsDialog = false
                        actions.deleteAllTrips()
                    },
                ) {
                    Text(stringResource(R.string.settings_delete_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTripsDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private data class VehicleEditorRequest(val vehicle: VehicleProfile, val isNew: Boolean)

@Composable
private fun VehicleSection(
    state: SettingsUiState,
    locale: Locale,
    onEdit: (VehicleProfile, Boolean) -> Unit,
    actions: SettingsActions,
    onOpenPaywall: () -> Unit,
) {
    val defaultVehicle = state.vehicles.firstOrNull { it.isDefault }
        ?: state.vehicles.firstOrNull()
    val visible = if (state.entitlement.isPremium) {
        state.vehicles
    } else {
        listOfNotNull(defaultVehicle)
    }

    SectionCard(title = stringResource(R.string.settings_vehicle_title)) {
        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_vehicle_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visible.forEach { vehicle ->
            VehicleRow(
                vehicle = vehicle,
                unit = state.settings.consumptionUnit,
                locale = locale,
                premium = state.entitlement.isPremium,
                onEdit = { onEdit(vehicle, false) },
                onDelete = { actions.deleteVehicle(vehicle) },
                onSetDefault = { actions.setDefaultVehicle(vehicle.id) },
            )
        }
        if (state.entitlement.isPremium) {
            NavigationRow(
                title = stringResource(R.string.settings_vehicle_add),
                description = stringResource(R.string.settings_vehicle_add_body),
                icon = Icons.Filled.Add,
                onClick = {
                    val base = defaultVehicle ?: VehicleProfile.default()
                    onEdit(
                        VehicleProfile(
                            id = UUID.randomUUID().toString(),
                            name = "",
                            consumptionLitersPer100Km = base.consumptionLitersPer100Km,
                            fuelPricePerLiter = base.fuelPricePerLiter,
                            currency = base.currency,
                            isDefault = state.vehicles.isEmpty(),
                        ),
                        true,
                    )
                },
            )
        } else {
            Spacer(Modifier.height(8.dp))
            PremiumNotice(text = stringResource(R.string.settings_vehicle_free_note))
            NavigationRow(
                title = stringResource(R.string.settings_vehicle_add_locked),
                icon = Icons.Filled.Lock,
                onClick = onOpenPaywall,
            )
        }
    }
}

@Composable
private fun VehicleRow(
    vehicle: VehicleProfile,
    unit: ConsumptionUnit,
    locale: Locale,
    premium: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
) {
    val displayName = vehicle.name.ifBlank {
        stringResource(R.string.settings_vehicle_name_fallback)
    }
    Column {
        NavigationRow(
            title = displayName,
            description = stringResource(
                R.string.settings_vehicle_summary,
                Formatters.consumption(
                    vehicle.consumptionLitersPer100Km,
                    unit,
                    locale,
                ),
                Formatters.money(vehicle.fuelPricePerLiter, vehicle.currency, locale),
            ),
            icon = Icons.Filled.DirectionsCar,
            onClick = onEdit,
            trailing = if (vehicle.isDefault) {
                {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_vehicle_default_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            } else {
                null
            },
        )
        if (premium && !vehicle.isDefault) {
            Row {
                TextButton(onClick = onSetDefault) {
                    Text(stringResource(R.string.settings_vehicle_set_default))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.settings_vehicle_delete))
                }
            }
        }
    }
}

@Composable
private fun TrackingSection(
    state: SettingsUiState,
    live: LiveTripState,
    permissions: AppPermissionsState,
    actions: SettingsActions,
    onRequestAutoDetection: () -> Unit,
) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.settings_tracking_title)) {
        SwitchRow(
            title = stringResource(R.string.settings_auto_detection_title),
            description = stringResource(R.string.settings_auto_detection_body),
            checked = state.settings.autoDetectionEnabled,
            enabled = !live.recording,
            onCheckedChange = { enabled ->
                if (enabled) onRequestAutoDetection() else actions.setAutoDetection(false)
            },
        )
        if (!permissions.hasFineLocation) {
            Spacer(Modifier.height(8.dp))
            NoticeBanner(
                text = stringResource(R.string.settings_permission_needed),
                actionLabel = stringResource(R.string.home_permission_action),
                onAction = permissions.requestForegroundLocation,
            )
        } else if (state.settings.autoDetectionEnabled && !permissions.hasBackgroundLocation) {
            Spacer(Modifier.height(8.dp))
            NoticeBanner(
                text = stringResource(R.string.settings_background_missing),
                actionLabel = stringResource(R.string.common_open_settings),
                onAction = { openAppSettings(context) },
            )
        }
        SwitchRow(
            title = stringResource(R.string.settings_keep_screen_on_title),
            description = stringResource(R.string.settings_keep_screen_on_body),
            checked = state.settings.keepScreenOnDuringTrip,
            onCheckedChange = actions.setKeepScreenOn,
        )
    }
}

@Composable
private fun MapSection(allowDownloads: Boolean, onChange: (Boolean) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_map_title)) {
        SwitchRow(
            title = stringResource(R.string.settings_map_consent_title),
            description = stringResource(R.string.settings_map_consent_body),
            checked = allowDownloads,
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun AppearanceSection(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_appearance_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                                    ThemeMode.LIGHT -> R.string.settings_theme_light
                                    ThemeMode.DARK -> R.string.settings_theme_dark
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DataSection(
    retentionDays: Int,
    onRetentionDays: (Int) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val presets = listOf(0, 7, 30, 90, 365)
    SectionCard(title = stringResource(R.string.settings_data_title)) {
        Text(
            text = stringResource(R.string.settings_retention_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        presets.chunked(3).forEach { rowPresets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPresets.forEach { days ->
                    FilterChip(
                        selected = retentionDays == days,
                        onClick = { onRetentionDays(days) },
                        label = {
                            Text(
                                if (days == 0) {
                                    stringResource(R.string.settings_retention_forever)
                                } else {
                                    pluralStringResource(
                                        R.plurals.settings_retention_days,
                                        days,
                                        days,
                                    )
                                },
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_retention_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onDeleteAll,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.settings_delete_all))
        }
    }
}

@Composable
private fun ScoringSection(
    config: ScoringConfig,
    onChange: (ScoringConfig) -> Unit,
    onReset: () -> Unit,
    locale: Locale,
) {
    var draft by remember(config) { mutableStateOf(config) }
    val defaults = ScoringConfig()

    SectionCard(title = stringResource(R.string.settings_scoring_title)) {
        Text(
            text = stringResource(R.string.settings_scoring_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ThresholdSlider(
            label = stringResource(R.string.settings_scoring_accel),
            valueText = stringResource(
                R.string.settings_scoring_mps2,
                oneDecimal(draft.hardAccelThresholdMps2, locale),
            ),
            defaultText = stringResource(
                R.string.settings_scoring_default,
                stringResource(
                    R.string.settings_scoring_mps2,
                    oneDecimal(defaults.hardAccelThresholdMps2, locale),
                ),
            ),
            value = draft.hardAccelThresholdMps2.toFloat(),
            range = 1f..5f,
            onValueChange = {
                draft = draft.copy(hardAccelThresholdMps2 = roundToOne(it.toDouble()))
            },
            onValueChangeFinished = { onChange(draft) },
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_scoring_brake),
            valueText = stringResource(
                R.string.settings_scoring_mps2,
                oneDecimal(draft.hardBrakeThresholdMps2, locale),
            ),
            defaultText = stringResource(
                R.string.settings_scoring_default,
                stringResource(
                    R.string.settings_scoring_mps2,
                    oneDecimal(defaults.hardBrakeThresholdMps2, locale),
                ),
            ),
            value = draft.hardBrakeThresholdMps2.toFloat(),
            range = 1f..6f,
            onValueChange = {
                draft = draft.copy(hardBrakeThresholdMps2 = roundToOne(it.toDouble()))
            },
            onValueChangeFinished = { onChange(draft) },
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_scoring_corner),
            valueText = stringResource(
                R.string.settings_scoring_mps2,
                oneDecimal(draft.sharpCornerThresholdMps2, locale),
            ),
            defaultText = stringResource(
                R.string.settings_scoring_default,
                stringResource(
                    R.string.settings_scoring_mps2,
                    oneDecimal(defaults.sharpCornerThresholdMps2, locale),
                ),
            ),
            value = draft.sharpCornerThresholdMps2.toFloat(),
            range = 1f..5f,
            onValueChange = {
                draft = draft.copy(sharpCornerThresholdMps2 = roundToOne(it.toDouble()))
            },
            onValueChangeFinished = { onChange(draft) },
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_scoring_speed),
            valueText = stringResource(
                R.string.settings_scoring_speed_value,
                draft.overspeedThresholdKmh.roundToInt(),
            ),
            defaultText = stringResource(
                R.string.settings_scoring_default,
                stringResource(
                    R.string.settings_scoring_speed_value,
                    defaults.overspeedThresholdKmh.roundToInt(),
                ),
            ),
            value = draft.overspeedThresholdKmh.toFloat(),
            range = 40f..140f,
            onValueChange = {
                draft = draft.copy(overspeedThresholdKmh = it.toDouble().roundToInt().toDouble())
            },
            onValueChangeFinished = { onChange(draft) },
        )
        ThresholdSlider(
            label = stringResource(R.string.settings_scoring_idle),
            valueText = stringResource(
                R.string.settings_scoring_idle_value,
                (draft.idleMinDurationMs / 1_000).toInt(),
            ),
            defaultText = stringResource(
                R.string.settings_scoring_default,
                stringResource(
                    R.string.settings_scoring_idle_value,
                    (defaults.idleMinDurationMs / 1_000).toInt(),
                ),
            ),
            value = (draft.idleMinDurationMs / 1_000).toFloat(),
            range = 10f..300f,
            onValueChange = {
                draft = draft.copy(idleMinDurationMs = it.roundToInt() * 1_000L)
            },
            onValueChangeFinished = { onChange(draft) },
        )
        TextButton(
            onClick = {
                draft = ScoringConfig()
                onReset()
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.settings_scoring_reset))
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    valueText: String,
    defaultText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
        )
        Text(
            text = defaultText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun oneDecimal(value: Double, locale: Locale): String =
    String.format(locale, "%.1f", value)

private fun roundToOne(value: Double): Double = (value * 10).roundToInt() / 10.0

@Composable
private fun PremiumSection(
    state: SettingsUiState,
    actions: SettingsActions,
) {
    val entitlement = state.entitlement
    SectionCard(title = stringResource(R.string.settings_premium_title)) {
        KeyValueRow(
            label = stringResource(R.string.settings_premium_status),
            value = stringResource(
                if (entitlement.isPremium) {
                    R.string.settings_premium_active
                } else {
                    R.string.settings_premium_free
                },
            ),
        )
        KeyValueRow(
            label = stringResource(R.string.settings_premium_source),
            value = stringResource(
                when (entitlement.source) {
                    PremiumSource.PLAY_BILLING -> R.string.premium_source_play
                    PremiumSource.COMMUNITY_BUILD -> R.string.premium_source_community
                    PremiumSource.DEBUG -> R.string.premium_source_debug
                    PremiumSource.NONE -> R.string.premium_source_none
                },
            ),
        )
        Spacer(Modifier.height(8.dp))
        when {
            entitlement.source == PremiumSource.COMMUNITY_BUILD -> Text(
                text = stringResource(R.string.settings_premium_community_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                if (!entitlement.isPremium) {
                    Text(
                        text = stringResource(R.string.settings_premium_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.billingAvailable) {
                        Button(
                            onClick = actions.purchase,
                            enabled = !state.working,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            if (state.working) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.settings_premium_buy))
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text(
                            text = stringResource(R.string.settings_premium_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (state.billingAvailable) {
                    OutlinedButton(
                        onClick = actions.restorePurchase,
                        enabled = !state.working,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.settings_premium_restore))
                    }
                }
            }
        }
        if (state.working) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.billingAvailable) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_tip_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = actions.tip,
                enabled = !state.working,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_tip_buy))
            }
        }
        Spacer(Modifier.height(8.dp))
        NavigationRow(
            title = stringResource(R.string.settings_support_title),
            description = stringResource(R.string.settings_support_body),
            icon = Icons.Filled.Star,
            onClick = actions.openSupport,
        )
    }
}

@Composable
private fun AboutSection(actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_about_title)) {
        KeyValueRow(
            label = stringResource(R.string.settings_about_version),
            value = BuildConfig.VERSION_NAME,
        )
        Text(
            text = stringResource(R.string.settings_about_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_about_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        NavigationRow(
            title = stringResource(R.string.settings_about_source),
            icon = Icons.Filled.Info,
            onClick = actions.openSource,
        )
        Text(
            text = stringResource(R.string.settings_about_no_ads),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
