package app.ecorota.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    private val saoPaulo = GeoPoint(-23.5505, -46.6333)
    private val rioDeJaneiro = GeoPoint(-22.9068, -43.1729)

    @Test
    fun `haversine distance matches the known sao paulo to rio distance`() {
        assertEquals(360_749.32, GeoMath.distanceMeters(saoPaulo, rioDeJaneiro), 500.0)
        assertEquals(360_749.32, GeoMath.distanceMeters(rioDeJaneiro, saoPaulo), 500.0)
    }

    @Test
    fun `distance to itself is zero`() {
        assertEquals(0.0, GeoMath.distanceMeters(saoPaulo, saoPaulo), 0.0)
    }

    @Test
    fun `one degree of latitude is about 111 kilometers`() {
        assertEquals(111_195.08, GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 1.0)
    }

    @Test
    fun `bearings follow the compass quadrants`() {
        val origin = GeoPoint(0.0, 0.0)
        assertEquals(0.0, GeoMath.bearingDegrees(origin, GeoPoint(1.0, 0.0)), 1e-9)
        assertEquals(90.0, GeoMath.bearingDegrees(origin, GeoPoint(0.0, 1.0)), 1e-9)
        assertEquals(180.0, GeoMath.bearingDegrees(origin, GeoPoint(-1.0, 0.0)), 1e-9)
        assertEquals(270.0, GeoMath.bearingDegrees(origin, GeoPoint(0.0, -1.0)), 1e-9)
    }

    @Test
    fun `bearing to rio is east north east`() {
        assertEquals(79.2424, GeoMath.bearingDegrees(saoPaulo, rioDeJaneiro), 0.01)
    }

    @Test
    fun `bearing delta wraps around north`() {
        assertEquals(20.0, GeoMath.bearingDeltaDegrees(350.0, 10.0), 1e-9)
        assertEquals(20.0, GeoMath.bearingDeltaDegrees(10.0, 350.0), 1e-9)
        assertEquals(0.1, GeoMath.bearingDeltaDegrees(0.0, 359.9), 1e-9)
        assertEquals(180.0, GeoMath.bearingDeltaDegrees(0.0, 180.0), 1e-9)
        assertEquals(0.0, GeoMath.bearingDeltaDegrees(42.0, 42.0), 1e-9)
    }

    @Test
    fun `bearing is always in the zero to three sixty range`() {
        val points = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(10.0, 10.0),
            GeoPoint(-10.0, -10.0),
            GeoPoint(0.0, 179.0),
            GeoPoint(0.0, -179.0),
        )
        for (from in points) {
            for (to in points) {
                val bearing = GeoMath.bearingDegrees(from, to)
                assertTrue("bearing $bearing out of range", bearing >= 0.0 && bearing < 360.0)
            }
        }
    }
}
