package app.ecorota.data.local

import app.ecorota.core.model.VehicleProfile

object VehicleMappers {

    fun VehicleProfile.toEntity(): VehicleEntity = VehicleEntity(
        id = id,
        name = name,
        consumptionLitersPer100Km = consumptionLitersPer100Km,
        fuelPricePerLiter = fuelPricePerLiter,
        currency = currency,
        isDefault = isDefault,
    )

    fun VehicleEntity.toProfile(): VehicleProfile = VehicleProfile(
        id = id,
        name = name,
        consumptionLitersPer100Km = consumptionLitersPer100Km,
        fuelPricePerLiter = fuelPricePerLiter,
        currency = currency,
        isDefault = isDefault,
    )
}
