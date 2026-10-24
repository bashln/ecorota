package app.ecorota.core.analysis

import app.ecorota.core.model.TripListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class TripTotals(
    val tripCount: Int,
    val distanceMeters: Double,
    val averageScore: Double,
    val avoidableLiters: Double,
    val avoidableCost: Double,
)

data class DailyTripTotals(
    val epochDay: Long,
    val tripCount: Int,
    val distanceMeters: Double,
    val averageScore: Double,
    val avoidableLiters: Double,
    val avoidableCost: Double,
)

/** Aggregations used by the history trend and the free summary header. */
object TripStatistics {

    fun totals(trips: List<TripListItem>): TripTotals {
        if (trips.isEmpty()) return TripTotals(0, 0.0, 0.0, 0.0, 0.0)
        return TripTotals(
            tripCount = trips.size,
            distanceMeters = trips.sumOf { it.distanceMeters },
            averageScore = trips.map { it.score }.average(),
            avoidableLiters = trips.sumOf { it.avoidableLiters },
            avoidableCost = trips.sumOf { it.avoidableCost },
        )
    }

    /**
     * Groups trips by the local calendar day of their start time. The offset is
     * passed in explicitly so the function stays pure and testable.
     */
    fun byLocalDay(trips: List<TripListItem>, offsetMinutes: Int): List<DailyTripTotals> {
        val zone = ZoneOffset.ofTotalSeconds(offsetMinutes * 60)
        return trips
            .groupBy { trip ->
                LocalDate.ofInstant(Instant.ofEpochMilli(trip.startedAtMs), zone).toEpochDay()
            }
            .map { (day, dailyTrips) ->
                DailyTripTotals(
                    epochDay = day,
                    tripCount = dailyTrips.size,
                    distanceMeters = dailyTrips.sumOf { it.distanceMeters },
                    averageScore = dailyTrips.map { it.score }.average(),
                    avoidableLiters = dailyTrips.sumOf { it.avoidableLiters },
                    avoidableCost = dailyTrips.sumOf { it.avoidableCost },
                )
            }
            .sortedBy { it.epochDay }
    }

    fun currentStreakDays(trips: List<TripListItem>, offsetMinutes: Int): Int {
        if (trips.isEmpty()) return 0
        val days = byLocalDay(trips, offsetMinutes).map { it.epochDay }.toSet()
        var day = LocalDate.now(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toEpochDay()
        if (day !in days) day -= 1
        var streak = 0
        while (day in days) {
            streak += 1
            day -= 1
        }
        return streak
    }
}
