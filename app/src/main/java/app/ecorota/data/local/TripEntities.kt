package app.ecorota.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Stored trip summary plus its score breakdown and fuel estimate.
 *
 * Route geometry and speed samples live in their own compact columns
 * ([routePolyline] and [speedSeries]) instead of child tables: a trip has
 * hundreds of points but they are only ever read as a whole.
 */
@Entity(tableName = "trips", indices = [Index("startedAtMs")])
data class TripEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val createdAtMs: Long,
    val distanceMeters: Double,
    val movingDurationMs: Long,
    val idleDurationMs: Long,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val score: Int,
    val behaviorScore: Int,
    val fuelScore: Int,
    val fuelWeight: Double,
    val accelScore: Int,
    val brakeScore: Int,
    val cornerScore: Int,
    val speedScore: Int,
    val idleScore: Int,
    val accelWeight: Double,
    val brakeWeight: Double,
    val cornerWeight: Double,
    val speedWeight: Double,
    val idleWeight: Double,
    val accelEventCount: Int,
    val brakeEventCount: Int,
    val cornerEventCount: Int,
    val speedEventCount: Int,
    val idleEventCount: Int,
    val accelWeightedCount: Double,
    val brakeWeightedCount: Double,
    val cornerWeightedCount: Double,
    val speedWeightedCount: Double,
    val idleWeightedCount: Double,
    val fuelEstimatedLiters: Double,
    val fuelAvoidableLiters: Double,
    val fuelIdleLiters: Double,
    val fuelOverspeedLiters: Double,
    val fuelBrakingLiters: Double,
    val fuelAccelerationLiters: Double,
    val fuelCorneringLiters: Double,
    val fuelPricePerLiter: Double,
    val currency: String,
    val manualStart: Boolean,
    val scoringVersion: Int,
    val routePolyline: String,
    val speedSeries: String,
)

@Entity(
    tableName = "driving_events",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class DrivingEventEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val type: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val peakValue: Double,
    val meanValue: Double,
    val startSpeedKmh: Double,
    val endSpeedKmh: Double,
    val severity: String,
    val penaltyPoints: Double,
    val extraFuelMilliliters: Double,
    val latitude: Double?,
    val longitude: Double?,
    val sortOrder: Int,
)

/** A trip with its events already loaded, used by detail and export. */
data class TripWithEvents(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val events: List<DrivingEventEntity>,
)
