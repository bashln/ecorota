package app.ecorota.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineCodecTest {

    @Test
    fun `google's documented example decodes and re-encodes`() {
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

        val points = PolylineCodec.decode(encoded)

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 1e-5)
        assertEquals(-120.2, points[0].longitude, 1e-5)
        assertEquals(40.7, points[1].latitude, 1e-5)
        assertEquals(-120.95, points[1].longitude, 1e-5)
        assertEquals(43.252, points[2].latitude, 1e-5)
        assertEquals(-126.453, points[2].longitude, 1e-5)
        assertEquals(encoded, PolylineCodec.encode(points))
    }

    @Test
    fun `empty input decodes to an empty list and encodes to an empty string`() {
        assertEquals(emptyList<GeoPoint>(), PolylineCodec.decode(""))
        assertEquals("", PolylineCodec.encode(emptyList()))
    }

    @Test
    fun `negative coordinates survive a round trip`() {
        val points = listOf(
            GeoPoint(latitude = -23.55052, longitude = -46.63331),
            GeoPoint(latitude = -22.90685, longitude = -43.17290),
        )

        val decoded = PolylineCodec.decode(PolylineCodec.encode(points))

        assertEquals(2, decoded.size)
        assertEquals(-23.55052, decoded[0].latitude, 1e-9)
        assertEquals(-46.63331, decoded[0].longitude, 1e-9)
        assertEquals(-22.90685, decoded[1].latitude, 1e-9)
        assertEquals(-43.17290, decoded[1].longitude, 1e-9)
    }

    @Test
    fun `precision is five decimal places`() {
        val point = GeoPoint(latitude = 12.34567, longitude = 98.76543)

        val decoded = PolylineCodec.decode(PolylineCodec.encode(listOf(point))).single()

        assertEquals(12.34567, decoded.latitude, 1e-9)
        assertEquals(98.76543, decoded.longitude, 1e-9)
        assertEquals(PolylineCodec.encode(listOf(point)), PolylineCodec.encode(listOf(decoded)))
    }

    @Test
    fun `values below five decimals are rounded to the nearest step`() {
        val decoded = PolylineCodec.decode(
            PolylineCodec.encode(listOf(GeoPoint(latitude = 1.234567, longitude = 2.345678))),
        ).single()

        assertEquals(1.23457, decoded.latitude, 1e-9)
        assertEquals(2.34568, decoded.longitude, 1e-9)
    }

    @Test
    fun `a longer route keeps every vertex distinct`() {
        val points = List(50) { index ->
            GeoPoint(latitude = -23.0 + index * 0.0001, longitude = -46.0 - index * 0.0002)
        }

        val decoded = PolylineCodec.decode(PolylineCodec.encode(points))

        assertEquals(points.size, decoded.size)
        assertTrue(decoded.zip(points).all { (actual, expected) ->
            kotlin.math.abs(actual.latitude - expected.latitude) < 1e-9 &&
                kotlin.math.abs(actual.longitude - expected.longitude) < 1e-9
        })
    }
}
