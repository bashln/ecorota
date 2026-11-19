package app.ecorota.core.detection

import app.ecorota.core.model.GeoMath
import app.ecorota.core.model.GeoPoint

/** The fields of a location fix that speed estimation needs. */
data class SpeedFix(
    val timestampMs: Long,
    val point: GeoPoint,
    /** Reported speed in m/s, or null when the provider had none. */
    val reportedSpeedMps: Double?,
    val accuracyMeters: Double,
)

/**
 * Turns a location fix into the speed the trip detector compares against its
 * thresholds.
 *
 * Two providers feed tracking. GPS reports speed and a tight accuracy; the
 * network provider reports a position good to hundreds of meters and a speed of
 * zero, which reads as a parked car. A coarse fix is therefore discarded instead
 * of trusted, and a fix without a speed field is derived from the previous one
 * over a window that matches the interval the stream was requested with.
 */
object SpeedEstimator {

    /** Beyond this accuracy a fix cannot tell a car from a lamp post. */
    const val TRUSTED_ACCURACY_METERS = 100.0

    /**
     * Speed in m/s, or null when this fix cannot tell how fast the vehicle is
     * moving. A null fix must be skipped by the caller: feeding 0.0 would look
     * like a stop and reset the detector's confirmation window.
     */
    fun speedMps(current: SpeedFix, previous: SpeedFix?, windowMs: Long): Double? {
        if (!current.isTrusted()) return null
        current.reportedSpeedMps?.let { return it.coerceAtLeast(0.0) }

        val anchor = previous?.takeIf { it.isTrusted() } ?: return null
        val gapMs = current.timestampMs - anchor.timestampMs
        if (gapMs <= 0L || gapMs > windowMs) return null
        val meters = GeoMath.distanceMeters(anchor.point, current.point)
        return meters / (gapMs / 1_000.0)
    }

    private fun SpeedFix.isTrusted(): Boolean =
        // An absent accuracy is unusual; dropping those fixes would lose speed
        // on devices that do not report one.
        !accuracyMeters.isFinite() || accuracyMeters <= TRUSTED_ACCURACY_METERS
}
