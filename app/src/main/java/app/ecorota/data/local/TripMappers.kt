package app.ecorota.data.local

import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.EventSeverity
import app.ecorota.core.model.FuelEstimate
import app.ecorota.core.model.GeoPoint
import app.ecorota.core.model.PolylineCodec
import app.ecorota.core.model.ScoreBreakdown
import app.ecorota.core.model.ScoreCategory
import app.ecorota.core.model.ScoreComponent
import app.ecorota.core.model.TripRecord

/**
 * Trip entity/record mapping. The breakdown is flattened into one column per
 * component so the history query can aggregate scores without joining a child
 * table or decoding a blob.
 */
object TripMappers {

    fun toEntity(trip: TripRecord): TripEntity {
        val breakdown = trip.breakdown
        fun componentOf(category: ScoreCategory): ScoreComponent =
            breakdown.component(category) ?: ScoreComponent(
                category = category,
                score = 100,
                weight = 0.0,
                eventCount = 0,
                weightedEventCount = 0.0,
                lostPoints = 0.0,
            )

        val accel = componentOf(ScoreCategory.ACCELERATION)
        val brake = componentOf(ScoreCategory.BRAKING)
        val corner = componentOf(ScoreCategory.CORNERING)
        val speed = componentOf(ScoreCategory.SPEED)
        val idling = componentOf(ScoreCategory.IDLING)
        val fuel = trip.fuel

        return TripEntity(
            id = trip.id,
            vehicleId = trip.vehicleId,
            startedAtMs = trip.startedAtMs,
            endedAtMs = trip.endedAtMs,
            createdAtMs = trip.createdAtMs,
            distanceMeters = trip.distanceMeters,
            movingDurationMs = trip.movingDurationMs,
            idleDurationMs = trip.idleDurationMs,
            averageSpeedKmh = trip.averageSpeedKmh,
            maxSpeedKmh = trip.maxSpeedKmh,
            score = breakdown.overallScore,
            behaviorScore = breakdown.behaviorScore,
            fuelScore = breakdown.fuelScore,
            fuelWeight = breakdown.fuelWeight,
            accelScore = accel.score,
            brakeScore = brake.score,
            cornerScore = corner.score,
            speedScore = speed.score,
            idleScore = idling.score,
            accelWeight = accel.weight,
            brakeWeight = brake.weight,
            cornerWeight = corner.weight,
            speedWeight = speed.weight,
            idleWeight = idling.weight,
            accelEventCount = accel.eventCount,
            brakeEventCount = brake.eventCount,
            cornerEventCount = corner.eventCount,
            speedEventCount = speed.eventCount,
            idleEventCount = idling.eventCount,
            accelWeightedCount = accel.weightedEventCount,
            brakeWeightedCount = brake.weightedEventCount,
            cornerWeightedCount = corner.weightedEventCount,
            speedWeightedCount = speed.weightedEventCount,
            idleWeightedCount = idling.weightedEventCount,
            fuelEstimatedLiters = fuel.estimatedLiters,
            fuelAvoidableLiters = fuel.avoidableLiters,
            fuelIdleLiters = fuel.idleLiters,
            fuelOverspeedLiters = fuel.overspeedLiters,
            fuelBrakingLiters = fuel.brakingLiters,
            fuelAccelerationLiters = fuel.accelerationLiters,
            fuelCorneringLiters = fuel.corneringLiters,
            fuelPricePerLiter = fuel.fuelPricePerLiter,
            currency = fuel.currency,
            manualStart = trip.manualStart,
            scoringVersion = trip.scoringVersion,
            routePolyline = PolylineCodec.encode(trip.route.map { it.point }),
            // One speed sample per route point keeps timestamps exact on read.
            // Storing the analyzer's separate 5 s series here would leave the two
            // columns with different lengths and force invented times in GPX.
            speedSeries = TripCodecs.speedSeriesOf(trip.route),
        )
    }

    fun toEventEntities(tripId: String, events: List<DrivingEvent>): List<DrivingEventEntity> =
        events.mapIndexed { index, event ->
            DrivingEventEntity(
                id = event.id,
                tripId = tripId,
                type = event.type.name,
                startedAtMs = event.startedAtMs,
                endedAtMs = event.endedAtMs,
                peakValue = event.peakValue,
                meanValue = event.meanValue,
                startSpeedKmh = event.startSpeedKmh,
                endSpeedKmh = event.endSpeedKmh,
                severity = event.severity.name,
                penaltyPoints = event.penaltyPoints,
                extraFuelMilliliters = event.extraFuelMilliliters,
                latitude = event.location?.latitude,
                longitude = event.location?.longitude,
                sortOrder = index,
            )
        }

    fun toRecord(trip: TripEntity, events: List<DrivingEventEntity>): TripRecord = TripRecord(
        id = trip.id,
        vehicleId = trip.vehicleId,
        startedAtMs = trip.startedAtMs,
        endedAtMs = trip.endedAtMs,
        distanceMeters = trip.distanceMeters,
        movingDurationMs = trip.movingDurationMs,
        idleDurationMs = trip.idleDurationMs,
        averageSpeedKmh = trip.averageSpeedKmh,
        maxSpeedKmh = trip.maxSpeedKmh,
        score = trip.score,
        breakdown = trip.toBreakdown(),
        fuel = trip.toFuelEstimate(),
        events = events.sortedBy { it.sortOrder }.map { it.toDrivingEvent() },
        route = TripCodecs.decodeRoute(
            routePolyline = trip.routePolyline,
            speedSeries = trip.speedSeries,
            startedAtMs = trip.startedAtMs,
            endedAtMs = trip.endedAtMs,
        ),
        manualStart = trip.manualStart,
        scoringVersion = trip.scoringVersion,
        createdAtMs = trip.createdAtMs,
        speedSamples = TripCodecs.decodeSpeedSeries(trip.speedSeries),
    )
    private fun TripEntity.toBreakdown(): ScoreBreakdown = ScoreBreakdown(
        components = listOf(
            component(ScoreCategory.ACCELERATION, accelScore, accelWeight, accelEventCount, accelWeightedCount),
            component(ScoreCategory.BRAKING, brakeScore, brakeWeight, brakeEventCount, brakeWeightedCount),
            component(ScoreCategory.CORNERING, cornerScore, cornerWeight, cornerEventCount, cornerWeightedCount),
            component(ScoreCategory.SPEED, speedScore, speedWeight, speedEventCount, speedWeightedCount),
            component(ScoreCategory.IDLING, idleScore, idleWeight, idleEventCount, idleWeightedCount),
        ),
        behaviorScore = behaviorScore,
        fuelScore = fuelScore,
        fuelWeight = fuelWeight,
        overallScore = score,
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

    private fun TripEntity.toFuelEstimate(): FuelEstimate = FuelEstimate(
        estimatedLiters = fuelEstimatedLiters,
        avoidableLiters = fuelAvoidableLiters,
        idleLiters = fuelIdleLiters,
        overspeedLiters = fuelOverspeedLiters,
        brakingLiters = fuelBrakingLiters,
        accelerationLiters = fuelAccelerationLiters,
        corneringLiters = fuelCorneringLiters,
        fuelPricePerLiter = fuelPricePerLiter,
        currency = currency,
    )

    private fun DrivingEventEntity.toDrivingEvent(): DrivingEvent = DrivingEvent(
        id = id,
        type = DrivingEventType.valueOf(type),
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        peakValue = peakValue,
        meanValue = meanValue,
        startSpeedKmh = startSpeedKmh,
        endSpeedKmh = endSpeedKmh,
        severity = EventSeverity.valueOf(severity),
        penaltyPoints = penaltyPoints,
        extraFuelMilliliters = extraFuelMilliliters,
        location = if (latitude != null && longitude != null) {
            GeoPoint(latitude = latitude, longitude = longitude)
        } else {
            null
        },
    )
}
