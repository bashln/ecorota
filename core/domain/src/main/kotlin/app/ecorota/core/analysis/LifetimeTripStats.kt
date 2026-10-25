package app.ecorota.core.analysis

/**
 * Totals over every stored trip. Deliberately excludes fuel and cost: trips can
 * have different currencies, so summing money would be meaningless.
 */
data class LifetimeTripStats(
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
    val bestScore: Int?,
    val bestScoreAtMs: Long?,
    val longestDistanceMeters: Double,
    val longestTripAtMs: Long?,
) {
    val averageTripDurationMs: Long
        get() = if (tripCount > 0) durationMs / tripCount else 0L

    val hasTrips: Boolean get() = tripCount > 0
}
