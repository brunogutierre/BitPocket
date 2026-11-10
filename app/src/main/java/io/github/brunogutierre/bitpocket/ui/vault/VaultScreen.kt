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
import io.github.brunogutierre.bitpocket.core.vault.Heartbeat
import io.github.brunogutierre.bitpocket.core.vault.HeartbeatStatus
import io.github.brunogutierre.bitpocket.ui.components.BannerSeverity
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
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
    onRefresh: () -> Unit,
    onCoins: () -> Unit,
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
            SecondaryButton(stringResource(R.string.coins_title), onClick = onCoins)
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
        state.heartbeat?.let { HeartbeatNotice(it, onRefresh) }
        Text(stringResource(R.string.home_activity), style = MaterialTheme.typography.titleMedium)
        if (state.history.isEmpty()) {
            Text(stringResource(R.string.vault_empty), style = MaterialTheme.typography.bodyMedium)
        }
        state.history.forEach { item -> TransactionRow(item, Modifier.clickable { onTransaction(item.txid) }) }
    }
}

/** Heir timelock status with a Refresh action once there are Vault coins. */
@Composable
fun HeartbeatNotice(
    heartbeat: Heartbeat,
    onRefresh: (() -> Unit)?,
) {
    val left = heartbeat.blocksLeft ?: return
    val body = stringResource(R.string.heartbeat_body, left, approximateDays(left))
    when (heartbeat.status) {
        HeartbeatStatus.OK -> {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HeartbeatStatus.DUE_SOON -> {
            WarningBanner(stringResource(R.string.heartbeat_due_title), body)
        }

        HeartbeatStatus.EXPIRED -> {
            WarningBanner(
                stringResource(R.string.heartbeat_expired_title),
                stringResource(R.string.heartbeat_expired_body),
                severity = BannerSeverity.ERROR,
            )
        }
    }
    onRefresh?.let { SecondaryButton(stringResource(R.string.heartbeat_refresh), onClick = it) }
}

/** About 144 blocks a day on Signet. */
private fun approximateDays(blocks: Int): Int = (blocks + 143) / 144
