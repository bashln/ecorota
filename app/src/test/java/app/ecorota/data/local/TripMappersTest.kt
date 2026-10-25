package app.ecorota.data.local

import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.EventSeverity
import app.ecorota.core.model.FuelEstimate
import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.RoutePoint
import app.ecorota.core.model.ScoreBreakdown
import app.ecorota.core.model.ScoreCategory
import app.ecorota.core.model.ScoreComponent
import app.ecorota.core.model.TripRecord
import app.ecorota.core.model.VehicleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMappersTest {

    @Test
    fun `trip record survives a round trip through the entity`() {
        val trip = buildTrip()

        val entity = TripMappers.toEntity(trip)
        val events = TripMappers.toEventEntities(trip.id, trip.events)
        val restored = TripMappers.toRecord(entity, events)

        assertEquals(trip.id, restored.id)
        assertEquals(trip.vehicleId, restored.vehicleId)
        assertEquals(trip.startedAtMs, restored.startedAtMs)
        assertEquals(trip.endedAtMs, restored.endedAtMs)
        assertEquals(trip.createdAtMs, restored.createdAtMs)
        assertEquals(trip.distanceMeters, restored.distanceMeters, 0.0)
        assertEquals(trip.movingDurationMs, restored.movingDurationMs)
        assertEquals(trip.idleDurationMs, restored.idleDurationMs)
        assertEquals(trip.averageSpeedKmh, restored.averageSpeedKmh, 0.0)
        assertEquals(trip.maxSpeedKmh, restored.maxSpeedKmh, 0.0)
        assertEquals(trip.score, restored.score)
        assertEquals(trip.manualStart, restored.manualStart)
        assertEquals(trip.scoringVersion, restored.scoringVersion)
        assertEquals(trip.breakdown, restored.breakdown)
        assertEquals(trip.fuel, restored.fuel)
        assertEquals(trip.events, restored.events)
        assertEquals(trip.route, restored.route)
    }

    @Test
    fun `breakdown weights and weighted counts round trip`() {
        val trip = buildTrip()

        val restored = TripMappers.toRecord(
            TripMappers.toEntity(trip),
            TripMappers.toEventEntities(trip.id, trip.events),
        )

        val component = requireNotNull(restored.breakdown.component(ScoreCategory.BRAKING))
        assertEquals(82, component.score)
        assertEquals(0.25, component.weight, 0.0)
        assertEquals(2, component.eventCount)
        assertEquals(2.4, component.weightedEventCount, 1e-9)
        assertEquals((100 - 82) * 0.25, component.lostPoints, 1e-9)
        assertEquals(71, restored.breakdown.behaviorScore)
        assertEquals(64, restored.breakdown.fuelScore)
        assertEquals(0.2, restored.breakdown.fuelWeight, 0.0)
        assertEquals(69, restored.breakdown.overallScore)
    }

    @Test
    fun `event without a location keeps a null location and events stay ordered`() {
        val trip = buildTrip()

        val restored = TripMappers.toRecord(
            TripMappers.toEntity(trip),
            TripMappers.toEventEntities(trip.id, trip.events).reversed(),
        )

        assertEquals(listOf("brake-1", "idle-1"), restored.events.map { it.id })
        assertNull(restored.events[1].location)
        assertTrue(restored.events[0].location != null)
    }

    @Test
    fun `trip without route or events round trips as empty collections`() {
        val trip = buildTrip().copy(route = emptyList(), events = emptyList())

        val entity = TripMappers.toEntity(trip)
        assertEquals("", entity.routePolyline)
        assertEquals("", entity.speedSeries)

        val restored = TripMappers.toRecord(entity, emptyList())
        assertTrue(restored.route.isEmpty())
        assertTrue(restored.events.isEmpty())
    }

    @Test
    fun `vehicle profile round trips through its entity`() {
        val vehicle = VehicleProfile(
            id = "van",
            name = "Van",
            consumptionLitersPer100Km = 11.3,
            fuelPricePerLiter = 6.49,
            currency = "BRL",
            isDefault = false,
        )

        val restored = with(VehicleMappers) { vehicle.toEntity().toProfile() }

        assertEquals(vehicle, restored)
        assertFalse(restored.isDefault)
    }

    private fun buildTrip(): TripRecord = TripRecord(
        id = "trip-1",
        vehicleId = VehicleProfile.DEFAULT_ID,
        startedAtMs = 1_700_000_000_000,
        endedAtMs = 1_700_000_600_000,
        distanceMeters = 7_820.0,
        movingDurationMs = 540_000,
        idleDurationMs = 60_000,
        averageSpeedKmh = 52.13,
        maxSpeedKmh = 96.4,
        score = 69,
        breakdown = ScoreBreakdown(
            components = listOf(
                component(ScoreCategory.ACCELERATION, 88, 0.20, 1, 1.0),
                component(ScoreCategory.BRAKING, 82, 0.25, 2, 2.4),
                component(ScoreCategory.CORNERING, 95, 0.15, 0, 0.0),
                component(ScoreCategory.SPEED, 74, 0.20, 1, 1.6),
                component(ScoreCategory.IDLING, 60, 0.20, 1, 0.6),
            ),
            behaviorScore = 71,
            fuelScore = 64,
            fuelWeight = 0.2,
            overallScore = 69,
        ),
        fuel = FuelEstimate(
            estimatedLiters = 0.6647,
            avoidableLiters = 0.1342,
            idleLiters = 0.01,
            overspeedLiters = 0.04,
            brakingLiters = 0.05,
            accelerationLiters = 0.02,
            corneringLiters = 0.0142,
            fuelPricePerLiter = 6.09,
            currency = "BRL",
        ),
        events = listOf(
            DrivingEvent(
                id = "brake-1",
                type = DrivingEventType.HARD_BRAKING,
                startedAtMs = 1_700_000_100_000,
                endedAtMs = 1_700_000_101_500,
                peakValue = 4.2,
                meanValue = 3.6,
                startSpeedKmh = 72.0,
                endSpeedKmh = 48.0,
                severity = EventSeverity.MODERATE,
                penaltyPoints = 7.0,
                extraFuelMilliliters = 42.5,
                location = GeoPoint(-23.55052, -46.63331),
            ),
            DrivingEvent(
                id = "idle-1",
                type = DrivingEventType.IDLING,
                startedAtMs = 1_700_000_300_000,
                endedAtMs = 1_700_000_360_000,
                peakValue = 60.0,
                meanValue = 60.0,
                startSpeedKmh = 0.0,
                endSpeedKmh = 0.0,
                severity = EventSeverity.MILD,
                penaltyPoints = 1.2,
                extraFuelMilliliters = 10.0,
                location = null,
            ),
        ),
        route = listOf(
            RoutePoint(1_700_000_000_000, GeoPoint(-23.55052, -46.63331), 12.5),
            RoutePoint(1_700_000_004_000, GeoPoint(-23.55152, -46.63231), 48.0),
            RoutePoint(1_700_000_009_000, GeoPoint(-23.55252, -46.63131), 72.75),
        ),
        manualStart = true,
        scoringVersion = 1,
        createdAtMs = 1_700_000_600_500,
    )

    private fun component(
        category: ScoreCategory,
        score: Int,
        weight: Double,
        eventCount: Int,
        weightedCount: Double,
    ): ScoreComponent = ScoreComponent(
        category = category,
        score = score,
        weight = weight,
        eventCount = eventCount,
        weightedEventCount = weightedCount,
        lostPoints = (100 - score) * weight,
    )
}
