package app.ecorota.core.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionGateTest {

    private val threshold = 0.45
    private val moving = 1.0
    private val still = 0.0

    private fun testConfig() = MotionGateConfig(
        motionThresholdMps2 = threshold,
        confirmSamples = 3,
        quietSamples = 4,
        cooldownMs = 8,
        maxSampleGapMs = 2,
    )

    private fun gate() = MotionGate(testConfig())

    private fun movingSample(index: Int, magnitude: Double = moving) =
        MotionSample(timestampNanos = index * 1_000_000L, magnitudeMps2 = magnitude)

    @Test
    fun `demand stays off until the confirm count is reached`() {
        val gate = gate()

        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(0)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(1)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(2)))
    }

    @Test
    fun `an exact threshold sample counts as motion`() {
        val gate = gate()

        gate.onSample(movingSample(0, magnitude = threshold))
        gate.onSample(movingSample(1, magnitude = threshold))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(2, magnitude = threshold)))
        assertEquals(LocationDemand.ON, gate.onSample(movingSample(3, magnitude = threshold)))
    }

    @Test
    fun `still samples reset an incomplete confirmation every time`() {
        val gate = gate()
        var index = 0

        repeat(5) {
            assertEquals(LocationDemand.OFF, gate.onSample(movingSample(index++, magnitude = moving)))
            assertEquals(LocationDemand.OFF, gate.onSample(movingSample(index++, magnitude = moving)))
            assertEquals(LocationDemand.OFF, gate.onSample(movingSample(index++, magnitude = still)))
        }

        assertEquals(LocationDemand.OFF, gate.demand())
    }

    @Test
    fun `an above threshold sample in suspect enters active and emits on`() {
        val gate = gate()

        gate.onSample(movingSample(0))
        gate.onSample(movingSample(1))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(2)))
        assertEquals(LocationDemand.ON, gate.onSample(movingSample(3)))
    }

    @Test
    fun `a below threshold sample in suspect resets to still`() {
        val gate = gate()

        gate.onSample(movingSample(0))
        gate.onSample(movingSample(1))
        gate.onSample(movingSample(2))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(3, magnitude = still)))

        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(4)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(5)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(6)))
        assertEquals(LocationDemand.ON, gate.onSample(movingSample(7)))
    }

    @Test
    fun `quiet within cooldown keeps the gate on`() {
        val gate = activate()

        for (i in 4..7) {
            assertEquals(LocationDemand.ON, gate.onSample(movingSample(i, magnitude = still)))
        }
    }

    @Test
    fun `quiet after cooldown emits off`() {
        val gate = activate()

        for (i in 4..10) {
            assertEquals(LocationDemand.ON, gate.onSample(movingSample(i, magnitude = still)))
        }
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(11, magnitude = still)))
    }

    @Test
    fun `an above threshold sample in active resets the quiet count`() {
        val config = MotionGateConfig(
            motionThresholdMps2 = threshold,
            confirmSamples = 3,
            quietSamples = 4,
            cooldownMs = 1,
            maxSampleGapMs = 2,
        )
        val gate = MotionGate(config)
        repeat(4) { gate.onSample(movingSample(it)) }

        gate.onSample(movingSample(4, magnitude = still))
        gate.onSample(movingSample(5, magnitude = still))
        assertEquals(LocationDemand.ON, gate.onSample(movingSample(6, magnitude = moving)))

        // Quiet count restarted at 6. Three quiet samples are not enough, so the
        // gate stays ON at index 9 and only turns OFF on the fourth at index 10.
        for (i in 7..9) {
            assertEquals(LocationDemand.ON, gate.onSample(movingSample(i, magnitude = still)))
        }
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(10, magnitude = still)))
    }

    @Test
    fun `a stale sample gap forces off`() {
        val gate = activate()

        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(100, magnitude = still)))
        assertEquals(LocationDemand.OFF, gate.demand())
    }

    @Test
    fun `non finite and negative magnitudes are rejected as no evidence`() {
        val gate = gate()

        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(0, magnitude = Double.NaN)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(1, magnitude = Double.POSITIVE_INFINITY)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(2, magnitude = Double.NEGATIVE_INFINITY)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(3, magnitude = -1.0)))
    }

    @Test
    fun `reset returns off and restarts the confirmation`() {
        val gate = activate()
        assertEquals(LocationDemand.ON, gate.demand())

        gate.reset()

        assertEquals(LocationDemand.OFF, gate.demand())
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(100)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(101)))
        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(102)))
        assertEquals(LocationDemand.ON, gate.onSample(movingSample(103)))
    }

    @Test
    fun `demand agrees with the last onSample result`() {
        val gate = gate()

        assertEquals(LocationDemand.OFF, gate.onSample(movingSample(0)))
        assertEquals(LocationDemand.OFF, gate.demand())

        gate.onSample(movingSample(1))
        gate.onSample(movingSample(2))
        assertEquals(LocationDemand.ON, gate.onSample(movingSample(3)))
        assertEquals(LocationDemand.ON, gate.demand())
    }

    private fun activate(): MotionGate {
        val gate = gate()
        gate.onSample(movingSample(0))
        gate.onSample(movingSample(1))
        gate.onSample(movingSample(2))
        gate.onSample(movingSample(3))
        return gate
    }
}
