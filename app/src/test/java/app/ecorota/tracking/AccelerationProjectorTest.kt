package app.ecorota.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AccelerationProjectorTest {

    @Test
    fun `upright mount reports no acceleration at rest`() {
        val projector = AccelerationProjector()
        settle(projector, 0f, GRAVITY, 0f)

        val sample = projector.project(0f, GRAVITY, 0f)

        assertNotNull(sample)
        assertEquals(0.0, sample!!.forwardMps2, 0.05)
        assertEquals(0.0, sample.lateralMps2, 0.05)
    }

    @Test
    fun `upright mount keeps forward acceleration positive`() {
        val projector = AccelerationProjector()
        settle(projector, 0f, GRAVITY, 0f)

        val sample = projector.project(0f, GRAVITY, -2f)

        assertNotNull(sample)
        assertEquals(1.9, sample!!.forwardMps2, 0.2)
        assertEquals(0.0, sample.lateralMps2, 0.2)
    }

    @Test
    fun `upright mount keeps braking negative`() {
        val projector = AccelerationProjector()
        settle(projector, 0f, GRAVITY, 0f)

        val sample = projector.project(0f, GRAVITY, 2f)

        assertNotNull(sample)
        assertEquals(-1.9, sample!!.forwardMps2, 0.2)
    }

    @Test
    fun `cornering lands on the lateral axis`() {
        val projector = AccelerationProjector()
        settle(projector, 0f, GRAVITY, 0f)

        val sample = projector.project(2f, GRAVITY, 0f)

        assertNotNull(sample)
        assertEquals(0.0, sample!!.forwardMps2, 0.2)
        assertEquals(1.9, sample.lateralMps2, 0.2)
    }

    @Test
    fun `flat mount falls back to the phone top as forward`() {
        val projector = AccelerationProjector()
        settle(projector, 0f, 0f, GRAVITY)

        val sample = projector.project(0f, 2f, GRAVITY)

        assertNotNull(sample)
        assertEquals(1.9, sample!!.forwardMps2, 0.2)
        assertEquals(0.0, sample.lateralMps2, 0.2)
    }

    @Test
    fun `a free-falling phone yields no orientation`() {
        val projector = AccelerationProjector()
        projector.project(0f, 0f, 0f)

        assertNull(projector.project(0f, 0f, 0f))
    }

    @Test
    fun `reset forgets the orientation`() {
        val projector = AccelerationProjector()
        settle(projector, 0f, GRAVITY, 0f)
        projector.reset()

        assertNull(projector.project(0f, GRAVITY, 0f))
    }

    private fun settle(projector: AccelerationProjector, x: Float, y: Float, z: Float) {
        repeat(60) { projector.project(x, y, z) }
    }

    private companion object {
        const val GRAVITY = 9.81f
    }
}
