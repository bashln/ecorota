package app.ecorota.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ecorota.R
import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.core.model.FuelUnits
import app.ecorota.core.model.VehicleProfile
import app.ecorota.ui.format.Formatters
import java.util.Locale

private enum class VehicleFieldError { CONSUMPTION, PRICE, CURRENCY }

@Composable
fun VehicleEditorDialog(
    initial: VehicleProfile,
    unit: ConsumptionUnit,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (VehicleProfile, ConsumptionUnit) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var unitState by rememberSaveable(initial.id) { mutableStateOf(unit.name) }
    var consumptionInput by rememberSaveable(initial.id) {
        mutableStateOf(
            Formatters.consumptionValue(initial.consumptionLitersPer100Km, unit, locale),
        )
    }
    var priceInput by rememberSaveable(initial.id) {
        mutableStateOf(String.format(locale, "%.2f", initial.fuelPricePerLiter))
    }
    var currency by rememberSaveable(initial.id) { mutableStateOf(initial.currency) }
    var error by remember { mutableStateOf<VehicleFieldError?>(null) }

    val currentUnit = ConsumptionUnit.entries.firstOrNull { it.name == unitState } ?: unit

    fun changeUnit(newUnit: ConsumptionUnit) {
        if (newUnit == currentUnit) return
        val parsed = Formatters.parseDecimal(consumptionInput)
        val converted = parsed?.takeIf { it > 0.0 }?.let { value ->
            val liters = FuelUnits.toLitersPer100Km(value, currentUnit)
            Formatters.consumptionValue(liters, newUnit, locale)
        }
        unitState = newUnit.name
        if (converted != null) consumptionInput = converted
        error = null
    }

    fun save() {
        val consumption = Formatters.parseDecimal(consumptionInput)
        val liters = consumption?.let { FuelUnits.toLitersPer100Km(it, currentUnit) }
        if (consumption == null || consumption <= 0.0 || liters == null || liters <= 0.0) {
            error = VehicleFieldError.CONSUMPTION
            return
        }
        val price = Formatters.parseDecimal(priceInput)
        if (price == null || price < 0.0) {
            error = VehicleFieldError.PRICE
            return
        }
        if (currency.length != 3) {
            error = VehicleFieldError.CURRENCY
            return
        }
        onSave(
            initial.copy(
                name = name.trim(),
                consumptionLitersPer100Km = liters,
                fuelPricePerLiter = price,
                currency = currency,
            ),
            currentUnit,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isNew) {
                        R.string.vehicle_editor_title_add
                    } else {
                        R.string.vehicle_editor_title_edit
                    },
                ),
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.vehicle_editor_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentUnit == ConsumptionUnit.LITERS_PER_100_KM,
                        onClick = { changeUnit(ConsumptionUnit.LITERS_PER_100_KM) },
                        label = { Text(stringResource(R.string.consumption_unit_l_per_100_km)) },
                    )
                    FilterChip(
                        selected = currentUnit == ConsumptionUnit.KILOMETERS_PER_LITER,
                        onClick = { changeUnit(ConsumptionUnit.KILOMETERS_PER_LITER) },
                        label = { Text(stringResource(R.string.consumption_unit_km_per_liter)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = consumptionInput,
                    onValueChange = {
                        consumptionInput = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.vehicle_editor_consumption)) },
                    suffix = {
                        Text(
                            when (currentUnit) {
                                ConsumptionUnit.LITERS_PER_100_KM ->
                                    stringResource(R.string.consumption_unit_l_per_100_km)
                                ConsumptionUnit.KILOMETERS_PER_LITER ->
                                    stringResource(R.string.consumption_unit_km_per_liter)
                            },
                        )
                    },
                    isError = error == VehicleFieldError.CONSUMPTION,
                    supportingText = if (error == VehicleFieldError.CONSUMPTION) {
                        { Text(stringResource(R.string.vehicle_editor_error_consumption)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = {
                        priceInput = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.vehicle_editor_price)) },
                    isError = error == VehicleFieldError.PRICE,
                    supportingText = if (error == VehicleFieldError.PRICE) {
                        { Text(stringResource(R.string.vehicle_editor_error_price)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = currency,
                    onValueChange = { value ->
                        currency = value.filter { it.isLetter() }
                            .uppercase(Locale.ROOT)
                            .take(3)
                        error = null
                    },
                    label = { Text(stringResource(R.string.vehicle_editor_currency)) },
                    isError = error == VehicleFieldError.CURRENCY,
                    supportingText = if (error == VehicleFieldError.CURRENCY) {
                        { Text(stringResource(R.string.vehicle_editor_error_currency)) }
                    } else {
                        { Text(stringResource(R.string.onboarding_vehicle_currency_hint)) }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::save) {
                Text(stringResource(R.string.vehicle_editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
