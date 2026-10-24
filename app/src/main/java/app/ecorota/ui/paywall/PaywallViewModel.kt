package app.ecorota.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.ecorota.billing.PurchaseResult
import app.ecorota.data.Entitlement
import app.ecorota.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PaywallMessage {
    CANCELLED,
    PURCHASE_SUCCESS,
    PURCHASE_UNAVAILABLE,
    PURCHASE_FAILED,
    RESTORE_SUCCESS,
    RESTORE_NOTHING,
}

data class PaywallUiState(
    val loading: Boolean = true,
    val entitlement: Entitlement = Entitlement(),
    val billingAvailable: Boolean = false,
    val working: Boolean = false,
    val message: PaywallMessage? = null,
)

class PaywallViewModel(private val container: AppContainer) : ViewModel() {

    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<PaywallMessage?>(null)

    private val status = combine(working, message) { isWorking, current ->
        isWorking to current
    }

    val state: StateFlow<PaywallUiState> = combine(
        container.entitlementRepository.entitlement,
        status,
    ) { entitlement, extra ->
        val (isWorking, currentMessage) = extra
        PaywallUiState(
            loading = false,
            entitlement = entitlement,
            billingAvailable = container.billingProvider.available,
            working = isWorking,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaywallUiState(),
    )

    fun purchase(activity: Activity?) {
        message.value = null
        if (!container.billingProvider.available) {
            message.value = PaywallMessage.PURCHASE_UNAVAILABLE
            return
        }
        if (activity == null) {
            message.value = PaywallMessage.PURCHASE_FAILED
            return
        }
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            val result = runCatching { container.billingProvider.launchPurchase(activity) }
                .getOrElse { PurchaseResult.Failed(it.message.orEmpty()) }
            working.value = false
            message.value = when (result) {
                PurchaseResult.Success, PurchaseResult.AlreadyOwned -> PaywallMessage.PURCHASE_SUCCESS
                PurchaseResult.Cancelled -> PaywallMessage.CANCELLED
                PurchaseResult.Unavailable -> PaywallMessage.PURCHASE_UNAVAILABLE
                is PurchaseResult.Failed -> PaywallMessage.PURCHASE_FAILED
            }
        }
    }

    fun restorePurchase() {
        message.value = null
        if (!container.billingProvider.available) {
            message.value = PaywallMessage.PURCHASE_UNAVAILABLE
            return
        }
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            val restored = runCatching {
                container.billingProvider.restorePurchase()
            }.getOrDefault(false)
            working.value = false
            message.value = if (restored) {
                PaywallMessage.RESTORE_SUCCESS
            } else {
                PaywallMessage.RESTORE_NOTHING
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PaywallViewModel(container) }
        }
    }
}
