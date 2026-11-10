package io.github.brunogutierre.bitpocket.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.history.TransactionRow
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography
import io.github.brunogutierre.bitpocket.vault.VaultViewModel

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onReceive: () -> Unit,
    onMoveIn: () -> Unit,
    onSend: () -> Unit,
    onTransaction: (txid: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.reload() }
    PageScaffold(
        title = stringResource(R.string.vault_title),
        onBack = onBack,
        network = viewModel.network,
        bottomActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PrimaryButton(stringResource(R.string.home_receive), onClick = onReceive, modifier = Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.home_send), onClick = onSend, modifier = Modifier.weight(1f))
            }
            SecondaryButton(stringResource(R.string.vault_move_in), onClick = onMoveIn)
        },
    ) {
        Text(
            stringResource(R.string.home_balance_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(state.balance?.let { BitcoinFormat.sats(it.totalSats) } ?: "—", style = MaterialTheme.monoTypography.balance)
        Text(
            stringResource(R.string.vault_policy_summary, stringResource(viewModel.config.timelock.label())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.home_activity), style = MaterialTheme.typography.titleMedium)
        if (state.history.isEmpty()) {
            Text(stringResource(R.string.vault_empty), style = MaterialTheme.typography.bodyMedium)
        }
        state.history.forEach { item -> TransactionRow(item, Modifier.clickable { onTransaction(item.txid) }) }
    }
}
