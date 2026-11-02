package app.ecorota.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TripEntity::class, DrivingEventEntity::class, VehicleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EcoRotaDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    abstract fun vehicleDao(): VehicleDao

    /** Truncates the WAL file so deleted trip coordinates do not linger on disk. */
    fun checkpointAndTruncateWal() {
        runCatching {
            openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        }
    }

    companion object {
        const val DATABASE_NAME = "ecorota.db"

        fun build(context: Context): EcoRotaDatabase = Room.databaseBuilder(
            context.applicationContext,
            EcoRotaDatabase::class.java,
            DATABASE_NAME,
        ).addCallback(
            object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    runCatching { db.query("PRAGMA secure_delete = ON").close() }
                }
            },
        ).build()
    }
}
