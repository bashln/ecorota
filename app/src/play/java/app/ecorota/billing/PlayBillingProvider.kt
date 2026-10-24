package app.ecorota.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import app.ecorota.data.PremiumRepository
import app.ecorota.data.PremiumSource
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Google Play implementation of the one-time premium unlock.
 *
 * Entitlement is verified on the device only: the purchase must come back from
 * Play as [Purchase.PurchaseState.PURCHASED] for [PREMIUM_PRODUCT_ID] before it
 * is acknowledged and written to [PremiumRepository]. There is no server-side
 * verification, which matches an offline, account-free app; Play still validates
 * the signed purchase on the device, and acknowledging it is what prevents the
 * automatic refund of an unacknowledged purchase.
 *
 * A purchase in [Purchase.PurchaseState.PENDING] (for example a cash payment
 * awaiting approval) is never granted. It becomes grantable through
 * [restorePurchase] once Play reports it as purchased.
 *
 * The [Activity] passed to [launchPurchase] is used only to show the Play
 * purchase sheet and is never stored.
 */
class PlayBillingProvider(
    context: Context,
    private val premiumRepository: PremiumRepository,
) : BillingProvider {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectMutex = Mutex()
    private val waiterLock = Any()

    @Volatile
    private var billingClient: BillingClient? = null

    @Volatile
    private var purchaseWaiter: CompletableDeferred<PurchaseUpdate>? = null

    @Volatile
    private var closed = false

    override val available: Boolean = true

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        val waiter = synchronized(waiterLock) { purchaseWaiter }
        // A purchase update that arrives when nobody is waiting is picked up by
        // the next restorePurchase(), which queries Play directly.
        waiter?.complete(PurchaseUpdate(result, purchases.orEmpty()))
    }

    override fun start() {
        if (closed) return
        scope.launch {
            try {
                ensureConnected()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: BillingFailure) {
                Log.w(TAG, "billing connection failed: ${responseCodeName(failure.responseCode)}")
            } catch (error: RuntimeException) {
                Log.w(TAG, "billing client could not connect", error)
            }
        }
    }

    override suspend fun launchPurchase(activity: Activity): PurchaseResult {
        val client = try {
            ensureConnected()
        } catch (failure: BillingFailure) {
            return failure.toPurchaseResult()
        }

        val product = try {
            queryProduct(client, PREMIUM_PRODUCT_ID)
        } catch (failure: BillingFailure) {
            return failure.toPurchaseResult()
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()

        val waiter = CompletableDeferred<PurchaseUpdate>()
        synchronized(waiterLock) { purchaseWaiter = waiter }

        val launchResult = try {
            client.launchBillingFlow(activity, flowParams)
        } catch (error: RuntimeException) {
            clearWaiter(waiter)
            Log.w(TAG, "billing flow could not start", error)
            return PurchaseResult.Failed(responseCodeName(BillingClient.BillingResponseCode.ERROR))
        }

        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            clearWaiter(waiter)
            return launchFailure(client, launchResult.responseCode)
        }

        val update = try {
            withTimeoutOrNull(PURCHASE_TIMEOUT_MS) { waiter.await() }
        } finally {
            clearWaiter(waiter)
        }

        if (update == null) {
            return PurchaseResult.Failed(PURCHASE_TIMEOUT)
        }
        if (update.result.responseCode != BillingClient.BillingResponseCode.OK) {
            return launchFailure(client, update.result.responseCode)
        }

        val purchase = update.purchases.firstOrNull { PREMIUM_PRODUCT_ID in it.products }
            ?: return PurchaseResult.Failed(NO_PURCHASE_PRODUCT)

        return when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> grant(client, purchase)
            Purchase.PurchaseState.PENDING -> PurchaseResult.Failed(PENDING_PURCHASE)
            else -> PurchaseResult.Failed(responseCodeName(BillingClient.BillingResponseCode.ERROR))
        }
    }

    override suspend fun launchTip(activity: Activity): PurchaseResult {
        val client = try {
            ensureConnected()
        } catch (failure: BillingFailure) {
            return failure.toPurchaseResult()
        }

        val product = try {
            queryProduct(client, TIP_PRODUCT_ID)
        } catch (failure: BillingFailure) {
            return failure.toPurchaseResult()
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()

        val waiter = CompletableDeferred<PurchaseUpdate>()
        synchronized(waiterLock) { purchaseWaiter = waiter }

        val launchResult = try {
            client.launchBillingFlow(activity, flowParams)
        } catch (error: RuntimeException) {
            clearWaiter(waiter)
            Log.w(TAG, "tip billing flow could not start", error)
            return PurchaseResult.Failed(responseCodeName(BillingClient.BillingResponseCode.ERROR))
        }

        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            clearWaiter(waiter)
            return tipLaunchFailure(client, launchResult.responseCode)
        }

        val update = try {
            withTimeoutOrNull(PURCHASE_TIMEOUT_MS) { waiter.await() }
        } finally {
            clearWaiter(waiter)
        }

        if (update == null) return PurchaseResult.Failed(PURCHASE_TIMEOUT)
        if (update.result.responseCode != BillingClient.BillingResponseCode.OK) {
            return tipLaunchFailure(client, update.result.responseCode)
        }

        val purchase = update.purchases.firstOrNull { TIP_PRODUCT_ID in it.products }
            ?: return PurchaseResult.Failed(NO_PURCHASE_PRODUCT)
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return PurchaseResult.Failed(PENDING_PURCHASE)
        }
        val consumeCode = consume(client, purchase.purchaseToken)
        return if (consumeCode == BillingClient.BillingResponseCode.OK) {
            PurchaseResult.Success
        } else {
            PurchaseResult.Failed(responseCodeName(consumeCode))
        }
    }

    override suspend fun restorePurchase(): Boolean {        if (closed) return false

        val client = try {
            ensureConnected()
        } catch (failure: BillingFailure) {
            Log.w(TAG, "restore failed: ${responseCodeName(failure.responseCode)}")
            return false
        }

        val purchases = try {
            queryInAppPurchases(client)
        } catch (failure: BillingFailure) {
            Log.w(TAG, "restore query failed: ${responseCodeName(failure.responseCode)}")
            return false
        }

        val purchase = purchases.firstOrNull {
            PREMIUM_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        } ?: return false

        return grant(client, purchase) is PurchaseResult.Success
    }

    override fun close() {
        closed = true
        val client = billingClient
        billingClient = null
        val waiter = synchronized(waiterLock) {
            purchaseWaiter.also { purchaseWaiter = null }
        }
        waiter?.complete(
            PurchaseUpdate(
                BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                    .build(),
                emptyList(),
            ),
        )
        client?.endConnection()
        scope.cancel()
    }

    private fun buildClient(): BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    private suspend fun ensureConnected(): BillingClient = connectMutex.withLock {
        if (closed) throw BillingFailure(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)

        val client = billingClient ?: buildClient().also { billingClient = it }
        if (client.isReady) return@withLock client

        val setup = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine<BillingResult> { continuation ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (continuation.isActive) continuation.resume(result)
                        }

                        override fun onBillingServiceDisconnected() {
                            // Next ensureConnected() retries startConnection().
                        }
                    },
                )
            }
        } ?: throw BillingFailure(LOCAL_TIMEOUT_CODE)

        if (setup.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingFailure(setup.responseCode)
        }
        client
    }

    private suspend fun queryProduct(client: BillingClient, productId: String): ProductDetails {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        val response = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { continuation ->
                client.queryProductDetailsAsync(params) { result, details ->
                    if (continuation.isActive) continuation.resume(result to details)
                }
            }
        } ?: throw BillingFailure(LOCAL_TIMEOUT_CODE)

        val result = response.first
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingFailure(result.responseCode)
        }
        return response.second.firstOrNull { it.productId == productId }
            ?: throw BillingFailure(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
    }

    private suspend fun queryInAppPurchases(client: BillingClient): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val response = withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { continuation ->
                client.queryPurchasesAsync(params) { result, purchases ->
                    if (continuation.isActive) continuation.resume(result to purchases)
                }
            }
        } ?: throw BillingFailure(LOCAL_TIMEOUT_CODE)

        val result = response.first
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            throw BillingFailure(result.responseCode)
        }
        return response.second
    }

    private suspend fun acknowledge(client: BillingClient, purchaseToken: String): Int {        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        return withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            suspendCancellableCoroutine<Int> { continuation ->
                client.acknowledgePurchase(params) { result ->
                    if (continuation.isActive) continuation.resume(result.responseCode)
                }
            }
        } ?: LOCAL_TIMEOUT_CODE
    }

    /** Consumes a tip so the same product can be bought again later. */
    private suspend fun consume(client: BillingClient, purchaseToken: String): Int {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        return withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            suspendCancellableCoroutine<Int> { continuation ->
                client.consumeAsync(params) { result, _ ->
                    if (continuation.isActive) continuation.resume(result.responseCode)
                }
            }
        } ?: LOCAL_TIMEOUT_CODE
    }

    /**
     * A tip is consumable. If Play reports it as already owned, the previous
     * attempt never finished consuming it; finish that now and treat the tip as
     * delivered rather than charging again.
     */
    private suspend fun consumeOwnedTips(client: BillingClient): Boolean {
        val purchase = try {
            queryInAppPurchases(client).firstOrNull {
                TIP_PRODUCT_ID in it.products &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
        } catch (failure: BillingFailure) {
            Log.w(TAG, "owned tip query failed: ${responseCodeName(failure.responseCode)}")
            null
        } ?: return false
        return consume(client, purchase.purchaseToken) == BillingClient.BillingResponseCode.OK
    }

    private suspend fun tipLaunchFailure(
        client: BillingClient,
        responseCode: Int,
    ): PurchaseResult = when (responseCode) {
        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            if (consumeOwnedTips(client)) PurchaseResult.Success else PurchaseResult.AlreadyOwned
        else -> BillingFailure(responseCode).toPurchaseResult()
    }

    /** Acknowledges (when needed) and then grants the entitlement. */
    private suspend fun grant(client: BillingClient, purchase: Purchase): PurchaseResult {
        // Grant first: the purchase is already paid and Play-verified. An
        // acknowledgement failure is retried by restorePurchase (including at
        // app start), while withholding the entitlement would punish the buyer.
        premiumRepository.setPremium(true, PremiumSource.PLAY_BILLING)
        if (!purchase.isAcknowledged) {
            val code = acknowledge(client, purchase.purchaseToken)
            if (code != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed, will retry: ${responseCodeName(code)}")
            }
        }
        return PurchaseResult.Success
    }

    /**
     * Play reports [BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED] without
     * the purchase, so query it back: the entitlement is granted, and an
     * unacknowledged purchase is acknowledged while there is a chance to.
     */    private suspend fun grantOwnedPurchase(client: BillingClient) {
        val purchase = try {
            queryInAppPurchases(client).firstOrNull {
                PREMIUM_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
        } catch (failure: BillingFailure) {
            Log.w(TAG, "owned purchase query failed: ${responseCodeName(failure.responseCode)}")
            null
        }

        if (purchase != null && !purchase.isAcknowledged) {
            val code = acknowledge(client, purchase.purchaseToken)
            if (code != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${responseCodeName(code)}")
            }
        }

        premiumRepository.setPremium(true, PremiumSource.PLAY_BILLING)
    }

    private suspend fun launchFailure(client: BillingClient, responseCode: Int): PurchaseResult =
        when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                grantOwnedPurchase(client)
                PurchaseResult.AlreadyOwned
            }
            else -> BillingFailure(responseCode).toPurchaseResult()
        }

    private fun clearWaiter(waiter: CompletableDeferred<PurchaseUpdate>) {
        synchronized(waiterLock) {
            if (purchaseWaiter === waiter) purchaseWaiter = null
        }
    }

    private class BillingFailure(val responseCode: Int) : Exception()

    private data class PurchaseUpdate(
        val result: BillingResult,
        val purchases: List<Purchase>,
    )

    companion object {
        const val PREMIUM_PRODUCT_ID = "premium_unlock"
        const val TIP_PRODUCT_ID = "tip_coffee"

        private const val TAG = "PlayBillingProvider"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val QUERY_TIMEOUT_MS = 10_000L
        private const val PURCHASE_TIMEOUT_MS = 60_000L
        private const val PENDING_PURCHASE = "PURCHASE_PENDING"
        private const val NO_PURCHASE_PRODUCT = "PURCHASE_PRODUCT_MISSING"
        private const val PURCHASE_TIMEOUT = "PURCHASE_TIMEOUT"

        /**
         * Play 7 no longer returns SERVICE_TIMEOUT; the code is only used here to
         * name the timeouts this provider enforces itself.
         */
        private const val LOCAL_TIMEOUT_CODE = -3

        private val UNAVAILABLE_CODES = setOf(
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            LOCAL_TIMEOUT_CODE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        )

        private fun BillingFailure.toPurchaseResult(): PurchaseResult =
            if (responseCode in UNAVAILABLE_CODES) {
                PurchaseResult.Unavailable
            } else {
                PurchaseResult.Failed(responseCodeName(responseCode))
            }

        private fun responseCodeName(code: Int): String = when (code) {
            BillingClient.BillingResponseCode.OK -> "OK"
            BillingClient.BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
            BillingClient.BillingResponseCode.ERROR -> "ERROR"
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
            BillingClient.BillingResponseCode.NETWORK_ERROR -> "NETWORK_ERROR"
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> "SERVICE_DISCONNECTED"
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
            LOCAL_TIMEOUT_CODE -> "SERVICE_TIMEOUT"
            else -> "ERROR_$code"
        }
    }
}
