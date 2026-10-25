package app.ecorota.core.model

/**
 * Fuel estimate for one trip.
 *
 * [estimatedLiters] is the trip's total fuel use, derived from the user's own
 * average consumption. [avoidableLiters] is the portion attributed to the
 * inefficient events detected in that trip, capped at the total. The per
 * category values are the physics-based contributions that produce it.
 */
data class FuelEstimate(
    val estimatedLiters: Double,
    val avoidableLiters: Double,
    val idleLiters: Double,
    val overspeedLiters: Double,
    val brakingLiters: Double,
    val accelerationLiters: Double,
    val corneringLiters: Double,
    val fuelPricePerLiter: Double,
    val currency: String,
) {
    val avoidableCost: Double get() = avoidableLiters * fuelPricePerLiter
    val estimatedCost: Double get() = estimatedLiters * fuelPricePerLiter
    val avoidablePercent: Double
        get() = if (estimatedLiters > 0.0) avoidableLiters / estimatedLiters * 100.0 else 0.0

    companion object {
        fun empty(price: Double = 0.0, currency: String = "BRL") = FuelEstimate(
            estimatedLiters = 0.0,
            avoidableLiters = 0.0,
            idleLiters = 0.0,
            overspeedLiters = 0.0,
            brakingLiters = 0.0,
            accelerationLiters = 0.0,
            corneringLiters = 0.0,
            fuelPricePerLiter = price,
            currency = currency,
        )
    }
}
