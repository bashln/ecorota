package app.ecorota.data

import app.ecorota.core.model.TripListItem
import app.ecorota.core.model.TripRecord
import kotlinx.coroutines.flow.Flow

/**
 * Trip persistence boundary. Implementations live in the data layer; the UI
 * and the tracking service only see these methods.
 */
interface TripRepository {
    fun observeTrips(limit: Int = 500): Flow<List<TripListItem>>

    fun observeTripsSince(sinceMs: Long): Flow<List<TripListItem>>

    suspend fun getTrip(id: String): TripRecord?

    suspend fun latestTrip(): TripRecord?

    suspend fun saveTrip(trip: TripRecord)

    suspend fun deleteTrip(id: String)

    suspend fun deleteAll()

    suspend fun getAllTrips(): List<TripRecord>

    /** Deletes trips that ended before [cutoffMs]. Returns the number removed. */
    suspend fun deleteTripsEndingBefore(cutoffMs: Long): Int
}
