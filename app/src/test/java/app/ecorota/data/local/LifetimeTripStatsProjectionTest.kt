package app.ecorota.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifetimeTripStatsProjectionTest {

    @Test
    fun `empty projection has no trips and no records`() {
        val stats = emptyRow().toLifetimeStats()

        assertFalse(stats.hasTrips)
        assertNull(stats.bestScore)
        assertNull(stats.bestScoreAtMs)
        assertNull(stats.longestTripAtMs)
        assertEquals(0L, stats.averageTripDurationMs)
    }

    @Test
    fun `a real trip keeps its zero score and timestamps`() {
        val stats = row(
            tripCount = 2,
            durationMs = 600_000L,
            bestScore = 0,
            bestScoreAtMs = 1_700_000_000_000L,
            longestTripAtMs = 1_700_000_000_000L,
        ).toLifetimeStats()

        assertTrue(stats.hasTrips)
        assertEquals(0, stats.bestScore)
        assertEquals(1_700_000_000_000L, stats.bestScoreAtMs)
        assertEquals(1_700_000_000_000L, stats.longestTripAtMs)
        assertEquals(300_000L, stats.averageTripDurationMs)
    }

    private fun emptyRow() = row(tripCount = 0, durationMs = 0L)

    private fun row(
        tripCount: Int,
        durationMs: Long,
        bestScore: Int = 0,
        bestScoreAtMs: Long = 0L,
        longestTripAtMs: Long = 0L,
    ) = LifetimeTripStatsProjection(
        tripCount = tripCount,
        distanceMeters = 0.0,
        durationMs = durationMs,
        idleDurationMs = 0L,
        maxSpeedKmh = 0.0,
        averageScore = 0.0,
        accelerationEvents = 0,
        brakingEvents = 0,
        corneringEvents = 0,
        overspeedEvents = 0,
        idlingEvents = 0,
        bestScore = bestScore,
        bestScoreAtMs = bestScoreAtMs,
        longestDistanceMeters = 0.0,
        longestTripAtMs = longestTripAtMs,
    )
}
