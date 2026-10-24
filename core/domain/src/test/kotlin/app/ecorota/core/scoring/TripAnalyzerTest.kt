package app.ecorota.core.scoring

import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.TelemetrySample
import app.ecorota.core.model.TripAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAnalyzerTest {

    private val analyzer = TripAnalyzer()

    @Test
    fun `distance accumulates along a one hertz stream`() {
        var analysis = analyzer.snapshot()
        repeat(10) { index ->
            analysis = analyzer.onSample(
                sample(timestampMs = index * 1_000L, northMeters = index * STEP_METERS, speedMps = 20.0),
            )
        }

        assertEquals(9 * STEP_METERS, analysis.distanceMeters, 0.5)
        assertEquals(10, analysis.route.size)
        assertEquals(72.0, analysis.maxSpeedKmh, 1e-9)
        assertEquals(9_000L, analysis.movingDurationMs)
    }

    @Test
    fun `distance rejects an implausible gps jump`() {
        analyzer.onSample(sample(timestampMs = 0L, northMeters = 0.0, speedMps = 20.0))
        val afterLeg = analyzer.onSample(sample(timestampMs = 1_000L, northMeters = 20.0, speedMps = 20.0))
        assertEquals(20.0, afterLeg.distanceMeters, 0.5)

        val afterJump = analyzer.onSample(
            sample(timestampMs = 2_000L, northMeters = 1_000.0, speedMps = 20.0),
        )
        assertEquals(20.0, afterJump.distanceMeters, 0.5)
    }

    @Test
    fun `distance recovers after a rejected gps jump`() {
        analyzer.onSample(sample(timestampMs = 0L, northMeters = 0.0, speedMps = 20.0))
        analyzer.onSample(sample(timestampMs = 1_000L, northMeters = 20.0, speedMps = 20.0))
        val afterJump = analyzer.onSample(
            sample(timestampMs = 2_000L, northMeters = 1_000.0, speedMps = 20.0),
        )
        assertEquals(20.0, afterJump.distanceMeters, 0.5)

        val recovered = analyzer.onSample(
            sample(timestampMs = 3_000L, northMeters = 40.0, speedMps = 20.0),
        )
        assertEquals(40.0, recovered.distanceMeters, 1.0)
    }

    @Test
    fun `distance ignores fixes less accurate than the limit`() {
        analyzer.onSample(sample(timestampMs = 0L, northMeters = 0.0, speedMps = 20.0))
        val analysis = analyzer.onSample(
            sample(timestampMs = 1_000L, northMeters = 20.0, speedMps = 20.0, accuracyMeters = 120.0),
        )
        assertEquals(0.0, analysis.distanceMeters, 0.0)
    }

    @Test
    fun `a single sensor spike does not create an event`() {
        analyzer.onSample(sample(0L, 0.0, 20.0, accelForwardMps2 = 4.0))
        val analysis = analyzer.onSample(sample(1_000L, 20.0, 20.0, accelForwardMps2 = 0.0))

        assertTrue(analysis.events.none { it.type == DrivingEventType.HARD_ACCELERATION })
    }

    @Test
    fun `hard acceleration is detected from the forward sensor`() {
        analyzer.onSample(sample(0L, 0.0, 20.0, accelForwardMps2 = 3.0))
        analyzer.onSample(sample(1_000L, 20.0, 20.0, accelForwardMps2 = 3.0))
        val analysis = analyzer.onSample(sample(2_000L, 40.0, 20.0, accelForwardMps2 = 0.0))

        val event = analysis.events.single()
        assertEquals(DrivingEventType.HARD_ACCELERATION, event.type)
        assertEquals(0L, event.startedAtMs)
        assertEquals(1_000L, event.endedAtMs)
        assertEquals(1_000L, event.durationMs)
        assertEquals(3.0, event.peakValue, 1e-9)
        assertEquals(3.0, event.meanValue, 1e-9)
        assertEquals(72.0, event.startSpeedKmh, 1e-9)
    }

    @Test
    fun `hard braking is detected from the forward sensor`() {
        analyzer.onSample(sample(0L, 0.0, 20.0, accelForwardMps2 = -4.0))
        analyzer.onSample(sample(1_000L, 20.0, 20.0, accelForwardMps2 = -4.0))
        val analysis = analyzer.onSample(sample(2_000L, 40.0, 20.0, accelForwardMps2 = 0.0))

        val event = analysis.events.single()
        assertEquals(DrivingEventType.HARD_BRAKING, event.type)
        assertEquals(0L, event.startedAtMs)
        assertEquals(1_000L, event.endedAtMs)
        assertEquals(4.0, event.peakValue, 1e-9)
        assertEquals(4.0, event.meanValue, 1e-9)
    }

    @Test
    fun `cornering is detected from the lateral sensor`() {
        analyzer.onSample(sample(0L, 0.0, 15.0, accelLateralMps2 = 3.0))
        analyzer.onSample(sample(1_000L, 15.0, 15.0, accelLateralMps2 = 3.0))
        val analysis = analyzer.onSample(sample(2_000L, 30.0, 15.0, accelLateralMps2 = 0.0))

        val event = analysis.events.single()
        assertEquals(DrivingEventType.SHARP_CORNERING, event.type)
        assertEquals(0L, event.startedAtMs)
        assertEquals(1_000L, event.endedAtMs)
        assertEquals(3.0, event.peakValue, 1e-9)
        assertEquals(54.0, event.startSpeedKmh, 1e-9)
    }

    @Test
    fun `acceleration falls back to gps speed when the sensor is missing`() {
        analyzer.onSample(sample(0L, 0.0, 10.0))
        analyzer.onSample(sample(1_000L, 10.0, 13.0))
        analyzer.onSample(sample(2_000L, 23.0, 16.0))
        val analysis = analyzer.onSample(sample(3_000L, 39.0, 16.0))

        val event = analysis.events.single()
        assertEquals(DrivingEventType.HARD_ACCELERATION, event.type)
        assertEquals(1_000L, event.startedAtMs)
        assertEquals(2_000L, event.endedAtMs)
        assertEquals(3.0, event.peakValue, 1e-9)
    }

    @Test
    fun `overspeed opens above the threshold and clears the hysteresis band`() {
        var analysis = analyzer.snapshot()
        for (second in 0..2) {
            analysis = analyzer.onSample(
                sample(second * 1_000L, second * METERS_PER_SECOND_AT_100_KMH, KMH_100),
            )
        }
        assertEquals(0, analysis.events.size)

        analysis = analyzer.onSample(sample(3_000L, 3 * METERS_PER_SECOND_AT_100_KMH, KMH_100))
        assertEquals(1, analysis.events.size)

        analysis = analyzer.onSample(sample(4_000L, 4 * METERS_PER_SECOND_AT_100_KMH, KMH_88))
        assertEquals(1, analysis.events.size)

        analysis = analyzer.onSample(sample(5_000L, 4 * METERS_PER_SECOND_AT_100_KMH, KMH_85))
        val event = analysis.events.single()
        assertEquals(DrivingEventType.OVERSPEED, event.type)
        assertEquals(0L, event.startedAtMs)
        assertEquals(3_000L, event.endedAtMs)
        assertEquals(3_000L, event.durationMs)
        assertEquals(100.0, event.peakValue, 1e-6)
        assertEquals(100.0, event.meanValue, 1e-6)
    }

    @Test
    fun `overspeed shorter than the minimum duration is discarded`() {
        analyzer.onSample(sample(0L, 0.0, KMH_100))
        analyzer.onSample(sample(1_000L, METERS_PER_SECOND_AT_100_KMH, KMH_100))
        val analysis = analyzer.onSample(sample(2_000L, 2 * METERS_PER_SECOND_AT_100_KMH, 80.0 / 3.6))

        assertTrue(analysis.events.isEmpty())
    }

    @Test
    fun `event cooldown suppresses a duplicate event`() {
        analyzer.onSample(sample(0L, 0.0, 20.0, accelForwardMps2 = 3.0))
        analyzer.onSample(sample(1_000L, 20.0, 20.0, accelForwardMps2 = 3.0))
        analyzer.onSample(sample(2_000L, 40.0, 20.0, accelForwardMps2 = 0.0))
        analyzer.onSample(sample(3_000L, 60.0, 20.0, accelForwardMps2 = 3.0))
        analyzer.onSample(sample(4_000L, 80.0, 20.0, accelForwardMps2 = 3.0))
        analyzer.onSample(sample(5_000L, 100.0, 20.0, accelForwardMps2 = 3.0))
        val analysis = analyzer.onSample(sample(6_000L, 120.0, 20.0, accelForwardMps2 = 0.0))

        val accelerations = analysis.events.filter { it.type == DrivingEventType.HARD_ACCELERATION }
        assertEquals(2, accelerations.size)
        assertEquals(0L, accelerations[0].startedAtMs)
        assertEquals(1_000L, accelerations[0].endedAtMs)
        assertEquals(4_000L, accelerations[1].startedAtMs)
        assertEquals(5_000L, accelerations[1].endedAtMs)
    }

    @Test
    fun `finishAtLastMovement drops the parked confirmation tail`() {
        analyzer.onSample(sample(0L, 0.0, 20.0))
        analyzer.onSample(sample(1_000L, 20.0, 20.0))
        analyzer.onSample(sample(2_000L, 40.0, 20.0))
        analyzer.onSample(sample(3_000L, 40.0, 0.0))
        analyzer.onSample(sample(200_000L, 40.0, 0.0))

        val analysis = analyzer.finishAtLastMovement()

        assertEquals(2_000L, analysis.endedAtMs)
        assertEquals(40.0, analysis.distanceMeters, 1.0)
        assertEquals(2_000L, analysis.movingDurationMs)
        assertEquals(0L, analysis.idleDurationMs)
        assertTrue(analysis.events.none { it.type == DrivingEventType.IDLING })
    }

    @Test
    fun `finish closes an open event at its last update`() {
        analyzer.onSample(sample(0L, 0.0, 20.0, accelForwardMps2 = 3.0))
        analyzer.onSample(sample(1_000L, 20.0, 20.0, accelForwardMps2 = 3.0))

        val analysis = analyzer.finish(9_000L)

        val event = analysis.events.single()
        assertEquals(DrivingEventType.HARD_ACCELERATION, event.type)
        assertEquals(0L, event.startedAtMs)
        assertEquals(1_000L, event.endedAtMs)
        assertEquals(9_000L, analysis.endedAtMs)
    }

    @Test
    fun `stale timestamps do not change aggregates`() {
        analyzer.onSample(sample(0L, 0.0, 20.0))
        val live = analyzer.onSample(sample(5_000L, 100.0, 20.0))
        assertEquals(100.0, live.distanceMeters, 0.5)
        assertEquals(5_000L, live.movingDurationMs)
        assertEquals(2, live.speedSamples.size)

        val stale = analyzer.onSample(sample(3_000L, 900.0, 20.0))

        assertEquals(100.0, stale.distanceMeters, 0.5)
        assertEquals(5_000L, stale.movingDurationMs)
        assertEquals(2, stale.speedSamples.size)
        assertEquals(72.0, stale.maxSpeedKmh, 1e-9)
        assertEquals(2, stale.route.size)
    }

    @Test
    fun `speed samples are thinned to the configured interval`() {
        var analysis: TripAnalysis = analyzer.snapshot()
        for (second in 0..11) {
            analysis = analyzer.onSample(
                sample(second * 1_000L, second * STEP_METERS, 20.0),
            )
        }

        assertEquals(3, analysis.speedSamples.size)
        assertEquals(listOf(0L, 5_000L, 10_000L), analysis.speedSamples.map { it.timestampMs })
        assertTrue(analysis.speedSamples.all { kotlin.math.abs(it.speedKmh - 72.0) < 1e-9 })
    }

    @Test
    fun `idle duration grows while moving duration does not`() {
        for (second in 0..4) {
            analyzer.onSample(sample(second * 1_000L, 0.0, 0.0))
        }
        val idle = analyzer.snapshot()
        assertEquals(4_000L, idle.idleDurationMs)
        assertEquals(0L, idle.movingDurationMs)

        for (second in 5..9) {
            analyzer.onSample(sample(second * 1_000L, second * 10.0, 10.0))
        }
        val moving = analyzer.snapshot()
        assertEquals(5_000L, moving.movingDurationMs)
        assertEquals(4_000L, moving.idleDurationMs)
    }

    @Test
    fun `idle shorter than the minimum produces no event`() {
        for (second in 0..43) {
            analyzer.onSample(sample(second * 1_000L, 0.0, 0.0))
        }

        val analysis = analyzer.finish(43_000L)

        assertTrue(analysis.events.isEmpty())
    }

    @Test
    fun `idle event opens after the minimum and excludes the grace period`() {
        for (second in 0..44) {
            analyzer.onSample(sample(second * 1_000L, 0.0, 0.0))
        }

        val analysis = analyzer.onSample(sample(45_000L, 0.0, 10.0))

        val event = analysis.events.single()
        assertEquals(DrivingEventType.IDLING, event.type)
        assertEquals(15_000L, event.startedAtMs)
        assertEquals(45_000L, event.endedAtMs)
        assertEquals(30_000L, event.durationMs)
        assertEquals(30.0, event.peakValue, 1e-9)
    }

    @Test
    fun `finish closes an open idle event`() {
        for (second in 0..59) {
            analyzer.onSample(sample(second * 1_000L, 0.0, 0.0))
        }

        val analysis = analyzer.finish(60_000L)

        val event = analysis.events.single()
        assertEquals(DrivingEventType.IDLING, event.type)
        assertEquals(15_000L, event.startedAtMs)
        assertEquals(60_000L, event.endedAtMs)
        assertEquals(45_000L, event.durationMs)
    }

    private fun sample(
        timestampMs: Long,
        northMeters: Double = 0.0,
        speedMps: Double = 0.0,
        accuracyMeters: Double = 5.0,
        accelForwardMps2: Double? = null,
        accelLateralMps2: Double? = null,
    ): TelemetrySample = TelemetrySample(
        timestampMs = timestampMs,
        point = GeoPoint(northMeters / METERS_PER_DEGREE_LAT, 0.0),
        speedMps = speedMps,
        bearingDeg = null,
        accuracyMeters = accuracyMeters,
        accelForwardMps2 = accelForwardMps2,
        accelLateralMps2 = accelLateralMps2,
    )

    private companion object {
        const val METERS_PER_DEGREE_LAT = 111_195.0802335329
        const val STEP_METERS = 20.0
        const val METERS_PER_SECOND_AT_100_KMH = 100.0 / 3.6
        const val KMH_100 = 100.0 / 3.6
        const val KMH_88 = 88.0 / 3.6
        const val KMH_85 = 85.0 / 3.6
    }
}
