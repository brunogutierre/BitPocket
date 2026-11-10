package io.github.brunogutierre.bitpocket.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.home.HomeViewModel
import io.github.brunogutierre.bitpocket.ui.components.BitPocketTopBar
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography
import io.github.brunogutierre.bitpocket.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onReceive: () -> Unit,
    onLocked: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { BitPocketTopBar(stringResource(R.string.home_title), viewModel.network) }) { innerPadding ->
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
                item { PrimaryButton(stringResource(R.string.home_receive), onClick = onReceive) }
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
