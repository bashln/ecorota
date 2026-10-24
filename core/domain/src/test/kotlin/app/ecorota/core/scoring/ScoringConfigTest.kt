package app.ecorota.core.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringConfigTest {

    @Test
    fun `sanitized clamps hostile values into usable ranges`() {
        val hostile = ScoringConfig(
            hardAccelThresholdMps2 = -1.0,
            overspeedThresholdKmh = 1_000.0,
            overspeedHysteresisKmh = -5.0,
            overspeedMinDurationMs = 1L,
            idleMinDurationMs = 1L,
            idleGraceMs = -10L,
            speedSampleIntervalMs = 0L,
            componentLossScale = 0.0,
            weightAcceleration = 9.0,
            fuelWeight = 5.0,
            fuelWasteRatioForZeroScore = 0.0,
            severityMildUpperRatio = 0.5,
            severityMildMultiplier = 99.0,
            severitySevereMultiplier = 99.0,
            vehicleMassKg = 10.0,
            drivetrainEfficiency = 0.0,
            airDensityKgPerM3 = 10.0,
            corneringLossCoefficient = 1.0,
        )

        val safe = hostile.sanitized()

        assertEquals(0.5, safe.hardAccelThresholdMps2, 1e-12)
        assertEquals(160.0, safe.overspeedThresholdKmh, 1e-12)
        assertEquals(0.0, safe.overspeedHysteresisKmh, 1e-12)
        assertEquals(500L, safe.overspeedMinDurationMs)
        assertEquals(5_000L, safe.idleMinDurationMs)
        assertEquals(0L, safe.idleGraceMs)
        assertEquals(1_000L, safe.speedSampleIntervalMs)
        assertEquals(0.1, safe.componentLossScale, 1e-12)
        assertEquals(1.0, safe.weightAcceleration, 1e-12)
        assertEquals(0.6, safe.fuelWeight, 1e-12)
        assertEquals(0.05, safe.fuelWasteRatioForZeroScore, 1e-12)
        assertEquals(1.0, safe.severityMildUpperRatio, 1e-12)
        assertEquals(3.0, safe.severityMildMultiplier, 1e-12)
        assertEquals(5.0, safe.severitySevereMultiplier, 1e-12)
        assertEquals(400.0, safe.vehicleMassKg, 1e-12)
        assertEquals(0.05, safe.drivetrainEfficiency, 1e-12)
        assertEquals(1.5, safe.airDensityKgPerM3, 1e-12)
        assertEquals(0.2, safe.corneringLossCoefficient, 1e-12)
    }

    @Test
    fun `sanitized clamps an inverted severity band without throwing`() {
        val safe = ScoringConfig(
            severityMildUpperRatio = 6.0,
            severityModerateUpperRatio = 0.5,
        ).sanitized()

        assertEquals(3.0, safe.severityMildUpperRatio, 1e-12)
        assertEquals(3.0, safe.severityModerateUpperRatio, 1e-12)
    }

    @Test
    fun `sanitized leaves the defaults untouched and is idempotent`() {
        val defaults = ScoringConfig()
        assertEquals(defaults, defaults.sanitized())

        val once = ScoringConfig(idleMinDurationMs = 1L).sanitized()
        assertEquals(once, once.sanitized())
    }

    @Test
    fun `normalized weights keep the configured ratio`() {
        val weights = ScoringConfig(
            weightAcceleration = 2.0,
            weightBraking = 1.0,
            weightCornering = 1.0,
            weightSpeed = 0.0,
            weightIdling = 0.0,
        ).normalizedWeights()

        assertEquals(0.5, weights.acceleration, 1e-12)
        assertEquals(0.25, weights.braking, 1e-12)
        assertEquals(0.25, weights.cornering, 1e-12)
        assertEquals(0.0, weights.speed, 1e-12)
        assertEquals(0.0, weights.idling, 1e-12)
    }
}
