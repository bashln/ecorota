package app.ecorota.core.scoring

import app.ecorota.core.model.DetectedEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.EventSeverity
import app.ecorota.core.model.FuelEstimate
import app.ecorota.core.model.ScoreCategory
import app.ecorota.core.model.TripAnalysis
import app.ecorota.core.model.VehicleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class TripScorerTest {

    private val config = ScoringConfig()
    private val vehicle = VehicleProfile(
        id = "test",
        name = "test",
        consumptionLitersPer100Km = 8.0,
        fuelPricePerLiter = 6.0,
    )

    @Test
    fun `density score follows the documented formula`() {
        assertEquals(86, TripScorer.densityScore(3.0, 2.0, 1.0, 1.0))
        assertEquals(100, TripScorer.densityScore(0.5, 10.0, 1.0, 1.0))
        assertEquals(0, TripScorer.densityScore(100.0, 1.0, 0.5, 3.0))
        assertEquals(100, TripScorer.densityScore(5.0, 0.0, 0.5, 3.0))
    }

    @Test
    fun `ratio score follows the documented formula`() {
        assertEquals(68, TripScorer.ratioScore(0.10, 0.02, 400.0))
        assertEquals(100, TripScorer.ratioScore(0.02, 0.02, 400.0))
        assertEquals(100, TripScorer.ratioScore(0.0, 0.02, 400.0))
        assertEquals(0, TripScorer.ratioScore(1.0, 0.02, 400.0))
    }

    @Test
    fun `weights are normalised to one`() {
        val weighted = ScoringConfig(
            weightAcceleration = 1.0,
            weightBraking = 0.0,
            weightCornering = 0.0,
            weightSpeed = 0.0,
            weightIdling = 0.0,
        ).normalizedWeights()
        assertEquals(1.0, weighted.acceleration, 1e-12)
        assertEquals(0.0, weighted.braking, 1e-12)
        assertEquals(0.0, weighted.cornering, 1e-12)
        assertEquals(0.0, weighted.speed, 1e-12)
        assertEquals(0.0, weighted.idling, 1e-12)

        val defaults = ScoringConfig().normalizedWeights()
        assertEquals(0.20, defaults.acceleration, 1e-12)
        assertEquals(0.25, defaults.braking, 1e-12)
        assertEquals(0.15, defaults.cornering, 1e-12)
        assertEquals(0.20, defaults.speed, 1e-12)
        assertEquals(0.20, defaults.idling, 1e-12)
        assertEquals(
            1.0,
            defaults.acceleration + defaults.braking + defaults.cornering +
                defaults.speed + defaults.idling,
            1e-12,
        )

        val zero = ScoringConfig(
            weightAcceleration = 0.0,
            weightBraking = 0.0,
            weightCornering = 0.0,
            weightSpeed = 0.0,
            weightIdling = 0.0,
        ).normalizedWeights()
        assertEquals(0.2, zero.acceleration, 1e-12)
        assertEquals(0.2, zero.idling, 1e-12)
    }

    @Test
    fun `acceleration severity maps from the threshold ratio`() {
        assertEquals(EventSeverity.MILD, TripScorer.severityOf(accel(3.0), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(accel(3.125), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(accel(3.5), config))
        assertEquals(EventSeverity.SEVERE, TripScorer.severityOf(accel(4.0), config))
    }

    @Test
    fun `braking severity maps from the threshold ratio`() {
        val braking = { peak: Double -> event(DrivingEventType.HARD_BRAKING, peak) }
        assertEquals(EventSeverity.MILD, TripScorer.severityOf(braking(3.3), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(braking(3.75), config))
        assertEquals(EventSeverity.SEVERE, TripScorer.severityOf(braking(5.0), config))
    }

    @Test
    fun `cornering severity maps from the threshold ratio`() {
        val cornering = { peak: Double -> event(DrivingEventType.SHARP_CORNERING, peak) }
        assertEquals(EventSeverity.MILD, TripScorer.severityOf(cornering(3.0), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(cornering(3.5), config))
        assertEquals(EventSeverity.SEVERE, TripScorer.severityOf(cornering(5.0), config))
    }

    @Test
    fun `overspeed severity maps from the excess over the limit`() {
        assertEquals(EventSeverity.MILD, TripScorer.severityOf(event(DrivingEventType.OVERSPEED, 95.0), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(event(DrivingEventType.OVERSPEED, 100.0), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(event(DrivingEventType.OVERSPEED, 110.0), config))
        assertEquals(EventSeverity.SEVERE, TripScorer.severityOf(event(DrivingEventType.OVERSPEED, 115.0), config))
    }

    @Test
    fun `idling severity maps from the event duration`() {
        assertEquals(EventSeverity.MILD, TripScorer.severityOf(idle(60.0), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(idle(120.0), config))
        assertEquals(EventSeverity.MODERATE, TripScorer.severityOf(idle(299.0), config))
        assertEquals(EventSeverity.SEVERE, TripScorer.severityOf(idle(300.0), config))
    }

    @Test
    fun `lost points match the formula and sum to the behavior score`() {
        val scored = TripScorer.score(
            analysis = analysis(
                distanceMeters = 3_000.0,
                movingDurationMs = 300_000L,
                idleDurationMs = 60_000L,
                events = listOf(
                    accel(4.0),
                    event(DrivingEventType.HARD_BRAKING, 5.0),
                    event(DrivingEventType.SHARP_CORNERING, 5.0),
                    event(DrivingEventType.OVERSPEED, 120.0, mean = 115.0, durationMs = 20_000L),
                    idle(30.0),
                    idle(30.0),
                ),
            ),
            vehicle = vehicle,
            config = config,
        )

        val breakdown = scored.breakdown
        assertEquals(1.0, breakdown.components.sumOf { it.weight }, 1e-9)
        for (component in breakdown.components) {
            assertTrue("${component.category} score out of range", component.score in 0..100)
            assertEquals(
                "${component.category} lostPoints",
                (100 - component.score) * component.weight,
                component.lostPoints,
                1e-9,
            )
            assertNotNull(breakdown.component(component.category))
        }

        val rawBehavior = breakdown.components.sumOf { it.score * it.weight }
        assertEquals(rawBehavior.roundToInt(), breakdown.behaviorScore)
        assertEquals(
            100 - breakdown.components.sumOf { it.lostPoints },
            breakdown.behaviorScore.toDouble(),
            0.5 + 1e-9,
        )

        val blended = breakdown.behaviorScore * (1 - breakdown.fuelWeight) +
            breakdown.fuelScore * breakdown.fuelWeight
        assertEquals(blended.roundToInt(), breakdown.overallScore)
    }

    @Test
    fun `a smooth trip outscores an aggressive trip of the same distance`() {
        val distance = 5_000.0
        val smooth = TripScorer.score(analysis(distanceMeters = distance), vehicle, config)

        val aggressive = TripScorer.score(
            analysis(
                distanceMeters = distance,
                movingDurationMs = 300_000L,
                idleDurationMs = 90_000L,
                events = List(4) { accel(5.0) } +
                    List(4) { event(DrivingEventType.HARD_BRAKING, 5.0) } +
                    List(4) { event(DrivingEventType.SHARP_CORNERING, 5.0) } +
                    event(DrivingEventType.OVERSPEED, 135.0, mean = 130.0, durationMs = 30_000L) +
                    List(3) { idle(30.0) },
            ),
            vehicle,
            config,
        )

        assertEquals(100, smooth.score)
        assertEquals(100, smooth.breakdown.behaviorScore)
        assertTrue(aggressive.score < smooth.score)
        assertTrue(aggressive.breakdown.behaviorScore < smooth.breakdown.behaviorScore)
    }

    @Test
    fun `fuel scales with distance and consumption`() {
        val base = TripScorer.score(analysis(distanceMeters = 50_000.0), vehicle, config)
        assertEquals(4.0, base.fuel.estimatedLiters, 1e-9)

        val farther = TripScorer.score(analysis(distanceMeters = 100_000.0), vehicle, config)
        assertEquals(8.0, farther.fuel.estimatedLiters, 1e-9)

        val thirstier = TripScorer.score(
            analysis(distanceMeters = 50_000.0),
            vehicle.copy(consumptionLitersPer100Km = 12.0),
            config,
        )
        assertEquals(6.0, thirstier.fuel.estimatedLiters, 1e-9)

        val empty = TripScorer.score(analysis(distanceMeters = 0.0), vehicle, config)
        assertEquals(0.0, empty.fuel.estimatedLiters, 0.0)
        assertEquals(100, empty.breakdown.fuelScore)
    }

    @Test
    fun `avoidable fuel never exceeds the estimate`() {
        val scored = TripScorer.score(
            analysis = analysis(
                distanceMeters = 10.0,
                idleDurationMs = 36_000_000L,
                events = List(10) { idle(3_600.0) },
                durationMs = 36_000_000L,
            ),
            vehicle = vehicle,
            config = config,
        )

        assertEquals(0.0008, scored.fuel.estimatedLiters, 1e-12)
        assertEquals(0.0008, scored.fuel.avoidableLiters, 1e-12)
        assertEquals(6.0, scored.fuel.idleLiters, 1e-9)
        assertTrue(scored.fuel.avoidableLiters <= scored.fuel.estimatedLiters)
        assertEquals(100.0, scored.fuel.avoidablePercent, 1e-9)
    }

    @Test
    fun `idle fuel is computed from liters per hour`() {
        val oneHour = TripScorer.score(
            analysis(distanceMeters = 100_000.0, events = listOf(idle(3_600.0))),
            vehicle,
            config,
        )
        assertEquals(0.6, oneHour.fuel.idleLiters, 1e-9)
        assertEquals(0.6, oneHour.fuel.avoidableLiters, 1e-9)

        val halfHour = TripScorer.score(
            analysis(distanceMeters = 100_000.0, events = listOf(idle(1_800.0))),
            vehicle,
            config,
        )
        assertEquals(0.3, halfHour.fuel.idleLiters, 1e-9)
    }

    @Test
    fun `overspeed fuel grows with speed and duration`() {
        val base = TripScorer.score(
            analysis(
                distanceMeters = 100_000.0,
                events = listOf(event(DrivingEventType.OVERSPEED, 115.0, mean = 110.0, durationMs = 10_000L)),
            ),
            vehicle,
            config,
        )
        val faster = TripScorer.score(
            analysis(
                distanceMeters = 100_000.0,
                events = listOf(event(DrivingEventType.OVERSPEED, 135.0, mean = 130.0, durationMs = 10_000L)),
            ),
            vehicle,
            config,
        )
        val longer = TripScorer.score(
            analysis(
                distanceMeters = 100_000.0,
                events = listOf(event(DrivingEventType.OVERSPEED, 115.0, mean = 110.0, durationMs = 20_000L)),
            ),
            vehicle,
            config,
        )

        assertTrue(base.fuel.overspeedLiters > 0.0)
        assertTrue(faster.fuel.overspeedLiters > base.fuel.overspeedLiters)
        assertTrue(longer.fuel.overspeedLiters > base.fuel.overspeedLiters)
        assertEquals(2 * base.fuel.overspeedLiters, longer.fuel.overspeedLiters, 1e-12)
    }

    @Test
    fun `braking fuel grows with the speed drop`() {
        fun brakingFuel(startKmh: Double, endKmh: Double): Double = TripScorer.score(
            analysis(
                distanceMeters = 100_000.0,
                events = listOf(
                    event(
                        DrivingEventType.HARD_BRAKING,
                        5.0,
                        startSpeedKmh = startKmh,
                        endSpeedKmh = endKmh,
                    ),
                ),
            ),
            vehicle,
            config,
        ).fuel.brakingLiters

        val hardDrop = brakingFuel(100.0, 40.0)
        val lightDrop = brakingFuel(100.0, 80.0)
        val noDrop = brakingFuel(50.0, 50.0)

        assertTrue(hardDrop > 0.0)
        assertTrue(hardDrop > lightDrop)
        assertTrue(lightDrop > 0.0)
        assertEquals(0.0, noDrop, 0.0)
    }

    @Test
    fun `config changes move the score in the expected direction`() {
        val idleTrip = analysis(
            distanceMeters = 10_000.0,
            movingDurationMs = 480_000L,
            idleDurationMs = 120_000L,
            events = listOf(idle(120.0)),
            durationMs = 600_000L,
        )
        val defaultIdle = TripScorer.score(idleTrip, vehicle, ScoringConfig())
            .breakdown.component(ScoreCategory.IDLING)!!.score
        val harsherIdle = TripScorer.score(idleTrip, vehicle, ScoringConfig(idlePenaltyScale = 1_000.0))
            .breakdown.component(ScoreCategory.IDLING)!!.score
        assertEquals(28, defaultIdle)
        assertTrue(harsherIdle < defaultIdle)

        val accelTrip = analysis(distanceMeters = 10_000.0, events = listOf(accel(3.0)))
        val defaultSeverity = TripScorer.score(accelTrip, vehicle, ScoringConfig()).events.single().severity
        val highThresholdSeverity = TripScorer.score(
            accelTrip,
            vehicle,
            ScoringConfig(hardAccelThresholdMps2 = 4.0),
        ).events.single().severity
        assertEquals(EventSeverity.MILD, defaultSeverity)
        assertEquals(EventSeverity.MILD, highThresholdSeverity)
        val lowThresholdSeverity = TripScorer.score(
            accelTrip,
            vehicle,
            ScoringConfig(hardAccelThresholdMps2 = 2.0),
        ).events.single().severity
        assertEquals(EventSeverity.MODERATE, lowThresholdSeverity)

        val sparseTrip = analysis(
            distanceMeters = 10_000.0,
            events = List(2) { accel(4.0) },
        )
        val gentleScale = TripScorer.score(sparseTrip, vehicle, ScoringConfig(componentLossScale = 0.5))
            .breakdown.component(ScoreCategory.ACCELERATION)!!.score
        val harshScale = TripScorer.score(sparseTrip, vehicle, ScoringConfig(componentLossScale = 3.0))
            .breakdown.component(ScoreCategory.ACCELERATION)!!.score
        assertEquals(99, gentleScale)
        assertEquals(92, harshScale)

        val wasteTrip = analysis(
            distanceMeters = 100_000.0,
            events = listOf(idle(6_000.0)),
        )
        val forgiving = TripScorer.score(
            wasteTrip,
            vehicle.copy(consumptionLitersPer100Km = 10.0),
            ScoringConfig(fuelWasteRatioForZeroScore = 0.5),
        ).breakdown.fuelScore
        val strict = TripScorer.score(
            wasteTrip,
            vehicle.copy(consumptionLitersPer100Km = 10.0),
            ScoringConfig(fuelWasteRatioForZeroScore = 0.35),
        ).breakdown.fuelScore
        assertEquals(80, forgiving)
        assertEquals(71, strict)
        assertTrue(strict < forgiving)

        val brakingOnly = TripScorer.score(
            analysis(distanceMeters = 10_000.0, events = listOf(event(DrivingEventType.HARD_BRAKING, 5.0))),
            vehicle,
            ScoringConfig(
                weightAcceleration = 0.0,
                weightBraking = 1.0,
                weightCornering = 0.0,
                weightSpeed = 0.0,
                weightIdling = 0.0,
            ),
        ).breakdown
        assertEquals(
            brakingOnly.component(ScoreCategory.BRAKING)!!.score,
            brakingOnly.behaviorScore,
        )
    }

    @Test
    fun `event points and extra fuel are attached to scored events`() {
        val scored = TripScorer.score(
            analysis(
                distanceMeters = 10_000.0,
                events = listOf(
                    event(
                        DrivingEventType.HARD_ACCELERATION,
                        3.0,
                        startSpeedKmh = 40.0,
                        endSpeedKmh = 60.0,
                    ),
                    event(DrivingEventType.OVERSPEED, 120.0, mean = 110.0, durationMs = 120_000L),
                    idle(60.0),
                ),
            ),
            vehicle,
            config,
        )

        val acceleration = scored.events.single { it.type == DrivingEventType.HARD_ACCELERATION }
        assertEquals(EventSeverity.MILD, acceleration.severity)
        assertEquals(3.6, acceleration.penaltyPoints, 1e-9)
        assertEquals(scored.fuel.accelerationLiters * 1000.0, acceleration.extraFuelMilliliters, 1e-9)
        assertTrue(acceleration.extraFuelMilliliters > 0.0)

        val overspeed = scored.events.single { it.type == DrivingEventType.OVERSPEED }
        assertEquals(EventSeverity.SEVERE, overspeed.severity)
        assertEquals(16.0, overspeed.penaltyPoints, 1e-9)

        val idling = scored.events.single { it.type == DrivingEventType.IDLING }
        assertEquals(EventSeverity.MILD, idling.severity)
        assertEquals(1.2, idling.penaltyPoints, 1e-9)
    }

    private fun accel(peak: Double) = event(DrivingEventType.HARD_ACCELERATION, peak)

    private fun idle(seconds: Double) = event(
        type = DrivingEventType.IDLING,
        peak = seconds,
        durationMs = (seconds * 1000).toLong(),
    )

    private fun event(
        type: DrivingEventType,
        peak: Double,
        startSpeedKmh: Double = 0.0,
        endSpeedKmh: Double = 0.0,
        mean: Double = peak,
        durationMs: Long = 1_000L,
    ) = DetectedEvent(
        type = type,
        startedAtMs = 0L,
        endedAtMs = durationMs,
        peakValue = peak,
        meanValue = mean,
        startSpeedKmh = startSpeedKmh,
        endSpeedKmh = endSpeedKmh,
        location = null,
    )

    private fun analysis(
        distanceMeters: Double = 5_000.0,
        movingDurationMs: Long = 600_000L,
        idleDurationMs: Long = 0L,
        events: List<DetectedEvent> = emptyList(),
        durationMs: Long = 900_000L,
    ) = TripAnalysis(
        startedAtMs = 0L,
        endedAtMs = durationMs,
        distanceMeters = distanceMeters,
        movingDurationMs = movingDurationMs,
        idleDurationMs = idleDurationMs,
        maxSpeedKmh = 90.0,
        events = events,
        route = emptyList(),
        speedSamples = emptyList(),
        lastSpeedKmh = 0.0,
    )

    @Test
    fun `fuel score maps waste ratio to points`() {
        assertEquals(
            100,
            TripScorer.fuelScore(
                FuelEstimate.empty().copy(estimatedLiters = 10.0, avoidableLiters = 0.0),
                config,
            ),
        )
        assertEquals(
            50,
            TripScorer.fuelScore(
                FuelEstimate.empty().copy(estimatedLiters = 10.0, avoidableLiters = 1.75),
                config,
            ),
        )
        assertEquals(
            0,
            TripScorer.fuelScore(
                FuelEstimate.empty().copy(estimatedLiters = 10.0, avoidableLiters = 3.5),
                config,
            ),
        )
        assertEquals(100, TripScorer.fuelScore(FuelEstimate.empty(), config))
    }
}
