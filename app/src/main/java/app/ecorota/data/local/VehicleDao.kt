package app.ecorota.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY isDefault DESC, name COLLATE NOCASE ASC, id ASC")
    abstract fun observeVehicles(): Flow<List<VehicleEntity>>

    /**
     * The flagged default, or the first vehicle by name when no row is flagged
     * (deleting the default vehicle leaves the others usable without silently
     * rewriting the table).
     */
    @Query(
        "SELECT * FROM vehicles ORDER BY isDefault DESC, name COLLATE NOCASE ASC, id ASC LIMIT 1",
    )
    abstract fun observeDefaultVehicle(): Flow<VehicleEntity?>

    @Query(
        "SELECT * FROM vehicles ORDER BY isDefault DESC, name COLLATE NOCASE ASC, id ASC LIMIT 1",
    )
    abstract suspend fun getDefaultVehicle(): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertVehicle(vehicle: VehicleEntity)

    /** Keeps an existing row when two callers bootstrap the table at the same time. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertVehicleIfAbsent(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET isDefault = 0 WHERE isDefault = 1")
    abstract suspend fun clearDefaults()

    @Query("UPDATE vehicles SET isDefault = 1 WHERE id = :vehicleId")
    abstract suspend fun markDefault(vehicleId: String)

    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    abstract suspend fun deleteVehicle(vehicleId: String)

    @Transaction
    open suspend fun upsertVehicle(vehicle: VehicleEntity) {
        if (vehicle.isDefault) clearDefaults()
        insertVehicle(vehicle)
    }

    @Transaction
    open suspend fun setDefault(vehicleId: String) {
        clearDefaults()
        markDefault(vehicleId)
    }
}
