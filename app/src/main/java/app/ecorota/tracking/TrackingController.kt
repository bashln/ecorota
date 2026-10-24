package app.ecorota.tracking

import app.ecorota.core.model.FuelEstimate
import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.ScoreBreakdown
import kotlinx.coroutines.flow.StateFlow

data class LiveTripState(
    val recording: Boolean = false,
    val automatic: Boolean = false,
    val startedAtMs: Long? = null,
    val distanceMeters: Double = 0.0,
    val durationMs: Long = 0,
    val currentSpeedKmh: Double = 0.0,
    val score: Int? = null,
    val eventCount: Int = 0,
    val fuel: FuelEstimate? = null,
    val breakdown: ScoreBreakdown? = null,
    val lastPoint: GeoPoint? = null,
) {
    val averageSpeedKmh: Double
        get() = if (durationMs > 0) {
            distanceMeters / 1000.0 / (durationMs / 3_600_000.0)
        } else {
            0.0
        }
}

/**
 * Single entry point for starting and stopping trips. The UI never touches the
 * foreground service directly, which keeps permission and notification logic
 * in one place.
 */
interface TrackingController {
    val state: StateFlow<LiveTripState>

    /** True while the low-power auto-detection service is listening. */
    val monitoring: StateFlow<Boolean>

    /** Returns false when location permission is missing. */
    suspend fun startRecording(manual: Boolean = true): Boolean

    suspend fun stopRecording(): Boolean

    suspend fun setAutoDetectionEnabled(enabled: Boolean)

    /** Re-applies the monitoring preference, e.g. after a permission change. */
    suspend fun refresh()
}
