package app.ecorota.data.repository

import app.ecorota.core.model.VehicleProfile
import app.ecorota.data.VehicleRepository
import app.ecorota.data.local.EcoRotaDatabase
import app.ecorota.data.local.VehicleMappers.toEntity
import app.ecorota.data.local.VehicleMappers.toProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomVehicleRepository(
    database: EcoRotaDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VehicleRepository {

    private val vehicleDao = database.vehicleDao()

    override fun observeVehicles(): Flow<List<VehicleProfile>> =
        vehicleDao.observeVehicles()
            .map { vehicles -> vehicles.map { it.toProfile() } }
            .flowOn(ioDispatcher)

    override fun observeDefaultVehicle(): Flow<VehicleProfile?> =
        vehicleDao.observeDefaultVehicle()
            .map { it?.toProfile() }
            .flowOn(ioDispatcher)

    override suspend fun defaultVehicle(): VehicleProfile = withContext(ioDispatcher) {
        vehicleDao.getDefaultVehicle()?.toProfile() ?: bootstrapDefaultVehicle()
    }

    override suspend fun upsert(vehicle: VehicleProfile) {
        withContext(ioDispatcher) { vehicleDao.upsertVehicle(vehicle.toEntity()) }
    }

    override suspend fun delete(vehicleId: String) {
        withContext(ioDispatcher) { vehicleDao.deleteVehicle(vehicleId) }
    }

    override suspend fun setDefault(vehicleId: String) {
        withContext(ioDispatcher) { vehicleDao.setDefault(vehicleId) }
    }

    /** First launch, or every vehicle was deleted: recreate the preset profile. */
    private suspend fun bootstrapDefaultVehicle(): VehicleProfile {
        val preset = VehicleProfile.default()
        vehicleDao.insertVehicleIfAbsent(preset.toEntity())
        return vehicleDao.getDefaultVehicle()?.toProfile() ?: preset
    }
}
