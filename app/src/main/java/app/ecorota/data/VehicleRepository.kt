package app.ecorota.data

import app.ecorota.core.model.VehicleProfile
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun observeVehicles(): Flow<List<VehicleProfile>>

    fun observeDefaultVehicle(): Flow<VehicleProfile?>

    suspend fun defaultVehicle(): VehicleProfile

    suspend fun upsert(vehicle: VehicleProfile)

    suspend fun delete(vehicleId: String)

    suspend fun setDefault(vehicleId: String)
}
