package app.ecorota.core.scoring

import app.ecorota.core.model.DetectedEvent
import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.DrivingEventType
import app.ecorota.core.model.EventSeverity
import app.ecorota.core.model.FuelEstimate
import app.ecorota.core.model.ScoreBreakdown
import app.ecorota.core.model.ScoreCategory
import app.ecorota.core.model.ScoreComponent
import app.ecorota.core.model.TripAnalysis
import app.ecorota.core.model.VehicleProfile
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ScoredTrip(
    val analysis: TripAnalysis,
    val events: List<DrivingEvent>,
    val breakdown: ScoreBreakdown,
    val fuel: FuelEstimate,
) {
    val score: Int get() = breakdown.overallScore
}

/**
 * Transparent scoring and fuel estimation.
 *
 * The score is a weighted average of five behavior components (0..100 each),
 * blended with a fuel-waste sub-score. Each component compares the density of
 * severity-weighted events in this trip against a reference density, so a
 * single bad moment in a short trip does not dominate the result beyond its
 * real weight. docs/SCORING.md walks through the formulas.
 */
object TripScorer {

    fun score(
        analysis: TripAnalysis,
        vehicle: VehicleProfile,
        config: ScoringConfig = ScoringConfig(),
    ): ScoredTrip {
        val cfg = config.sanitized()
        val weights = cfg.normalizedWeights()

        val fuel = estimateFuel(analysis, vehicle, cfg)
        val scoredEvents = analysis.events.map { detected ->
            val severity = severityOf(detected, cfg)
            DrivingEvent(
                id = UUID.randomUUID().toString(),
                type = detected.type,
                startedAtMs = detected.startedAtMs,
                endedAtMs = detected.endedAtMs,
                peakValue = detected.peakValue,
                meanValue = detected.meanValue,
                startSpeedKmh = detected.startSpeedKmh,
                endSpeedKmh = detected.endSpeedKmh,
                severity = severity,
                penaltyPoints = eventPoints(detected, severity, cfg),
                extraFuelMilliliters = eventFuelLiters(detected, cfg) * 1000.0,
                location = detected.location,
            )
        }

        val effectiveKm = max(analysis.distanceKm, cfg.minDistanceForDensityKm)

        val accelEvents = scoredEvents.filter { it.type == DrivingEventType.HARD_ACCELERATION }
        val brakeEvents = scoredEvents.filter { it.type == DrivingEventType.HARD_BRAKING }
        val cornerEvents = scoredEvents.filter { it.type == DrivingEventType.SHARP_CORNERING }
        val overspeedEvents = scoredEvents.filter { it.type == DrivingEventType.OVERSPEED }
        val idleEvents = scoredEvents.filter { it.type == DrivingEventType.IDLING }

        val accelWeighted = accelEvents.sumOf { severityMultiplier(it.severity, cfg) }
        val brakeWeighted = brakeEvents.sumOf { severityMultiplier(it.severity, cfg) }
        val cornerWeighted = cornerEvents.sumOf { severityMultiplier(it.severity, cfg) }

        val accelScore = densityScore(
            weightedEvents = accelWeighted,
            effectiveKm = effectiveKm,
            referencePer10Km = cfg.referenceAccelPer10Km,
            lossScale = cfg.componentLossScale,
        )
        val brakeScore = densityScore(
            weightedEvents = brakeWeighted,
            effectiveKm = effectiveKm,
            referencePer10Km = cfg.referenceBrakePer10Km,
            lossScale = cfg.componentLossScale,
        )
        val cornerScore = densityScore(
            weightedEvents = cornerWeighted,
            effectiveKm = effectiveKm,
            referencePer10Km = cfg.referenceCornerPer10Km,
            lossScale = cfg.componentLossScale,
        )

        val tripDuration = analysis.durationMs
        val idleRatio = if (tripDuration > 0) {
            analysis.avoidableIdleMs.toDouble() / tripDuration
        } else {
            0.0
        }
        val idleScore = ratioScore(
            ratio = idleRatio,
            allowed = cfg.idleAllowedRatio,
            penaltyScale = cfg.idlePenaltyScale,
        )

        val overspeedRatio = if (analysis.movingDurationMs > 0) {
            analysis.overspeedDurationMs.toDouble() / analysis.movingDurationMs
        } else {
            0.0
        }
        val overspeedScore = ratioScore(
            ratio = overspeedRatio,
            allowed = cfg.overspeedAllowedRatio,
            penaltyScale = cfg.overspeedPenaltyScale,
        )

        val components = listOf(
            ScoreComponent(
                category = ScoreCategory.ACCELERATION,
                score = accelScore,
                weight = weights.acceleration,
                eventCount = accelEvents.size,
                weightedEventCount = accelWeighted,
                lostPoints = (100 - accelScore) * weights.acceleration,
            ),
            ScoreComponent(
                category = ScoreCategory.BRAKING,
                score = brakeScore,
                weight = weights.braking,
                eventCount = brakeEvents.size,
                weightedEventCount = brakeWeighted,
                lostPoints = (100 - brakeScore) * weights.braking,
            ),
            ScoreComponent(
                category = ScoreCategory.CORNERING,
                score = cornerScore,
                weight = weights.cornering,
                eventCount = cornerEvents.size,
                weightedEventCount = cornerWeighted,
                lostPoints = (100 - cornerScore) * weights.cornering,
            ),
            ScoreComponent(
                category = ScoreCategory.SPEED,
                score = overspeedScore,
                weight = weights.speed,
                eventCount = overspeedEvents.size,
                weightedEventCount = overspeedEvents.sumOf {
                    severityMultiplier(it.severity, cfg)
                },
                lostPoints = (100 - overspeedScore) * weights.speed,
            ),
            ScoreComponent(
                category = ScoreCategory.IDLING,
                score = idleScore,
                weight = weights.idling,
                eventCount = idleEvents.size,
                weightedEventCount = idleEvents.sumOf { severityMultiplier(it.severity, cfg) },
                lostPoints = (100 - idleScore) * weights.idling,
            ),
        )

        val behaviorScore = components.sumOf { it.score * it.weight }.roundToInt().coerceIn(0, 100)
        val fuelScore = fuelScore(fuel, cfg)
        val overall = (behaviorScore * (1 - cfg.fuelWeight) + fuelScore * cfg.fuelWeight)
            .roundToInt()
            .coerceIn(0, 100)

        return ScoredTrip(
            analysis = analysis,
            events = scoredEvents,
            breakdown = ScoreBreakdown(
                components = components,
                behaviorScore = behaviorScore,
                fuelScore = fuelScore,
                fuelWeight = cfg.fuelWeight,
                overallScore = overall,
            ),
            fuel = fuel,
        )
    }

    /** 100 minus the excess event density, converted into points. */
    internal fun densityScore(
        weightedEvents: Double,
        effectiveKm: Double,
        referencePer10Km: Double,
        lossScale: Double,
    ): Int {
        if (effectiveKm <= 0.0) return 100
        val densityPer10Km = weightedEvents / effectiveKm * 10.0
        val excess = max(0.0, densityPer10Km - referencePer10Km)
        return (100.0 - excess * lossScale).coerceIn(0.0, 100.0).roundToInt()
    }

    internal fun ratioScore(ratio: Double, allowed: Double, penaltyScale: Double): Int {
        val excess = max(0.0, ratio - allowed)
        return (100.0 - excess * penaltyScale).coerceIn(0.0, 100.0).roundToInt()
    }

    internal fun fuelScore(fuel: FuelEstimate, config: ScoringConfig): Int {
        if (fuel.estimatedLiters <= 0.0) return 100
        val wasteRatio = (fuel.avoidableLiters / fuel.estimatedLiters).coerceIn(0.0, 1.0)
        val normalized = wasteRatio / config.fuelWasteRatioForZeroScore
        return (100.0 * (1.0 - normalized.coerceIn(0.0, 1.0))).roundToInt().coerceIn(0, 100)
    }

    fun severityOf(event: DetectedEvent, config: ScoringConfig): EventSeverity = when (event.type) {
        DrivingEventType.HARD_ACCELERATION -> ratioSeverity(
            event.peakValue / config.hardAccelThresholdMps2,
            config,
        )
        DrivingEventType.HARD_BRAKING -> ratioSeverity(
            event.peakValue / config.hardBrakeThresholdMps2,
            config,
        )
        DrivingEventType.SHARP_CORNERING -> ratioSeverity(
            event.peakValue / config.sharpCornerThresholdMps2,
            config,
        )
        DrivingEventType.OVERSPEED -> {
            val excess = event.peakValue - config.overspeedThresholdKmh
            when {
                excess < 10.0 -> EventSeverity.MILD
                excess < 25.0 -> EventSeverity.MODERATE
                else -> EventSeverity.SEVERE
            }
        }
        DrivingEventType.IDLING -> {
            val seconds = event.durationMs / 1000.0
            when {
                seconds < 120.0 -> EventSeverity.MILD
                seconds < 300.0 -> EventSeverity.MODERATE
                else -> EventSeverity.SEVERE
            }
        }
    }

    private fun ratioSeverity(ratio: Double, config: ScoringConfig): EventSeverity = when {
        ratio < config.severityMildUpperRatio -> EventSeverity.MILD
        ratio < config.severityModerateUpperRatio -> EventSeverity.MODERATE
        else -> EventSeverity.SEVERE
    }

    fun severityMultiplier(severity: EventSeverity, config: ScoringConfig): Double = when (severity) {
        EventSeverity.MILD -> config.severityMildMultiplier
        EventSeverity.MODERATE -> config.severityModerateMultiplier
        EventSeverity.SEVERE -> config.severitySevereMultiplier
    }

    private fun eventPoints(
        event: DetectedEvent,
        severity: EventSeverity,
        config: ScoringConfig,
    ): Double {
        val minutes = event.durationMs / 60_000.0
        val base = when (event.type) {
            DrivingEventType.HARD_ACCELERATION -> config.accelEventPoints
            DrivingEventType.HARD_BRAKING -> config.brakeEventPoints
            DrivingEventType.SHARP_CORNERING -> config.cornerEventPoints
            DrivingEventType.OVERSPEED -> config.overspeedEventPointsPerMinute * minutes
            DrivingEventType.IDLING -> config.idleEventPointsPerMinute * minutes
        }
        return base * severityMultiplier(severity, config)
    }

    private fun estimateFuel(
        analysis: TripAnalysis,
        vehicle: VehicleProfile,
        config: ScoringConfig,
    ): FuelEstimate {
        val estimatedLiters = max(
            0.0,
            analysis.distanceKm * vehicle.consumptionLitersPer100Km / 100.0,
        )
        var idleLiters = 0.0
        var overspeedLiters = 0.0
        var brakingLiters = 0.0
        var accelerationLiters = 0.0
        var corneringLiters = 0.0

        for (event in analysis.events) {
            val liters = eventFuelLiters(event, config)
            when (event.type) {
                DrivingEventType.IDLING -> idleLiters += liters
                DrivingEventType.OVERSPEED -> overspeedLiters += liters
                DrivingEventType.HARD_BRAKING -> brakingLiters += liters
                DrivingEventType.HARD_ACCELERATION -> accelerationLiters += liters
                DrivingEventType.SHARP_CORNERING -> corneringLiters += liters
            }
        }

        val physicsTotal = idleLiters + overspeedLiters + brakingLiters +
            accelerationLiters + corneringLiters
        val avoidable = min(estimatedLiters, max(0.0, physicsTotal))
        return FuelEstimate(
            estimatedLiters = estimatedLiters,
            avoidableLiters = avoidable,
            idleLiters = idleLiters,
            overspeedLiters = overspeedLiters,
            brakingLiters = brakingLiters,
            accelerationLiters = accelerationLiters,
            corneringLiters = corneringLiters,
            fuelPricePerLiter = vehicle.fuelPricePerLiter,
            currency = vehicle.currency,
        )
    }

    private fun fuelLitersFromEnergy(energyJoules: Double, config: ScoringConfig): Double {
        val fuelEnergyJoules = config.gasolineEnergyMjPerLiter * 1_000_000.0 *
            config.drivetrainEfficiency
        return if (fuelEnergyJoules > 0) energyJoules.coerceAtLeast(0.0) / fuelEnergyJoules else 0.0
    }

    private fun eventFuelLiters(event: DetectedEvent, config: ScoringConfig): Double =
        when (event.type) {
            DrivingEventType.IDLING ->
                event.durationMs / 3_600_000.0 * config.idleFuelLitersPerHour
            DrivingEventType.OVERSPEED -> overspeedFuelLiters(event, config)
            DrivingEventType.HARD_BRAKING ->
                kineticFuelLiters(event, config, config.brakeWasteFactor)
            DrivingEventType.HARD_ACCELERATION ->
                kineticFuelLiters(event, config, config.accelerationInefficiencyFactor)
            DrivingEventType.SHARP_CORNERING -> corneringFuelLiters(event, config)
        }

    private fun kineticFuelLiters(
        event: DetectedEvent,
        config: ScoringConfig,
        factor: Double,
    ): Double {
        val startMps = event.startSpeedKmh / 3.6
        val endMps = event.endSpeedKmh / 3.6
        val deltaSquared = startMps * startMps - endMps * endMps
        if (deltaSquared <= 0.0) return 0.0
        val energy = 0.5 * config.vehicleMassKg * deltaSquared * factor
        return fuelLitersFromEnergy(energy, config)
    }

    /**
     * Extra aerodynamic power above the efficient speed, integrated over the
     * event duration: P = ½ρCdA(v² - vRef²)v.
     */
    private fun overspeedFuelLiters(event: DetectedEvent, config: ScoringConfig): Double {
        val speedMps = event.meanValue.coerceAtLeast(0.0) / 3.6
        val referenceMps = config.overspeedThresholdKmh / 3.6
        val deltaSquared = speedMps * speedMps - referenceMps * referenceMps
        if (deltaSquared <= 0.0) return 0.0
        val extraPowerWatts = 0.5 * config.airDensityKgPerM3 * config.dragAreaCdA *
            deltaSquared * speedMps
        val seconds = event.durationMs / 1000.0
        return fuelLitersFromEnergy(extraPowerWatts * seconds, config)
    }

    /**
     * Tire scrub while cornering, approximated as a small fraction of lateral
     * work over the arc length. Cornering affects the score far more than fuel,
     * which is the honest result of the physics.
     */
    private fun corneringFuelLiters(event: DetectedEvent, config: ScoringConfig): Double {
        val averageSpeedMps = ((event.startSpeedKmh + event.endSpeedKmh) / 2.0) / 3.6
        val arcMeters = averageSpeedMps * event.durationMs / 1000.0
        val energy = config.corneringLossCoefficient * config.vehicleMassKg *
            event.peakValue * arcMeters
        return fuelLitersFromEnergy(energy, config)
    }
}
