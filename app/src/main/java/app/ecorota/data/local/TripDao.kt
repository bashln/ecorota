package app.ecorota.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TripDao {

    @Query(SUMMARY_COLUMNS + " GROUP BY t.id ORDER BY t.startedAtMs DESC LIMIT :limit")
    abstract fun observeSummaries(limit: Int): Flow<List<TripListProjection>>

    @Query(SUMMARY_COLUMNS + " WHERE t.startedAtMs >= :sinceMs GROUP BY t.id ORDER BY t.startedAtMs DESC")
    abstract fun observeSummariesSince(sinceMs: Long): Flow<List<TripListProjection>>

    @Query(LIFETIME_STATS)
    abstract fun observeLifetimeStats(): Flow<LifetimeTripStatsProjection>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract suspend fun getTripWithEvents(id: String): TripWithEvents?

    @Transaction
    @Query("SELECT * FROM trips ORDER BY startedAtMs DESC LIMIT 1")
    abstract suspend fun latestTripWithEvents(): TripWithEvents?

    @Transaction
    @Query("SELECT * FROM trips ORDER BY startedAtMs DESC")
    abstract suspend fun allTripsWithEvents(): List<TripWithEvents>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEvents(events: List<DrivingEventEntity>)

    @Query("DELETE FROM driving_events WHERE tripId = :tripId")
    abstract suspend fun deleteEventsForTrip(tripId: String)

    @Query("DELETE FROM trips WHERE id = :id")
    abstract suspend fun deleteTrip(id: String)

    @Query("DELETE FROM trips")
    abstract suspend fun deleteAllTrips()

    @Query("DELETE FROM trips WHERE endedAtMs < :cutoffMs")
    abstract suspend fun deleteTripsEndingBefore(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM trips")
    abstract suspend fun count(): Int

    /** Replaces the whole trip, including its events, in one transaction. */
    @Transaction
    open suspend fun replaceTrip(trip: TripEntity, events: List<DrivingEventEntity>) {
        upsertTrip(trip)
        deleteEventsForTrip(trip.id)
        if (events.isNotEmpty()) insertEvents(events)
    }

    private companion object {
        const val SUMMARY_COLUMNS = """
            SELECT t.id AS id,
                   t.vehicleId AS vehicleId,
                   t.startedAtMs AS startedAtMs,
                   t.endedAtMs AS endedAtMs,
                   t.distanceMeters AS distanceMeters,
                   t.averageSpeedKmh AS averageSpeedKmh,
                   t.maxSpeedKmh AS maxSpeedKmh,
                   t.score AS score,
                   t.manualStart AS manualStart,
                   t.currency AS currency,
                   t.fuelAvoidableLiters AS avoidableLiters,
                   t.fuelAvoidableLiters * t.fuelPricePerLiter AS avoidableCost,
                   COUNT(e.id) AS eventCount
            FROM trips t
            LEFT JOIN driving_events e ON e.tripId = t.id
        """

        const val LIFETIME_STATS = """
            SELECT COUNT(*) AS tripCount,
                   COALESCE(SUM(distanceMeters), 0.0) AS distanceMeters,
                   COALESCE(SUM(endedAtMs - startedAtMs), 0) AS durationMs,
                   COALESCE(SUM(idleDurationMs), 0) AS idleDurationMs,
                   COALESCE(MAX(maxSpeedKmh), 0.0) AS maxSpeedKmh,
                   COALESCE(AVG(score), 0.0) AS averageScore,
                   COALESCE(SUM(accelEventCount), 0) AS accelerationEvents,
                   COALESCE(SUM(brakeEventCount), 0) AS brakingEvents,
                   COALESCE(SUM(cornerEventCount), 0) AS corneringEvents,
                   COALESCE(SUM(speedEventCount), 0) AS overspeedEvents,
                   COALESCE(SUM(idleEventCount), 0) AS idlingEvents,
                   COALESCE((SELECT MAX(score) FROM trips), 0) AS bestScore,
                   COALESCE((SELECT startedAtMs FROM trips ORDER BY score DESC, startedAtMs DESC LIMIT 1), 0) AS bestScoreAtMs,
                   COALESCE((SELECT MAX(distanceMeters) FROM trips), 0.0) AS longestDistanceMeters,
                   COALESCE((SELECT startedAtMs FROM trips ORDER BY distanceMeters DESC, startedAtMs DESC LIMIT 1), 0) AS longestTripAtMs
            FROM trips
        """
    }
}
