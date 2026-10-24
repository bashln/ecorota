package app.ecorota.core.model

data class TripRecord(
    val id: String,
    val vehicleId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceMeters: Double,
    val movingDurationMs: Long,
    val idleDurationMs: Long,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val score: Int,
    val breakdown: ScoreBreakdown,
    val fuel: FuelEstimate,
    val events: List<DrivingEvent>,
    val route: List<RoutePoint>,
    val manualStart: Boolean,
    val scoringVersion: Int,
    val createdAtMs: Long,
    /** Downsampled speed series for the premium chart; may be empty for old trips. */
    val speedSamples: List<SpeedSample> = emptyList(),
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)
    val eventCount: Int get() = events.size
}

/** Row used by the history list; it deliberately excludes route and events. */
data class TripListItem(
    val id: String,
    val vehicleId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val score: Int,
    val eventCount: Int,
    val avoidableLiters: Double,
    val avoidableCost: Double,
    val currency: String,
    val manualStart: Boolean,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)
}

object ScoringVersion {
    const val CURRENT = 1
}
