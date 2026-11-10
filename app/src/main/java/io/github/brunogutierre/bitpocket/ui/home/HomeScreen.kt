package io.github.brunogutierre.bitpocket.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.vault.HeartbeatStatus
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.home.HomeViewModel
import io.github.brunogutierre.bitpocket.ui.components.BitPocketTopBar
import io.github.brunogutierre.bitpocket.ui.components.EmptyState
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.history.TransactionRow
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography
import io.github.brunogutierre.bitpocket.ui.theme.screenHorizontalPadding
import io.github.brunogutierre.bitpocket.ui.vault.HeartbeatNotice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onSettings: () -> Unit,
    onVault: () -> Unit,
    onSetUpVault: () -> Unit,
    onHealth: () -> Unit,
    onTransaction: (txid: String) -> Unit,
    onLocked: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from Send or Receive: show what changed locally (e.g. a pending send) without a sync.
    LaunchedEffect(Unit) { viewModel.reload() }
    Scaffold(
        topBar = {
            BitPocketTopBar(stringResource(R.string.home_title), viewModel.network, navigationIcon = {
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                }
            })
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = viewModel::sync,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                contentPadding = PaddingValues(horizontal = screenHorizontalPadding(), vertical = Spacing.xl),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { BalanceHeader(state.balance) }
                if (state.syncFailed) {
                    item {
                        WarningBanner(stringResource(R.string.home_sync_failed_title), stringResource(R.string.home_sync_failed_body))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        PrimaryButton(stringResource(R.string.home_receive), onClick = onReceive, modifier = Modifier.weight(1f))
                        PrimaryButton(stringResource(R.string.home_send), onClick = onSend, modifier = Modifier.weight(1f))
                    }
                }
                item { VaultCard(state.vaultBalance, onVault, onSetUpVault) }
                state.health?.let { report ->
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.health_title)) },
                            supportingContent = { Text(stringResource(R.string.health_grade, report.grade.name, report.score)) },
                            modifier =
                                Modifier
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                                    .clickable(onClick = onHealth),
                        )
                    }
                }
                state.vaultHeartbeat?.takeIf { it.status != HeartbeatStatus.OK }?.let { heartbeat ->
                    item { HeartbeatNotice(heartbeat, onRefresh = onVault) }
                }
                item { Text(stringResource(R.string.home_activity), style = MaterialTheme.typography.titleMedium) }
                if (state.history.isEmpty()) {
                    item {
                        EmptyState(
                            painterResource(R.drawable.ic_science),
                            stringResource(R.string.home_empty_title),
                            stringResource(R.string.home_empty_body),
                        )
                    }
                }
                items(state.history, key = {
                    it.txid
                }) { item -> TransactionRow(item, Modifier.clickable { onTransaction(item.txid) }, state.labels[item.txid]) }
                item {
                    SecondaryButton(stringResource(R.string.home_lock), onClick = {
                        viewModel.lock()
                        onLocked()
                    })
                }
            }
        }
    }
}

/** Total balance in sats (BTC below), and how much of it is still unconfirmed. */
@Composable
private fun BalanceHeader(balance: Balance?) {
    val unknown = stringResource(R.string.home_balance_unknown)
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                if (balance == null) contentDescription = unknown
            },
    ) {
        Text(
            stringResource(R.string.home_balance_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(balance?.let { BitcoinFormat.sats(it.totalSats) } ?: "—", style = MaterialTheme.monoTypography.balance)
        if (balance != null) {
            Text(
                BitcoinFormat.btc(balance.totalSats),
                style = MaterialTheme.monoTypography.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (balance.pendingSats != 0L) {
                Text(
                    stringResource(R.string.home_pending, BitcoinFormat.sats(balance.pendingSats)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The Vault pocket at a glance, or an invitation to set it up. */
@Composable
private fun VaultCard(
    balance: Balance?,
    onOpen: () -> Unit,
    onSetUp: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.vault_title)) },
        supportingContent = {
            Text(if (balance == null) stringResource(R.string.vault_card_set_up) else BitcoinFormat.sats(balance.totalSats))
        },
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .clickable(onClick = if (balance == null) onSetUp else onOpen),
    )
}
