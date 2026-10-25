package app.ecorota.billing

import android.app.Activity

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data object Cancelled : PurchaseResult
    data object Unavailable : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

/**
 * Billing boundary. The `play` flavor uses Google Play Billing; the `libre`
 * flavor ships a no-op implementation and unlocks premium locally.
 */
interface BillingProvider {
    val available: Boolean

    /** Starts the billing client connection. Safe to call more than once. */
    fun start()

    suspend fun launchPurchase(activity: Activity): PurchaseResult

    /**
     * One-time consumable tip. It grants nothing; Play consumes the purchase so
     * the user can tip again later.
     */
    suspend fun launchTip(activity: Activity): PurchaseResult

    /** Queries Play for an existing one-time purchase. Returns true when owned. */
    suspend fun restorePurchase(): Boolean

    fun close()
}
