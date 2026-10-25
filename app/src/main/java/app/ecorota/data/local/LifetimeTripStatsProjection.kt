package app.ecorota.data.local

import app.ecorota.core.analysis.LifetimeTripStats

/**
 * Flat result of the all-time aggregate. Room cannot map the raw row straight
 * into [LifetimeTripStats] because the query coalesces the "no trips" timestamps
 * to zero, so this projection keeps the raw values and the mapper decides which
 * zeroes mean "absent".
 */
data class LifetimeTripStatsProjection(
    val tripCount: Int,
    val distanceMeters: Double,
    val durationMs: Long,
    val idleDurationMs: Long,
    val maxSpeedKmh: Double,
    val averageScore: Double,
    val accelerationEvents: Int,
    val brakingEvents: Int,
    val corneringEvents: Int,
    val overspeedEvents: Int,
    val idlingEvents: Int,
    val bestScore: Int,
    val bestScoreAtMs: Long,
    val longestDistanceMeters: Double,
    val longestTripAtMs: Long,
) {
    fun toLifetimeStats(): LifetimeTripStats {
        val hasTrips = tripCount > 0
        return LifetimeTripStats(
            tripCount = tripCount,
            distanceMeters = distanceMeters,
            durationMs = durationMs,
            idleDurationMs = idleDurationMs,
            maxSpeedKmh = maxSpeedKmh,
            averageScore = averageScore,
            accelerationEvents = accelerationEvents,
            brakingEvents = brakingEvents,
            corneringEvents = corneringEvents,
            overspeedEvents = overspeedEvents,
            idlingEvents = idlingEvents,
            // A score of 0 is legitimate, so only the empty database maps to null.
            bestScore = if (hasTrips) bestScore else null,
            bestScoreAtMs = if (hasTrips) bestScoreAtMs else null,
            longestDistanceMeters = longestDistanceMeters,
            longestTripAtMs = if (hasTrips) longestTripAtMs else null,
        )
    }
}
