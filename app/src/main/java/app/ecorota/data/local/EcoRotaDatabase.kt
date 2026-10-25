package app.ecorota.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, DrivingEventEntity::class, VehicleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EcoRotaDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    abstract fun vehicleDao(): VehicleDao

    companion object {
        const val DATABASE_NAME = "ecorota.db"

        fun build(context: Context): EcoRotaDatabase = Room.databaseBuilder(
            context.applicationContext,
            EcoRotaDatabase::class.java,
            DATABASE_NAME,
        ).build()
    }
}
