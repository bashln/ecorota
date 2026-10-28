package app.ecorota.ui.trip

import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.EventSeverity
import app.ecorota.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripMapSelectionTest {
    @Test
    fun selectedEventWithoutLocationDoesNotMoveTheMap() {
        val event = event(id = "without-location", location = null)

        assertNull(selectedMapLocation(listOf(event), event.id))
    }

    @Test
    fun selectedEventWithLocationMovesTheMapToThatEvent() {
        val location = GeoPoint(-23.55, -46.63)
        val event = event(id = "with-location", location = location)

        assertEquals(location, selectedMapLocation(listOf(event), event.id))
    }

    @Test
    fun unknownEventDoesNotMoveTheMap() {
        assertNull(selectedMapLocation(listOf(event("known", GeoPoint(1.0, 2.0))), "unknown"))
    }

    private fun event(id: String, location: GeoPoint?) = DrivingEvent(
        id = id,
        type = DrivingEventType.HARD_BRAKING,
        startedAtMs = 1L,
        endedAtMs = 2L,
        peakValue = 1.0,
        meanValue = 1.0,
        startSpeedKmh = 1.0,
        endSpeedKmh = 0.0,
        severity = EventSeverity.MILD,
        penaltyPoints = 1.0,
        extraFuelMilliliters = 0.0,
        location = location,
    )
}
