package app.ecorota.core.analysis

import app.ecorota.core.model.TripListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TripStatisticsTest {

    @Test
    fun `empty input yields empty totals`() {
        assertEquals(TripTotals(0, 0.0, 0.0, 0.0, 0.0), TripStatistics.totals(emptyList()))
        assertTrue(TripStatistics.byLocalDay(emptyList(), offsetMinutes = 0).isEmpty())
    }

    @Test
    fun `totals aggregate every field`() {
        val totals = TripStatistics.totals(
            listOf(
                trip("a", startedAtMs = instant("2024-01-01T10:00:00Z"), distance = 10_000.0, score = 80, liters = 0.5, cost = 3.0),
                trip("b", startedAtMs = instant("2024-01-01T18:00:00Z"), distance = 5_000.0, score = 60, liters = 0.25, cost = 1.5),
            ),
        )

        assertEquals(2, totals.tripCount)
        assertEquals(15_000.0, totals.distanceMeters, 1e-9)
        assertEquals(70.0, totals.averageScore, 1e-9)
        assertEquals(0.75, totals.avoidableLiters, 1e-9)
        assertEquals(4.5, totals.avoidableCost, 1e-9)
    }

    @Test
    fun `byLocalDay groups and sorts ascending`() {
        val daily = TripStatistics.byLocalDay(
            listOf(
                trip("d2-morning", instant("2024-01-02T10:00:00Z"), 10_000.0, score = 70, liters = 0.25, cost = 1.5),
                trip("d1", instant("2024-01-01T10:00:00Z"), 20_000.0, score = 90, liters = 0.5, cost = 3.0),
                trip("d2-evening", instant("2024-01-02T20:00:00Z"), 5_000.0, score = 80, liters = 0.25, cost = 1.5),
            ),
            offsetMinutes = 0,
        )

        assertEquals(2, daily.size)
        assertEquals(19_723L, daily[0].epochDay)
        assertEquals(1, daily[0].tripCount)
        assertEquals(20_000.0, daily[0].distanceMeters, 1e-9)
        assertEquals(90.0, daily[0].averageScore, 1e-9)
        assertEquals(0.5, daily[0].avoidableLiters, 1e-9)
        assertEquals(3.0, daily[0].avoidableCost, 1e-9)

        assertEquals(19_724L, daily[1].epochDay)
        assertEquals(2, daily[1].tripCount)
        assertEquals(15_000.0, daily[1].distanceMeters, 1e-9)
        assertEquals(75.0, daily[1].averageScore, 1e-9)
        assertEquals(0.5, daily[1].avoidableLiters, 1e-9)
        assertEquals(3.0, daily[1].avoidableCost, 1e-9)
        assertTrue(daily[0].epochDay < daily[1].epochDay)
    }

    @Test
    fun `the offset decides which day a near midnight trip belongs to`() {
        val lateNight = instant("2024-01-01T23:30:00Z")
        val trips = listOf(trip("late", lateNight, 1_000.0, score = 90, liters = 0.1, cost = 0.6))

        assertEquals(19_723L, TripStatistics.byLocalDay(trips, offsetMinutes = 0).single().epochDay)
        assertEquals(19_724L, TripStatistics.byLocalDay(trips, offsetMinutes = 60).single().epochDay)
        assertEquals(19_723L, TripStatistics.byLocalDay(trips, offsetMinutes = -120).single().epochDay)
    }

    @Test
    fun `a trip just before midnight stays on the same day with a positive offset`() {
        val beforeMidnight = instant("2024-01-01T22:30:00Z")
        val trips = listOf(trip("early", beforeMidnight, 1_000.0, score = 90, liters = 0.1, cost = 0.6))

        assertEquals(19_723L, TripStatistics.byLocalDay(trips, offsetMinutes = 60).single().epochDay)
        assertEquals(19_724L, TripStatistics.byLocalDay(trips, offsetMinutes = 120).single().epochDay)
    }

    private fun trip(
        id: String,
        startedAtMs: Long,
        distance: Double,
        score: Int,
        liters: Double,
        cost: Double,
    ) = TripListItem(
        id = id,
        vehicleId = "vehicle",
        startedAtMs = startedAtMs,
        endedAtMs = startedAtMs + 600_000L,
        distanceMeters = distance,
        averageSpeedKmh = 40.0,
        maxSpeedKmh = 90.0,
        score = score,
        eventCount = 3,
        avoidableLiters = liters,
        avoidableCost = cost,
        currency = "BRL",
        manualStart = false,
    )

    private fun instant(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
