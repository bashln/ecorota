package app.ecorota.core.model

/**
 * A car the user wants to track. Consumption is stored canonically as L/100 km;
 * the settings UI converts to km/L when the user prefers that unit.
 */
data class VehicleProfile(
    val id: String,
    val name: String,
    val consumptionLitersPer100Km: Double,
    val fuelPricePerLiter: Double,
    val currency: String = "BRL",
    val isDefault: Boolean = true,
) {
    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_CONSUMPTION_L_PER_100_KM = 8.5
        const val DEFAULT_FUEL_PRICE = 6.09
        const val DEFAULT_CURRENCY = "BRL"

        fun default() = VehicleProfile(
            id = DEFAULT_ID,
            name = "",
            consumptionLitersPer100Km = DEFAULT_CONSUMPTION_L_PER_100_KM,
            fuelPricePerLiter = DEFAULT_FUEL_PRICE,
            currency = DEFAULT_CURRENCY,
        )
    }
}

enum class ConsumptionUnit {
    LITERS_PER_100_KM,
    KILOMETERS_PER_LITER,
}

object FuelUnits {
    fun toLitersPer100Km(value: Double, unit: ConsumptionUnit): Double = when (unit) {
        ConsumptionUnit.LITERS_PER_100_KM -> value
        ConsumptionUnit.KILOMETERS_PER_LITER ->
            if (value > 0.0) 100.0 / value else VehicleProfile.DEFAULT_CONSUMPTION_L_PER_100_KM
    }

    fun fromLitersPer100Km(value: Double, unit: ConsumptionUnit): Double = when (unit) {
        ConsumptionUnit.LITERS_PER_100_KM -> value
        ConsumptionUnit.KILOMETERS_PER_LITER -> if (value > 0.0) 100.0 / value else 0.0
    }
}
