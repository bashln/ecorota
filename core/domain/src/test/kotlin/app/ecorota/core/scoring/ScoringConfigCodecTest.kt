package app.ecorota.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringConfigCodecTest {

    @Test
    fun `decode of a missing or malformed payload falls back to defaults`() {
        assertEquals(ScoringConfig(), ScoringConfigCodec.decode(null))
        assertEquals(ScoringConfig(), ScoringConfigCodec.decode(""))
        assertEquals(ScoringConfig(), ScoringConfigCodec.decode("   "))
        assertEquals(ScoringConfig(), ScoringConfigCodec.decode("not json at all"))
        assertEquals(ScoringConfig(), ScoringConfigCodec.decode("""{"hardAccelThresholdMps2":"fast"}"""))
    }

    @Test
    fun `encode sanitizes before persisting`() {
        val decoded = ScoringConfigCodec.decode(
            """{"hardAccelThresholdMps2":-4.0,"weightAcceleration":9.0,"fuelWeight":2.0}""",
        )

        assertEquals(0.5, decoded.hardAccelThresholdMps2, 1e-12)
        assertEquals(1.0, decoded.weightAcceleration, 1e-12)
        assertEquals(0.6, decoded.fuelWeight, 1e-12)
        assertEquals(ScoringConfig().overspeedThresholdKmh, decoded.overspeedThresholdKmh, 1e-12)
    }

    @Test
    fun `unknown keys are ignored`() {
        val config = ScoringConfig(hardAccelThresholdMps2 = 2.7, idleGraceMs = 12_000L)
        val encoded = ScoringConfigCodec.encode(config)
        val withExtraKey = encoded.dropLast(1) + ""","legacyKey":{"nested":true}}"""

        assertEquals(config, ScoringConfigCodec.decode(withExtraKey))
    }

    @Test
    fun `every field survives a round trip`() {
        val config = ScoringConfig(
            hardAccelThresholdMps2 = 2.1,
            hardBrakeThresholdMps2 = 3.1,
            sharpCornerThresholdMps2 = 2.9,
            overspeedThresholdKmh = 95.0,
            overspeedHysteresisKmh = 5.0,
            overspeedMinDurationMs = 4_000L,
            idleSpeedThresholdKmh = 6.0,
            idleMinDurationMs = 40_000L,
            idleGraceMs = 10_000L,
            eventMinDurationMs = 600L,
            eventCooldownMs = 2_500L,
            speedSampleIntervalMs = 4_000L,
            maxAccuracyForDistanceMeters = 40.0,
            minDistanceForDensityKm = 0.8,
            referenceAccelPer10Km = 0.6,
            referenceBrakePer10Km = 0.8,
            referenceCornerPer10Km = 0.55,
            componentLossScale = 2.5,
            idleAllowedRatio = 0.03,
            idlePenaltyScale = 350.0,
            overspeedAllowedRatio = 0.025,
            overspeedPenaltyScale = 240.0,
            weightAcceleration = 0.21,
            weightBraking = 0.26,
            weightCornering = 0.14,
            weightSpeed = 0.19,
            weightIdling = 0.20,
            fuelWeight = 0.18,
            fuelWasteRatioForZeroScore = 0.4,
            severityMildUpperRatio = 1.3,
            severityModerateUpperRatio = 1.7,
            severityMildMultiplier = 0.55,
            severityModerateMultiplier = 1.05,
            severitySevereMultiplier = 1.65,
            accelEventPoints = 5.5,
            brakeEventPoints = 6.5,
            cornerEventPoints = 3.5,
            overspeedEventPointsPerMinute = 4.5,
            idleEventPointsPerMinute = 1.5,
            vehicleMassKg = 1_500.0,
            gasolineEnergyMjPerLiter = 34.0,
            drivetrainEfficiency = 0.3,
            idleFuelLitersPerHour = 0.7,
            dragAreaCdA = 0.65,
            airDensityKgPerM3 = 1.15,
            brakeWasteFactor = 0.3,
            accelerationInefficiencyFactor = 0.2,
            corneringLossCoefficient = 0.015,
        )

        val decoded = ScoringConfigCodec.decode(ScoringConfigCodec.encode(config))

        assertEquals(config, decoded)
        assertEquals(config.hardAccelThresholdMps2, decoded.hardAccelThresholdMps2, 0.0)
        assertEquals(config.corneringLossCoefficient, decoded.corneringLossCoefficient, 0.0)
    }

    @Test
    fun `defaults survive a round trip`() {
        assertEquals(ScoringConfig(), ScoringConfigCodec.decode(ScoringConfigCodec.encode(ScoringConfig())))
    }
}
