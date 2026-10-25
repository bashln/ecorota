package app.ecorota.data.repository

import app.ecorota.core.analysis.LifetimeTripStats
import app.ecorota.core.model.TripListItem
import app.ecorota.core.model.TripRecord
import app.ecorota.data.TripRepository
import app.ecorota.data.local.EcoRotaDatabase
import app.ecorota.data.local.TripMappers
import app.ecorota.data.local.TripWithEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTripRepository(database: EcoRotaDatabase) : TripRepository {

    private val tripDao = database.tripDao()

    override fun observeTrips(limit: Int): Flow<List<TripListItem>> =
        // A negative LIMIT means "unbounded" in SQLite; clamp so a bad caller cannot load every trip.
        tripDao.observeSummaries(limit.coerceAtLeast(1))
            .map { rows -> rows.map { it.toTripListItem() } }

    override fun observeTripsSince(sinceMs: Long): Flow<List<TripListItem>> =
        tripDao.observeSummariesSince(sinceMs)
            .map { rows -> rows.map { it.toTripListItem() } }

    override fun observeLifetimeStats(): Flow<LifetimeTripStats> =
        tripDao.observeLifetimeStats()
            .map { row -> row.toLifetimeStats() }

    override suspend fun getTrip(id: String): TripRecord? =
        tripDao.getTripWithEvents(id)?.toRecord()

    override suspend fun latestTrip(): TripRecord? =
        tripDao.latestTripWithEvents()?.toRecord()

    override suspend fun saveTrip(trip: TripRecord) {
        tripDao.replaceTrip(
            trip = TripMappers.toEntity(trip),
            events = TripMappers.toEventEntities(trip.id, trip.events),
        )
    }

    override suspend fun deleteTrip(id: String) {
        tripDao.deleteTrip(id)
    }

    override suspend fun deleteAll() {
        tripDao.deleteAllTrips()
    }

    override suspend fun getAllTrips(): List<TripRecord> =
        tripDao.allTripsWithEvents().map { it.toRecord() }

    override suspend fun deleteTripsEndingBefore(cutoffMs: Long): Int =
        tripDao.deleteTripsEndingBefore(cutoffMs)

    private fun TripWithEvents.toRecord(): TripRecord = TripMappers.toRecord(trip, events)
}
