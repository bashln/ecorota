package app.ecorota.core.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TripDetectorTest {

    private val detector = TripDetector()

    @Test
    fun `defaults describe a fifteen second start and two and a half minute stop window`() {
        val config = AutoTripConfig()
        assertEquals(20.0, config.startSpeedKmh, 0.0)
        assertEquals(15_000L, config.startConfirmMs)
        assertEquals(4.0, config.stopSpeedKmh, 0.0)
        assertEquals(150_000L, config.stopConfirmMs)
        assertEquals(TripDetector.State.PARKED, detector.state)
    }

    @Test
    fun `no start before the confirmation window elapses`() {
        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 0L))
        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 14_999L))
        assertEquals(TripDetector.State.PARKED, detector.state)
    }

    @Test
    fun `start signal fires exactly at the confirmation time`() {
        detector.onFix(speedKmh = 25.0, timestampMs = 0L)

        val signal = detector.onFix(speedKmh = 25.0, timestampMs = 15_000L)

        assertSame(TripDetector.Signal.StartTrip, signal)
        assertEquals(TripDetector.State.DRIVING, detector.state)
    }

    @Test
    fun `speed below the start threshold resets the timer`() {
        detector.onFix(speedKmh = 25.0, timestampMs = 0L)
        assertNull(detector.onFix(speedKmh = 10.0, timestampMs = 5_000L))

        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 6_000L))
        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 20_000L))
        assertSame(
            TripDetector.Signal.StartTrip,
            detector.onFix(speedKmh = 25.0, timestampMs = 21_000L),
        )
    }

    @Test
    fun `stop signal fires after the stop confirmation window`() {
        startTrip()

        assertNull(detector.onFix(speedKmh = 2.0, timestampMs = 20_000L))
        assertNull(detector.onFix(speedKmh = 2.0, timestampMs = 169_999L))
        assertSame(
            TripDetector.Signal.StopTrip,
            detector.onFix(speedKmh = 2.0, timestampMs = 170_000L),
        )
        assertEquals(TripDetector.State.PARKED, detector.state)
    }

    @Test
    fun `speed above the stop threshold resets the stop timer`() {
        startTrip()

        detector.onFix(speedKmh = 2.0, timestampMs = 20_000L)
        assertNull(detector.onFix(speedKmh = 50.0, timestampMs = 30_000L))
        assertNull(detector.onFix(speedKmh = 2.0, timestampMs = 40_000L))
        assertNull(detector.onFix(speedKmh = 2.0, timestampMs = 189_999L))
        assertSame(
            TripDetector.Signal.StopTrip,
            detector.onFix(speedKmh = 2.0, timestampMs = 190_000L),
        )
    }

    @Test
    fun `no duplicate signal without a new confirmation window`() {
        startTrip()

        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 15_001L))
        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 30_000L))
        assertEquals(TripDetector.State.DRIVING, detector.state)
    }

    @Test
    fun `reset returns to a parked state and restarts the window`() {
        startTrip()

        detector.reset()

        assertEquals(TripDetector.State.PARKED, detector.state)
        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 20_000L))
        assertSame(
            TripDetector.Signal.StartTrip,
            detector.onFix(speedKmh = 25.0, timestampMs = 35_000L),
        )
    }

    @Test
    fun `a completed stop allows a later start`() {
        startTrip()
        detector.onFix(speedKmh = 2.0, timestampMs = 20_000L)
        assertEquals(
            TripDetector.Signal.StopTrip,
            detector.onFix(speedKmh = 2.0, timestampMs = 170_000L),
        )

        assertNull(detector.onFix(speedKmh = 25.0, timestampMs = 200_000L))
        assertSame(
            TripDetector.Signal.StartTrip,
            detector.onFix(speedKmh = 25.0, timestampMs = 215_000L),
        )
    }

    private fun startTrip() {
        detector.onFix(speedKmh = 25.0, timestampMs = 0L)
        detector.onFix(speedKmh = 25.0, timestampMs = 15_000L)
    }
}
