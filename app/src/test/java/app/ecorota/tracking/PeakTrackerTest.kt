package app.ecorota.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakTrackerTest {

    @Test
    fun `consume reports the signed extreme of each axis`() {
        val tracker = PeakTracker()
        tracker.record(AxisAcceleration(forwardMps2 = 2.0, lateralMps2 = 1.0))
        tracker.record(AxisAcceleration(forwardMps2 = -3.5, lateralMps2 = 4.0))
        tracker.record(AxisAcceleration(forwardMps2 = 1.0, lateralMps2 = 0.5))

        val peaks = tracker.consume()

        assertEquals(-3.5, peaks.forwardMps2!!, 0.0)
        assertEquals(4.0, peaks.lateralMps2!!, 0.0)
    }

    @Test
    fun `consume drains the store`() {
        val tracker = PeakTracker()
        tracker.record(AxisAcceleration(forwardMps2 = 2.0, lateralMps2 = 3.0))
        tracker.consume()

        val peaks = tracker.consume()

        assertNull(peaks.forwardMps2)
        assertNull(peaks.lateralMps2)
    }

    @Test
    fun `reset drops pending peaks`() {
        val tracker = PeakTracker()
        tracker.record(AxisAcceleration(forwardMps2 = 2.0, lateralMps2 = 3.0))
        tracker.reset()

        assertNull(tracker.consume().forwardMps2)
    }
}

class RotationGateTest {

    @Test
    fun `fast rotation suppresses samples for the hold-off window`() {
        val gate = RotationGate()
        gate.onRotation(rateRadPerSec = 3.0, timestampNanos = 1_000_000_000L)

        assertFalse(gate.accepts(1_100_000_000L))
        assertTrue(gate.accepts(1_600_000_000L))
    }

    @Test
    fun `gentle rotation leaves samples alone`() {
        val gate = RotationGate()
        gate.onRotation(rateRadPerSec = 0.3, timestampNanos = 1_000_000_000L)

        assertTrue(gate.accepts(1_100_000_000L))
    }
}
