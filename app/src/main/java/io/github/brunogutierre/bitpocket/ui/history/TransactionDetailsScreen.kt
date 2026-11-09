package io.github.brunogutierre.bitpocket.ui.history

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import io.github.brunogutierre.bitpocket.history.TransactionDetailsUiState
import io.github.brunogutierre.bitpocket.history.TransactionDetailsViewModel
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.extendedColors
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

@Composable
fun TransactionDetailsScreen(
    viewModel: TransactionDetailsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PageScaffold(
        title = stringResource(R.string.tx_title),
        onBack = onBack,
        network = viewModel.network,
        bottomActions = {
            SecondaryButton(stringResource(R.string.tx_open_explorer), onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, viewModel.explorerUrl().toUri()))
                } catch (e: ActivityNotFoundException) {
                    // No browser installed: nothing to open.
                }
            })
        },
    ) {
        when (val current = state) {
            TransactionDetailsUiState.Loading -> {
                Unit
            }

            TransactionDetailsUiState.NotFound -> {
                Text(stringResource(R.string.tx_not_found), style = MaterialTheme.typography.bodyLarge)
            }

            is TransactionDetailsUiState.Loaded -> {
                Details(current.item)
            }
        }
        DetailRow(stringResource(R.string.tx_id)) {
            SelectionContainer { Text(viewModel.txid, style = MaterialTheme.monoTypography.small) }
        }
    }
}

@Composable
private fun Details(item: HistoryItem) {
    val incoming = item.netSats > 0
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            stringResource(if (incoming) R.string.history_received else R.string.history_sent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            BitcoinFormat.signedSats(item.netSats),
            style = MaterialTheme.monoTypography.balance,
            color = if (incoming) MaterialTheme.extendedColors.success else Color.Unspecified,
        )
    }
    DetailRow(stringResource(R.string.tx_status)) { Text(statusText(item)) }
    item.feeSats?.let { fee ->
        val rate = item.feeRateSatPerVb?.let { stringResource(R.string.tx_fee_rate, it) }.orEmpty()
        DetailRow(stringResource(R.string.tx_fee)) { Text(BitcoinFormat.sats(fee) + rate, style = MaterialTheme.monoTypography.amount) }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        HorizontalDivider()
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}
