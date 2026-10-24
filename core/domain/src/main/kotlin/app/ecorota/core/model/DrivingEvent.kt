package app.ecorota.core.model

enum class DrivingEventType {
    HARD_ACCELERATION,
    HARD_BRAKING,
    SHARP_CORNERING,
    OVERSPEED,
    IDLING,
}

enum class EventSeverity {
    MILD,
    MODERATE,
    SEVERE,
}

/**
 * A raw event produced by [app.ecorota.core.scoring.TripAnalyzer] before scoring.
 *
 * Units depend on [type]:
 * - HARD_ACCELERATION, HARD_BRAKING, SHARP_CORNERING: m/s²
 * - OVERSPEED: km/h
 * - IDLING: seconds of avoidable idling
 */
data class DetectedEvent(
    val type: DrivingEventType,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val peakValue: Double,
    val meanValue: Double,
    val startSpeedKmh: Double,
    val endSpeedKmh: Double,
    val location: GeoPoint?,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)
}

/** A detected event after scoring, ready to be shown and persisted. */
data class DrivingEvent(
    val id: String,
    val type: DrivingEventType,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val peakValue: Double,
    val meanValue: Double,
    val startSpeedKmh: Double,
    val endSpeedKmh: Double,
    val severity: EventSeverity,
    /** Informational cost of this event, in score points (severity weighted). */
    val penaltyPoints: Double,
    val extraFuelMilliliters: Double,
    val location: GeoPoint?,
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0)
}
