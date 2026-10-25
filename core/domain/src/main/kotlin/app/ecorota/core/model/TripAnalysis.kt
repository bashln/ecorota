package app.ecorota.core.model

/**
 * Snapshot of a trip in progress or just finished, produced by the analyzer.
 * It contains everything the scorer needs, and nothing Android-specific.
 */
data class TripAnalysis(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceMeters: Double,
    val movingDurationMs: Long,
    val idleDurationMs: Long,
    val maxSpeedKmh: Double,
    val events: List<DetectedEvent>,
    val route: List<RoutePoint>,
    val speedSamples: List<SpeedSample>,
    val lastSpeedKmh: Double,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)

    /** Average speed while the vehicle was moving. */
    val averageSpeedKmh: Double
        get() = if (movingDurationMs > 0) {
            distanceMeters / 1000.0 / (movingDurationMs / 3_600_000.0)
        } else {
            0.0
        }

    val distanceKm: Double get() = distanceMeters / 1000.0

    val overspeedDurationMs: Long
        get() = events.filter { it.type == DrivingEventType.OVERSPEED }.sumOf { it.durationMs }

    val avoidableIdleMs: Long
        get() = events.filter { it.type == DrivingEventType.IDLING }.sumOf { it.durationMs }
}
