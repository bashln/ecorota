package app.ecorota.data

import kotlinx.coroutines.flow.Flow

enum class PremiumSource {
    NONE,
    PLAY_BILLING,
    COMMUNITY_BUILD,
    DEBUG,
}

data class Entitlement(
    val isPremium: Boolean = false,
    val source: PremiumSource = PremiumSource.NONE,
) {
    fun canUse(feature: PremiumFeature): Boolean = when (feature) {
        PremiumFeature.ADVANCED_STATS,
        PremiumFeature.MULTI_VEHICLE,
        PremiumFeature.EVENT_SEVERITY,
        PremiumFeature.DATA_EXPORT,
        -> isPremium
    }
}

enum class PremiumFeature {
    ADVANCED_STATS,
    MULTI_VEHICLE,
    EVENT_SEVERITY,
    DATA_EXPORT,
}

interface EntitlementRepository {
    val entitlement: Flow<Entitlement>

    suspend fun refresh()

    suspend fun current(): Entitlement
}
