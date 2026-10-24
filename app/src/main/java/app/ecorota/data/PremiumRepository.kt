package app.ecorota.data

/**
 * Premium entitlement source. The billing layer writes to it after a verified
 * Play purchase; the community build writes a local grant at startup.
 */
interface PremiumRepository : EntitlementRepository {
    suspend fun setPremium(isPremium: Boolean, source: PremiumSource)
}
