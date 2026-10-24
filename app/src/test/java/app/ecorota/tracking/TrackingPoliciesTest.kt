package app.ecorota.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecordingPolicyTest {

    @Test
    fun `a short distance is discarded even when it lasted long`() {
        assertTrue(TripRecordingPolicy.shouldDiscard(distanceMeters = 199.9, durationMs = 900_000L))
    }

    @Test
    fun `a short duration is discarded even when it covered distance`() {
        assertTrue(TripRecordingPolicy.shouldDiscard(distanceMeters = 5_000.0, durationMs = 59_999L))
    }

    @Test
    fun `exactly at the thresholds is kept`() {
        assertFalse(
            TripRecordingPolicy.shouldDiscard(
                distanceMeters = TripRecordingPolicy.MIN_DISTANCE_METERS,
                durationMs = TripRecordingPolicy.MIN_DURATION_MS,
            ),
        )
    }

    @Test
    fun `a normal trip is kept`() {
        assertFalse(TripRecordingPolicy.shouldDiscard(distanceMeters = 12_400.0, durationMs = 1_800_000L))
    }
}

class RetentionPolicyTest {

    @Test
    fun `zero days keeps trips forever`() {
        assertNull(RetentionPolicy.cutoffMs(nowMs = 1_000_000_000L, retentionDays = 0))
    }

    @Test
    fun `negative days keeps trips forever`() {
        assertNull(RetentionPolicy.cutoffMs(nowMs = 1_000_000_000L, retentionDays = -5))
    }

    @Test
    fun `thirty days moves the cutoff back thirty days`() {
        val now = 10_000_000_000L

        val cutoff = RetentionPolicy.cutoffMs(nowMs = now, retentionDays = 30)

        assertEquals(now - 30 * RetentionPolicy.MILLIS_PER_DAY, cutoff)
    }
}
