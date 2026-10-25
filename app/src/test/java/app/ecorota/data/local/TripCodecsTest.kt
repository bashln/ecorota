package app.ecorota.data.local

import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.PolylineCodec
import app.ecorota.core.model.SpeedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCodecsTest {

    @Test
    fun `speed series round trip preserves timestamps and speeds`() {
        val samples = listOf(
            SpeedSample(timestampMs = 1_700_000_000_000, speedKmh = 0.0),
            SpeedSample(timestampMs = 1_700_000_005_000, speedKmh = 41.5),
            SpeedSample(timestampMs = 1_700_000_010_000, speedKmh = 87.25),
        )

        val encoded = TripCodecs.encodeSpeedSeries(samples)

        assertEquals(
            "1700000000000:0.0;1700000005000:41.5;1700000010000:87.25",
            encoded,
        )
        assertEquals(samples, TripCodecs.decodeSpeedSeries(encoded))
    }

    @Test
    fun `speed series of empty list encodes to empty string and decodes back to empty`() {
        val encoded = TripCodecs.encodeSpeedSeries(emptyList())

        assertEquals("", encoded)
        assertEquals(emptyList<SpeedSample>(), TripCodecs.decodeSpeedSeries(encoded))
        assertEquals(emptyList<SpeedSample>(), TripCodecs.decodeSpeedSeries(""))
    }

    @Test
    fun `malformed speed series tokens are dropped without failing the rest`() {
        val raw = "100:10.0;nonsense;200:;:30;300:20.0:99; 400 : 30.5 ;500:NaN;600:Infinity"

        val decoded = TripCodecs.decodeSpeedSeries(raw)

        assertEquals(
            listOf(
                SpeedSample(timestampMs = 100, speedKmh = 10.0),
                SpeedSample(timestampMs = 400, speedKmh = 30.5),
            ),
            decoded,
        )
    }

    @Test
    fun `route decodes to empty list when the polyline is empty`() {
        val route = TripCodecs.decodeRoute(
            routePolyline = "",
            speedSeries = "100:10.0",
            startedAtMs = 1_000,
            endedAtMs = 2_000,
        )

        assertTrue(route.isEmpty())
    }

    @Test
    fun `route with matching sample count keeps the original timestamps and speeds`() {
        val points = listOf(
            GeoPoint(-23.55052, -46.63331),
            GeoPoint(-23.55152, -46.63231),
            GeoPoint(-23.55252, -46.63131),
        )
        val speeds = listOf(12.5, 48.0, 72.75)
        val timestamps = listOf(1_700_000_000_000, 1_700_000_004_000, 1_700_000_009_000)

        val route = TripCodecs.decodeRoute(
            routePolyline = PolylineCodec.encode(points),
            speedSeries = TripCodecs.encodeSpeedSeries(
                timestamps.mapIndexed { index, timestamp ->
                    SpeedSample(timestampMs = timestamp, speedKmh = speeds[index])
                },
            ),
            startedAtMs = timestamps.first(),
            endedAtMs = timestamps.last(),
        )

        assertEquals(points, route.map { it.point })
        assertEquals(timestamps, route.map { it.timestampMs })
        assertEquals(speeds, route.map { it.speedKmh })
    }

    @Test
    fun `route with mismatched samples is spread across the trip and keeps usable speeds`() {
        val points = listOf(
            GeoPoint(-23.55052, -46.63331),
            GeoPoint(-23.55152, -46.63231),
            GeoPoint(-23.55252, -46.63131),
            GeoPoint(-23.55352, -46.63031),
            GeoPoint(-23.55452, -46.62931),
        )
        val speedSeries = TripCodecs.encodeSpeedSeries(
            listOf(
                SpeedSample(timestampMs = 5_000, speedKmh = 20.0),
                SpeedSample(timestampMs = 9_000, speedKmh = 80.0),
            ),
        )

        val route = TripCodecs.decodeRoute(
            routePolyline = PolylineCodec.encode(points),
            speedSeries = speedSeries,
            startedAtMs = 1_000,
            endedAtMs = 9_000,
        )

        assertEquals(points, route.map { it.point })
        assertEquals(listOf(1_000L, 3_000L, 5_000L, 7_000L, 9_000L), route.map { it.timestampMs })
        assertEquals(listOf(20.0, 20.0, 20.0, 20.0, 80.0), route.map { it.speedKmh })
    }

    @Test
    fun `single point route is handled without dividing by zero`() {
        val point = GeoPoint(-23.55052, -46.63331)

        val route = TripCodecs.decodeRoute(
            routePolyline = PolylineCodec.encode(listOf(point)),
            speedSeries = "",
            startedAtMs = 4_000,
            endedAtMs = 9_000,
        )

        assertEquals(1, route.size)
        assertEquals(point, route.single().point)
        assertEquals(4_000L, route.single().timestampMs)
        assertEquals(0.0, route.single().speedKmh, 0.0)
    }

    @Test
    fun `route falls back to start time when end precedes start`() {
        val point = GeoPoint(10.0, 20.0)

        val route = TripCodecs.decodeRoute(
            routePolyline = PolylineCodec.encode(listOf(point)),
            speedSeries = "1:5.0;2:90.0",
            startedAtMs = 9_000,
            endedAtMs = 4_000,
        )

        assertEquals(9_000L, route.single().timestampMs)
        assertEquals(5.0, route.single().speedKmh, 0.0)
    }
}
