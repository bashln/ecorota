package app.ecorota.core.detection

import app.ecorota.core.model.GeoMath
import app.ecorota.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedEstimatorTest {

    private val windowMs = 90_000L

    @Test
    fun `a reported speed is used as it comes`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 0L, speedMps = 12.5, accuracyMeters = 5.0),
            previous = null,
            windowMs = windowMs,
        )

        assertEquals(12.5, speed!!, 1e-9)
    }

    @Test
    fun `a negative reported speed reads as standing still`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 0L, speedMps = -1.0, accuracyMeters = 5.0),
            previous = null,
            windowMs = windowMs,
        )

        assertEquals(0.0, speed!!, 1e-9)
    }

    @Test
    fun `a coarse fix reports no speed even when the provider claims one`() {
        // The network provider answers 0 m/s while the car is moving, which
        // would keep the detector asleep for the whole drive.
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 0L, speedMps = 0.0, accuracyMeters = 800.0),
            previous = null,
            windowMs = windowMs,
        )

        assertNull(speed)
    }

    @Test
    fun `a fix without a speed field is derived from the previous fix`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 30_000L, speedMps = null, accuracyMeters = 5.0, metersEast = 300.0),
            previous = fix(atMs = 0L, speedMps = null, accuracyMeters = 5.0),
            windowMs = windowMs,
        )

        assertEquals(10.0, speed!!, 0.05)
    }

    @Test
    fun `the derived window covers the monitor interval`() {
        // Regression: the window used to be 10 s while monitoring asks for a
        // fix every 30 s, so every monitor fix without a speed read as 0 km/h.
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 30_000L, speedMps = null, accuracyMeters = 5.0, metersEast = 600.0),
            previous = fix(atMs = 0L, speedMps = null, accuracyMeters = 5.0),
            windowMs = windowMs,
        )

        assertEquals(20.0, speed!!, 0.05)
    }

    @Test
    fun `a gap longer than the window cannot be derived`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 100_000L, speedMps = null, accuracyMeters = 5.0, metersEast = 900.0),
            previous = fix(atMs = 0L, speedMps = null, accuracyMeters = 5.0),
            windowMs = windowMs,
        )

        assertNull(speed)
    }

    @Test
    fun `the first fix of a trip has no speed to derive from`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 0L, speedMps = null, accuracyMeters = 5.0),
            previous = null,
            windowMs = windowMs,
        )

        assertNull(speed)
    }

    @Test
    fun `an unknown accuracy still allows the reported speed`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 0L, speedMps = 11.0, accuracyMeters = Double.NaN),
            previous = null,
            windowMs = windowMs,
        )

        assertEquals(11.0, speed!!, 1e-9)
    }

    @Test
    fun `a fix at the accuracy boundary is trusted`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 0L, speedMps = 8.0, accuracyMeters = 100.0),
            previous = null,
            windowMs = windowMs,
        )

        assertEquals(8.0, speed!!, 1e-9)
    }

    @Test
    fun `a clock regression yields no derived speed`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 0L, speedMps = null, accuracyMeters = 5.0, metersEast = 300.0),
            previous = fix(atMs = 30_000L, speedMps = null, accuracyMeters = 5.0),
            windowMs = windowMs,
        )

        assertNull(speed)
    }

    @Test
    fun `a coarse previous fix cannot anchor a derived speed`() {
        val speed = SpeedEstimator.speedMps(
            current = fix(atMs = 30_000L, speedMps = null, accuracyMeters = 5.0, metersEast = 300.0),
            previous = fix(atMs = 0L, speedMps = null, accuracyMeters = 800.0),
            windowMs = windowMs,
        )

        assertNull(speed)
    }

    private fun fix(
        atMs: Long,
        speedMps: Double?,
        accuracyMeters: Double,
        metersEast: Double = 0.0,
    ) = SpeedFix(
        timestampMs = atMs,
        point = GeoPoint(
            latitude = 0.0,
            longitude = metersEast / metersPerDegree(),
        ),
        reportedSpeedMps = speedMps,
        accuracyMeters = accuracyMeters,
    )

    private companion object {
        // Derive from the same geodesic the estimator uses, so the test does
        // not encode a different Earth radius than production code.
        fun metersPerDegree(): Double =
            GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
    }
}
