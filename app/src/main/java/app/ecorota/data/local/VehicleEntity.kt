package app.ecorota.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val consumptionLitersPer100Km: Double,
    val fuelPricePerLiter: Double,
    val currency: String,
    val isDefault: Boolean,
)
