package app.ecorota.billing

import android.app.Activity

/**
 * Billing boundary for the community build, which ships no Play Billing SDK.
 *
 * Premium is unlocked locally by the app, so this provider never owns the
 * entitlement and only reports that there is nothing to buy.
 */
class NoOpBillingProvider : BillingProvider {

    override val available: Boolean = false

    override fun start() = Unit

    override suspend fun launchPurchase(activity: Activity): PurchaseResult =
        PurchaseResult.Unavailable

    override suspend fun launchTip(activity: Activity): PurchaseResult =
        PurchaseResult.Unavailable

    override suspend fun restorePurchase(): Boolean = false

    override fun close() = Unit
}
