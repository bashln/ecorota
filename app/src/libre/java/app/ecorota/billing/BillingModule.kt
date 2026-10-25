package app.ecorota.billing

import android.content.Context
import app.ecorota.data.PremiumRepository

/** Community flavor: no Play Billing SDK and no purchase flow. */
fun createBillingProvider(
    context: Context,
    premiumRepository: PremiumRepository,
): BillingProvider = NoOpBillingProvider()
