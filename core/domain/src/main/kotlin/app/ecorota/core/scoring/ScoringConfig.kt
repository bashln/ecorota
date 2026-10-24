package app.ecorota.core.scoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Every tunable of the scoring pipeline.
 *
 * Defaults were chosen for a compact passenger car (1.4 t) and are documented
 * in docs/SCORING.md. The settings screen lets users adjust detection
 * thresholds; the remaining physics parameters can be changed by editing this
 * class or by importing a tuned configuration.
 */
@Serializable
data class ScoringConfig(
    // Detectors.
    val hardAccelThresholdMps2: Double = 2.5,
    val hardBrakeThresholdMps2: Double = 3.0,
    val sharpCornerThresholdMps2: Double = 2.8,
    val overspeedThresholdKmh: Double = 90.0,
    val overspeedHysteresisKmh: Double = 4.0,
    val overspeedMinDurationMs: Long = 3_000,
    val idleSpeedThresholdKmh: Double = 5.0,
    val idleMinDurationMs: Long = 45_000,
    val idleGraceMs: Long = 15_000,
    val eventMinDurationMs: Long = 500,
    val eventCooldownMs: Long = 3_000,
    val speedSampleIntervalMs: Long = 5_000,
    val maxAccuracyForDistanceMeters: Double = 50.0,

    // Score normalisation.
    val minDistanceForDensityKm: Double = 1.0,
    val referenceAccelPer10Km: Double = 0.5,
    val referenceBrakePer10Km: Double = 0.75,
    val referenceCornerPer10Km: Double = 0.5,
    val componentLossScale: Double = 3.0,
    val idleAllowedRatio: Double = 0.02,
    val idlePenaltyScale: Double = 400.0,
    val overspeedAllowedRatio: Double = 0.02,
    val overspeedPenaltyScale: Double = 250.0,

    // Weights.
    val weightAcceleration: Double = 0.20,
    val weightBraking: Double = 0.25,
    val weightCornering: Double = 0.15,
    val weightSpeed: Double = 0.20,
    val weightIdling: Double = 0.20,
    val fuelWeight: Double = 0.20,
    val fuelWasteRatioForZeroScore: Double = 0.35,

    // Severity bands, as a ratio over the detection threshold.
    val severityMildUpperRatio: Double = 1.25,
    val severityModerateUpperRatio: Double = 1.60,
    val severityMildMultiplier: Double = 0.6,
    val severityModerateMultiplier: Double = 1.0,
    val severitySevereMultiplier: Double = 1.6,

    // Informational points shown next to an event.
    val accelEventPoints: Double = 6.0,
    val brakeEventPoints: Double = 7.0,
    val cornerEventPoints: Double = 4.0,
    val overspeedEventPointsPerMinute: Double = 5.0,
    val idleEventPointsPerMinute: Double = 2.0,

    // Physics used by the fuel estimate.
    val vehicleMassKg: Double = 1_400.0,
    val gasolineEnergyMjPerLiter: Double = 34.2,
    val drivetrainEfficiency: Double = 0.28,
    val idleFuelLitersPerHour: Double = 0.6,
    val dragAreaCdA: Double = 0.7,
    val airDensityKgPerM3: Double = 1.2,
    val brakeWasteFactor: Double = 0.35,
    val accelerationInefficiencyFactor: Double = 0.15,
    val corneringLossCoefficient: Double = 0.01,
) {
    /** Clamps values into usable ranges so a hand-edited config cannot crash scoring. */
    fun sanitized(): ScoringConfig {
        val mildRatio = severityMildUpperRatio.coerceIn(1.0, 3.0)
        return copy(
            hardAccelThresholdMps2 = hardAccelThresholdMps2.coerceIn(0.5, 8.0),
            hardBrakeThresholdMps2 = hardBrakeThresholdMps2.coerceIn(0.5, 8.0),
            sharpCornerThresholdMps2 = sharpCornerThresholdMps2.coerceIn(0.5, 8.0),
            overspeedThresholdKmh = overspeedThresholdKmh.coerceIn(40.0, 160.0),
            overspeedHysteresisKmh = overspeedHysteresisKmh.coerceIn(0.0, 20.0),
            overspeedMinDurationMs = overspeedMinDurationMs.coerceIn(500, 60_000),
            idleSpeedThresholdKmh = idleSpeedThresholdKmh.coerceIn(1.0, 15.0),
            idleMinDurationMs = idleMinDurationMs.coerceIn(5_000, 600_000),
            idleGraceMs = idleGraceMs.coerceIn(0, 120_000),
            eventMinDurationMs = eventMinDurationMs.coerceIn(0, 10_000),
            eventCooldownMs = eventCooldownMs.coerceIn(0, 30_000),
            speedSampleIntervalMs = speedSampleIntervalMs.coerceIn(1_000, 60_000),
            maxAccuracyForDistanceMeters = maxAccuracyForDistanceMeters.coerceIn(5.0, 200.0),
            minDistanceForDensityKm = minDistanceForDensityKm.coerceIn(0.1, 20.0),
            referenceAccelPer10Km = referenceAccelPer10Km.coerceIn(0.0, 50.0),
            referenceBrakePer10Km = referenceBrakePer10Km.coerceIn(0.0, 50.0),
            referenceCornerPer10Km = referenceCornerPer10Km.coerceIn(0.0, 50.0),
            componentLossScale = componentLossScale.coerceIn(0.1, 20.0),
            idleAllowedRatio = idleAllowedRatio.coerceIn(0.0, 0.5),
            idlePenaltyScale = idlePenaltyScale.coerceIn(0.0, 2_000.0),
            overspeedAllowedRatio = overspeedAllowedRatio.coerceIn(0.0, 0.5),
            overspeedPenaltyScale = overspeedPenaltyScale.coerceIn(0.0, 2_000.0),
            weightAcceleration = weightAcceleration.coerceIn(0.0, 1.0),
            weightBraking = weightBraking.coerceIn(0.0, 1.0),
            weightCornering = weightCornering.coerceIn(0.0, 1.0),
            weightSpeed = weightSpeed.coerceIn(0.0, 1.0),
            weightIdling = weightIdling.coerceIn(0.0, 1.0),
            fuelWeight = fuelWeight.coerceIn(0.0, 0.6),
            fuelWasteRatioForZeroScore = fuelWasteRatioForZeroScore.coerceIn(0.05, 1.0),
            severityMildUpperRatio = mildRatio,
            severityModerateUpperRatio = severityModerateUpperRatio.coerceIn(mildRatio, 5.0),
            severityMildMultiplier = severityMildMultiplier.coerceIn(0.0, 3.0),
            severityModerateMultiplier = severityModerateMultiplier.coerceIn(0.0, 3.0),
            severitySevereMultiplier = severitySevereMultiplier.coerceIn(0.0, 5.0),
            accelEventPoints = accelEventPoints.coerceIn(0.0, 100.0),
            brakeEventPoints = brakeEventPoints.coerceIn(0.0, 100.0),
            cornerEventPoints = cornerEventPoints.coerceIn(0.0, 100.0),
            overspeedEventPointsPerMinute = overspeedEventPointsPerMinute.coerceIn(0.0, 100.0),
            idleEventPointsPerMinute = idleEventPointsPerMinute.coerceIn(0.0, 100.0),
            vehicleMassKg = vehicleMassKg.coerceIn(400.0, 6_000.0),
            gasolineEnergyMjPerLiter = gasolineEnergyMjPerLiter.coerceIn(20.0, 50.0),
            drivetrainEfficiency = drivetrainEfficiency.coerceIn(0.05, 0.6),
            idleFuelLitersPerHour = idleFuelLitersPerHour.coerceIn(0.1, 5.0),
            dragAreaCdA = dragAreaCdA.coerceIn(0.2, 3.0),
            airDensityKgPerM3 = airDensityKgPerM3.coerceIn(0.8, 1.5),
            brakeWasteFactor = brakeWasteFactor.coerceIn(0.0, 1.0),
            accelerationInefficiencyFactor = accelerationInefficiencyFactor.coerceIn(0.0, 1.0),
            corneringLossCoefficient = corneringLossCoefficient.coerceIn(0.0, 0.2),
        )
    }

    /** Weights normalised to sum to one, so a user-tuned set still yields a 0..100 score. */
    fun normalizedWeights(): ScoreWeights {
        val sum = weightAcceleration + weightBraking + weightCornering + weightSpeed + weightIdling
        if (sum <= 0.0) return ScoreWeights(0.2, 0.2, 0.2, 0.2, 0.2)
        return ScoreWeights(
            acceleration = weightAcceleration / sum,
            braking = weightBraking / sum,
            cornering = weightCornering / sum,
            speed = weightSpeed / sum,
            idling = weightIdling / sum,
        )
    }
}

data class ScoreWeights(
    val acceleration: Double,
    val braking: Double,
    val cornering: Double,
    val speed: Double,
    val idling: Double,
)

/** Persists a config as JSON inside DataStore. Unknown keys are ignored on read. */
object ScoringConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(config: ScoringConfig): String = json.encodeToString(config.sanitized())

    fun decode(raw: String?): ScoringConfig {
        if (raw.isNullOrBlank()) return ScoringConfig()
        return runCatching { json.decodeFromString<ScoringConfig>(raw).sanitized() }
            .getOrDefault(ScoringConfig())
    }
}
