package app.ecorota.export

import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.EventSeverity
import app.ecorota.core.model.FuelEstimate
import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.RoutePoint
import app.ecorota.core.model.ScoreBreakdown
import app.ecorota.core.model.ScoringVersion
import app.ecorota.core.model.TripRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Document

class DefaultTripExporterTest {

    private val exporter = DefaultTripExporter()
    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    @Before
    fun fixTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `csv starts with the documented header`() {
        val rows = csvRows(exporter, listOf(trip()))

        assertEquals(EXPECTED_HEADER, rows.first())
        assertEquals(23, rows.first().split(",").size)
    }

    @Test
    fun `csv quotes values containing a comma or a quote`() {
        val rows = csvRows(
            exporter,
            listOf(trip(id = "trip,1", vehicleId = "van \"A\"")),
        )

        val row = rows[1]
        assertTrue(row, row.startsWith("\"trip,1\",\"van \"\"A\"\"\","))
        assertEquals(23, row.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")).size)
    }

    @Test
    fun `csv writes one row per event`() {
        val events = listOf(event("e1", DrivingEventType.HARD_BRAKING), event("e2", DrivingEventType.IDLING))
        val rows = csvRows(exporter, listOf(trip(events = events)))

        assertEquals(3, rows.size)
        assertTrue(rows[1], rows[1].contains(",HARD_BRAKING,"))
        assertTrue(rows[2], rows[2].contains(",IDLING,"))
    }

    @Test
    fun `csv writes one row with empty event columns when there are no events`() {
        val rows = csvRows(exporter, listOf(trip()))

        assertEquals(2, rows.size)
        val fields = rows[1].split(",")
        assertEquals(23, fields.size)
        assertEquals("BRL", fields[16])
        assertTrue(fields.subList(17, 23).all { it.isEmpty() })
        assertTrue(rows[1], rows[1].endsWith("BRL,,,,,,"))
    }

    @Test
    fun `csv formats numbers with three decimals and iso offsets`() {
        val rows = csvRows(exporter, listOf(trip()))
        val fields = rows[1].split(",")

        assertEquals("1.234", fields[4])
        assertEquals("600.000", fields[5])
        assertEquals("500.000", fields[6])
        assertEquals("100.000", fields[7])
        assertEquals("0.041", fields[14])
        assertEquals("0.246", fields[15])
        // 2024-01-01T10:00:00Z in UTC-3.
        assertEquals("2024-01-01T07:00:00-03:00", fields[2])
        assertEquals("2024-01-01T07:10:00-03:00", fields[3])
    }

    @Test
    fun `csv uses the documented file name pattern`() {
        assertEquals("ecorota-trips-20240101-0700.csv", exporter.csvFileName(START_MS))
        assertEquals("ecorota-trip-20240101-0700.gpx", exporter.gpxFileName(trip()))
    }

    @Test
    fun `gpx is well formed with one track point per route point`() {
        val route = listOf(
            RoutePoint(START_MS, GeoPoint(-23.5505, -46.6333), 36.0),
            RoutePoint(START_MS + 1_000, GeoPoint(-23.5506, -46.6334), 54.0),
            RoutePoint(START_MS + 2_000, GeoPoint(-23.5507, -46.6335), 0.0),
        )
        val document = parseGpx(exporter, trip(route = route))

        assertEquals("gpx", document.documentElement.tagName)
        assertEquals("1.1", document.documentElement.getAttribute("version"))
        assertEquals(1, document.getElementsByTagName("trk").length)
        assertEquals(1, document.getElementsByTagName("trkseg").length)
        assertEquals(route.size, document.getElementsByTagName("trkpt").length)

        val points = document.getElementsByTagName("trkpt")
        for (index in route.indices) {
            val element = points.item(index) as org.w3c.dom.Element
            assertEquals(route[index].point.latitude.toString(), element.getAttribute("lat").toDouble().toString())
            assertEquals(route[index].point.longitude.toString(), element.getAttribute("lon").toDouble().toString())
        }
    }

    @Test
    fun `gpx carries utc time and speed in metres per second`() {
        val route = listOf(
            RoutePoint(START_MS, GeoPoint(-23.5505, -46.6333), 36.0),
            RoutePoint(START_MS + 1_000, GeoPoint(-23.5506, -46.6334), 54.0),
        )
        val document = parseGpx(exporter, trip(route = route))

        val times = document.getElementsByTagName("time")
        // Metadata time plus one per track point.
        assertEquals(route.size + 1, times.length)
        val firstPointTime = (document.getElementsByTagName("trkpt").item(0) as org.w3c.dom.Element)
            .getElementsByTagName("time").item(0).textContent
        assertEquals(Instant.ofEpochMilli(START_MS).toString(), firstPointTime)

        val speeds = document.getElementsByTagNameNS("*", "speed")
        assertEquals(route.size, speeds.length)
        assertEquals("10.000", speeds.item(0).textContent)
        assertEquals("15.000", speeds.item(1).textContent)
    }

    @Test
    fun `gpx escapes xml special characters`() {
        val document = parseGpx(exporter, trip(id = "trip & <fast> \"quoted\""))

        assertEquals("trip & <fast> \"quoted\"", document.getElementsByTagName("name").item(0).textContent)
    }

    private fun csvRows(exporter: DefaultTripExporter, trips: List<TripRecord>): List<String> {
        val output = ByteArrayOutputStream()
        exporter.writeCsv(trips, output)
        val text = output.toString(StandardCharsets.UTF_8.name())
        assertTrue(text, text.contains("\r\n"))
        return text.split("\r\n").dropLastWhile { it.isEmpty() }
    }

    private fun parseGpx(exporter: DefaultTripExporter, trip: TripRecord): Document {
        val output = ByteArrayOutputStream()
        exporter.writeGpx(trip, output)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(output.toByteArray()))
    }

    private fun trip(
        id: String = "trip-1",
        vehicleId: String = "vehicle-1",
        events: List<DrivingEvent> = emptyList(),
        route: List<RoutePoint> = emptyList(),
    ): TripRecord = TripRecord(
        id = id,
        vehicleId = vehicleId,
        startedAtMs = START_MS,
        endedAtMs = START_MS + 600_000,
        distanceMeters = 1_234.0,
        movingDurationMs = 500_000,
        idleDurationMs = 100_000,
        averageSpeedKmh = 7.407,
        maxSpeedKmh = 82.0,
        score = 71,
        breakdown = ScoreBreakdown(
            components = emptyList(),
            behaviorScore = 68,
            fuelScore = 80,
            fuelWeight = 0.3,
            overallScore = 71,
        ),
        fuel = FuelEstimate(
            estimatedLiters = 0.1,
            avoidableLiters = 0.041,
            idleLiters = 0.02,
            overspeedLiters = 0.011,
            brakingLiters = 0.006,
            accelerationLiters = 0.003,
            corneringLiters = 0.001,
            fuelPricePerLiter = 6.0,
            currency = "BRL",
        ),
        events = events,
        route = route,
        manualStart = false,
        scoringVersion = ScoringVersion.CURRENT,
        createdAtMs = START_MS + 600_000,
    )

    private fun event(id: String, type: DrivingEventType): DrivingEvent = DrivingEvent(
        id = id,
        type = type,
        startedAtMs = START_MS + 120_000,
        endedAtMs = START_MS + 123_500,
        peakValue = 3.456,
        meanValue = 2.5,
        startSpeedKmh = 40.0,
        endSpeedKmh = 42.0,
        severity = EventSeverity.MODERATE,
        penaltyPoints = 4.5,
        extraFuelMilliliters = 12.5,
        location = GeoPoint(-23.5505, -46.6333),
    )

    private companion object {
        /** 2024-01-01T10:00:00Z. */
        const val START_MS = 1_704_103_200_000L

        const val EXPECTED_HEADER =
            "trip_id,vehicle_id,started_at_iso,ended_at_iso,distance_km,duration_s,moving_s," +
                "idle_s,avg_speed_kmh,max_speed_kmh,score,behavior_score,fuel_score," +
                "estimated_liters,avoidable_liters,avoidable_cost,currency,event_type," +
                "event_severity,event_started_at_iso,event_duration_s,event_peak," +
                "event_extra_fuel_ml"
    }
}
