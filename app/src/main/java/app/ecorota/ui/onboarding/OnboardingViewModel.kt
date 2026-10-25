package app.ecorota.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ecorota.core.model.AppSettings
import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.core.model.FuelUnits
import app.ecorota.core.model.VehicleProfile
import app.ecorota.di.AppContainer
import app.ecorota.ui.format.Formatters
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingError {
    CONSUMPTION,
    PRICE,
    CURRENCY,
    SAVE_FAILED,
}

data class OnboardingUiState(
    val loading: Boolean = true,
    val vehicleName: String = "",
    val consumptionInput: String = "",
    val priceInput: String = "",
    val currency: String = VehicleProfile.DEFAULT_CURRENCY,
    val unit: ConsumptionUnit = ConsumptionUnit.LITERS_PER_100_KM,
    val saving: Boolean = false,
    val error: OnboardingError? = null,
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = runCatching { container.settingsRepository.current() }
                .getOrDefault(AppSettings())
            val vehicle = runCatching { container.vehicleRepository.defaultVehicle() }
                .getOrElse { VehicleProfile.default() }
            val locale = Locale.getDefault()
            mutableState.value = OnboardingUiState(
                loading = false,
                vehicleName = vehicle.name,
                consumptionInput = Formatters.consumptionValue(
                    litersPer100Km = vehicle.consumptionLitersPer100Km,
                    unit = settings.consumptionUnit,
                    locale = locale,
                ),
                priceInput = String.format(locale, "%.2f", vehicle.fuelPricePerLiter),
                currency = if (vehicle.currency.length == 3) vehicle.currency else settings.currency,
                unit = settings.consumptionUnit,
            )
        }
    }

    fun setVehicleName(value: String) {
        mutableState.update { it.copy(vehicleName = value, error = null) }
    }

    fun setConsumption(value: String) {
        mutableState.update { it.copy(consumptionInput = value, error = null) }
    }

    fun setPrice(value: String) {
        mutableState.update { it.copy(priceInput = value, error = null) }
    }

    /** Keeps the typed number when the user flips L/100 km and km/L. */
    fun setUnit(unit: ConsumptionUnit) {
        mutableState.update { current ->
            if (current.unit == unit) {
                current
            } else {
                val parsed = Formatters.parseDecimal(current.consumptionInput)
                val converted = parsed?.takeIf { it > 0.0 }?.let { value ->
                    val liters = FuelUnits.toLitersPer100Km(value, current.unit)
                    Formatters.consumptionValue(liters, unit, Locale.getDefault())
                }
                current.copy(
                    unit = unit,
                    consumptionInput = converted ?: current.consumptionInput,
                    error = null,
                )
            }
        }
    }

    fun setCurrency(value: String) {
        val normalized = value.filter { it.isLetter() }.uppercase(Locale.ROOT).take(3)
        mutableState.update { it.copy(currency = normalized, error = null) }
    }

    fun setAutoDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.trackingController.setAutoDetectionEnabled(enabled)
        }
    }

    fun complete() {
        val current = mutableState.value
        if (current.saving) return

        val consumption = Formatters.parseDecimal(current.consumptionInput)
        val litersPer100Km = consumption?.let {
            FuelUnits.toLitersPer100Km(it, current.unit)
        }
        if (consumption == null || consumption <= 0.0 || litersPer100Km == null ||
            litersPer100Km <= 0.0
        ) {
            mutableState.update { it.copy(error = OnboardingError.CONSUMPTION) }
            return
        }

        val price = Formatters.parseDecimal(current.priceInput)
        if (price == null || price < 0.0) {
            mutableState.update { it.copy(error = OnboardingError.PRICE) }
            return
        }

        if (current.currency.length != 3) {
            mutableState.update { it.copy(error = OnboardingError.CURRENCY) }
            return
        }

        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val saved = runCatching {
                container.vehicleRepository.upsert(
                    VehicleProfile(
                        id = VehicleProfile.DEFAULT_ID,
                        name = current.vehicleName.trim(),
                        consumptionLitersPer100Km = litersPer100Km,
                        fuelPricePerLiter = price,
                        currency = current.currency,
                        isDefault = true,
                    ),
                )
                container.settingsRepository.update { settings ->
                    settings.copy(
                        onboardingCompleted = true,
                        consumptionUnit = current.unit,
                        currency = current.currency,
                    )
                }
            }.isSuccess
            if (saved) {
                container.trackingController.refresh()
            } else {
                mutableState.update { it.copy(saving = false, error = OnboardingError.SAVE_FAILED) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(container) }
        }
    }
}
