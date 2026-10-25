package app.ecorota.billing

import android.content.Context
import app.ecorota.data.PremiumRepository

/** Play flavor: the real one-time purchase and tip client. */
fun createBillingProvider(
    context: Context,
    premiumRepository: PremiumRepository,
): BillingProvider = PlayBillingProvider(context, premiumRepository)
