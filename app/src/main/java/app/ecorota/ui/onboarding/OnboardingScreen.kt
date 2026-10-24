package app.ecorota.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ecorota.R
import app.ecorota.core.model.AppSettings
import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.di.AppContainer
import app.ecorota.ui.components.SwitchRow
import app.ecorota.ui.permissions.AppPermissionsState
import app.ecorota.ui.permissions.rememberAppPermissions
import app.ecorota.ui.permissions.rememberAutoDetectionOptIn
import app.ecorota.ui.theme.extraColors

private const val PAGE_PRIVACY = 0
private const val PAGE_PERMISSIONS = 1
private const val PAGE_VEHICLE = 2
private const val PAGE_COUNT = 3

@Composable
fun OnboardingRoute(container: AppContainer) {
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val permissions = rememberAppPermissions()
    val requestAutoDetection = rememberAutoDetectionOptIn(
        onEnable = { viewModel.setAutoDetectionEnabled(true) },
    )

    OnboardingScreen(
        state = state,
        permissions = permissions,
        autoDetectionEnabled = settings.autoDetectionEnabled,
        onNameChange = viewModel::setVehicleName,
        onConsumptionChange = viewModel::setConsumption,
        onPriceChange = viewModel::setPrice,
        onUnitChange = viewModel::setUnit,
        onCurrencyChange = viewModel::setCurrency,
        onAutoDetectionChange = { enabled ->
            if (enabled) requestAutoDetection() else viewModel.setAutoDetectionEnabled(false)
        },
        onComplete = viewModel::complete,
    )
}

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    permissions: AppPermissionsState,
    autoDetectionEnabled: Boolean,
    onNameChange: (String) -> Unit,
    onConsumptionChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onUnitChange: (ConsumptionUnit) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onAutoDetectionChange: (Boolean) -> Unit,
    onComplete: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_PRIVACY) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                StepIndicator(current = page, total = PAGE_COUNT)
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding-page",
                modifier = Modifier.weight(1f),
            ) { target ->
                when (target) {
                    PAGE_PRIVACY -> PrivacyPage()
                    PAGE_PERMISSIONS -> PermissionsPage(
                        permissions = permissions,
                        autoDetectionEnabled = autoDetectionEnabled,
                        onAutoDetectionChange = onAutoDetectionChange,
                    )
                    else -> VehiclePage(
                        state = state,
                        onNameChange = onNameChange,
                        onConsumptionChange = onConsumptionChange,
                        onPriceChange = onPriceChange,
                        onUnitChange = onUnitChange,
                        onCurrencyChange = onCurrencyChange,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page > PAGE_PRIVACY) {
                    TextButton(
                        onClick = { page -= 1 },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (page == PAGE_VEHICLE) onComplete() else page += 1
                    },
                    enabled = !state.saving && !state.loading,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (page == PAGE_VEHICLE) {
                            stringResource(R.string.onboarding_finish)
                        } else {
                            stringResource(R.string.onboarding_continue)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    val description = stringResource(R.string.onboarding_step_indicator, current + 1, total)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(
                        color = if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PrivacyPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Eco,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_privacy_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_privacy_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        BenefitRow(
            icon = Icons.Filled.CloudOff,
            title = stringResource(R.string.onboarding_benefit_offline_title),
            body = stringResource(R.string.onboarding_benefit_offline_body),
        )
        BenefitRow(
            icon = Icons.Filled.PersonOff,
            title = stringResource(R.string.onboarding_benefit_account_title),
            body = stringResource(R.string.onboarding_benefit_account_body),
        )
        BenefitRow(
            icon = Icons.Filled.Block,
            title = stringResource(R.string.onboarding_benefit_ads_title),
            body = stringResource(R.string.onboarding_benefit_ads_body),
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = stringResource(R.string.onboarding_privacy_storage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String, body: String) {
    Row(modifier = Modifier.padding(vertical = 10.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionsPage(
    permissions: AppPermissionsState,
    autoDetectionEnabled: Boolean,
    onAutoDetectionChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            color = extraColors.warningSurface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_permissions_disclosure),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PermissionStatusRow(
            label = stringResource(R.string.onboarding_permissions_location_label),
            granted = permissions.hasFineLocation,
        )
        val notificationsRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (notificationsRequired) {
            PermissionStatusRow(
                label = stringResource(R.string.onboarding_permissions_notifications_label),
                granted = permissions.hasNotifications,
            )
        }
        Spacer(Modifier.height(8.dp))
        val allGranted = permissions.hasFineLocation &&
            (!notificationsRequired || permissions.hasNotifications)
        Button(
            onClick = { permissions.requestOnboardingAccess() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            enabled = !allGranted,
        ) {
            Text(
                text = if (allGranted) {
                    stringResource(R.string.onboarding_permissions_allowed)
                } else {
                    stringResource(R.string.onboarding_permissions_allow)
                },
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(
                R.string.onboarding_permissions_background_state,
                stringResource(
                    if (permissions.hasBackgroundLocation) {
                        R.string.onboarding_permissions_background_all_time
                    } else {
                        R.string.onboarding_permissions_background_while_using
                    },
                ),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
        ) {
            SwitchRow(
                title = stringResource(R.string.onboarding_auto_detection_title),
                description = stringResource(R.string.onboarding_auto_detection_body),
                checked = autoDetectionEnabled,
                onCheckedChange = onAutoDetectionChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_permissions_optional_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(
                if (granted) R.string.common_granted else R.string.common_not_granted,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun VehiclePage(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onConsumptionChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onUnitChange: (ConsumptionUnit) -> Unit,
    onCurrencyChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_vehicle_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_vehicle_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.vehicleName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onboarding_vehicle_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.unit == ConsumptionUnit.LITERS_PER_100_KM,
                onClick = { onUnitChange(ConsumptionUnit.LITERS_PER_100_KM) },
                label = { Text(stringResource(R.string.consumption_unit_l_per_100_km)) },
            )
            FilterChip(
                selected = state.unit == ConsumptionUnit.KILOMETERS_PER_LITER,
                onClick = { onUnitChange(ConsumptionUnit.KILOMETERS_PER_LITER) },
                label = { Text(stringResource(R.string.consumption_unit_km_per_liter)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.consumptionInput,
            onValueChange = onConsumptionChange,
            label = { Text(stringResource(R.string.onboarding_vehicle_consumption)) },
            suffix = { Text(consumptionUnitSuffix(state.unit)) },
            singleLine = true,
            isError = state.error == OnboardingError.CONSUMPTION,
            supportingText = if (state.error == OnboardingError.CONSUMPTION) {
                { Text(stringResource(R.string.onboarding_consumption_error)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.priceInput,
            onValueChange = onPriceChange,
            label = { Text(stringResource(R.string.onboarding_vehicle_price)) },
            singleLine = true,
            isError = state.error == OnboardingError.PRICE,
            supportingText = if (state.error == OnboardingError.PRICE) {
                { Text(stringResource(R.string.onboarding_price_error)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.currency,
            onValueChange = onCurrencyChange,
            label = { Text(stringResource(R.string.onboarding_vehicle_currency)) },
            singleLine = true,
            isError = state.error == OnboardingError.CURRENCY,
            supportingText = if (state.error == OnboardingError.CURRENCY) {
                { Text(stringResource(R.string.onboarding_currency_error)) }
            } else {
                { Text(stringResource(R.string.onboarding_vehicle_currency_hint)) }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.error == OnboardingError.SAVE_FAILED) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_save_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Start,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun consumptionUnitSuffix(unit: ConsumptionUnit): String = when (unit) {
    ConsumptionUnit.LITERS_PER_100_KM -> stringResource(R.string.consumption_unit_l_per_100_km)
    ConsumptionUnit.KILOMETERS_PER_LITER -> stringResource(R.string.consumption_unit_km_per_liter)
}
