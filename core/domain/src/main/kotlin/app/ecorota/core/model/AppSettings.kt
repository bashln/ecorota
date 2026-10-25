package app.ecorota.core.model

import app.ecorota.core.scoring.ScoringConfig

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * User preferences. Trip data itself never leaves the device; these flags only
 * control local behavior.
 */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val autoDetectionEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val consumptionUnit: ConsumptionUnit = ConsumptionUnit.LITERS_PER_100_KM,
    val currency: String = "BRL",
    val allowMapTileDownloads: Boolean = false,
    val keepScreenOnDuringTrip: Boolean = true,
    /** 0 keeps trips forever; any other value is the retention window in days. */
    val dataRetentionDays: Int = 0,
    val scoringConfig: ScoringConfig = ScoringConfig(),
)
