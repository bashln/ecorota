package app.ecorota.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ecorota.R
import app.ecorota.data.PremiumSource
import app.ecorota.di.AppContainer
import app.ecorota.ui.components.LoadingState
import app.ecorota.ui.util.findActivity

@Composable
fun PaywallRoute(container: AppContainer, onClose: () -> Unit) {
    val viewModel: PaywallViewModel = viewModel(
        factory = PaywallViewModel.factory(container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    PaywallScreen(
        state = state,
        onClose = onClose,
        onPurchase = { viewModel.purchase(activity) },
        onRestore = viewModel::restorePurchase,
    )
}

@Composable
private fun PaywallScreen(
    state: PaywallUiState,
    onClose: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    if (state.loading) {
        LoadingState(label = stringResource(R.string.common_loading))
        return
    }
    val entitlement = state.entitlement
    val communityBuild = entitlement.source == PremiumSource.COMMUNITY_BUILD
    val unlocked = entitlement.isPremium

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.paywall_close),
                    )
                }
                Text(
                    text = stringResource(R.string.paywall_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.paywall_headline),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.paywall_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Benefit(
                                title = stringResource(R.string.paywall_benefit_charts_title),
                                body = stringResource(R.string.paywall_benefit_charts_body),
                            )
                            Benefit(
                                title = stringResource(R.string.paywall_benefit_vehicles_title),
                                body = stringResource(R.string.paywall_benefit_vehicles_body),
                            )
                            Benefit(
                                title = stringResource(R.string.paywall_benefit_events_title),
                                body = stringResource(R.string.paywall_benefit_events_body),
                            )
                            Benefit(
                                title = stringResource(R.string.paywall_benefit_export_title),
                                body = stringResource(R.string.paywall_benefit_export_body),
                            )
                        }
                    }
                }

                if (state.message != null) {
                    item {
                        Surface(
                            color = if (state.message == PaywallMessage.PURCHASE_SUCCESS ||
                                state.message == PaywallMessage.RESTORE_SUCCESS
                            ) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    when (state.message) {
                                        PaywallMessage.CANCELLED -> R.string.paywall_msg_cancelled
                                        PaywallMessage.PURCHASE_SUCCESS ->
                                            R.string.paywall_msg_purchase_success
                                        PaywallMessage.PURCHASE_UNAVAILABLE ->
                                            R.string.paywall_msg_unavailable
                                        PaywallMessage.PURCHASE_FAILED ->
                                            R.string.paywall_msg_purchase_failed
                                        PaywallMessage.RESTORE_SUCCESS ->
                                            R.string.paywall_msg_restore_success
                                        PaywallMessage.RESTORE_NOTHING ->
                                            R.string.paywall_msg_restore_nothing
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (communityBuild) {
                            Text(
                                text = stringResource(R.string.paywall_community_build),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (unlocked) {
                            Text(
                                text = stringResource(R.string.paywall_active),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.paywall_one_time),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.paywall_price_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            if (state.billingAvailable) {
                                Button(
                                    onClick = onPurchase,
                                    enabled = !state.working,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.paywall_buy))
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onRestore,
                                    enabled = !state.working,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.settings_premium_restore))
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.settings_premium_unavailable),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (state.working) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.paywall_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun Benefit(title: String, body: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
