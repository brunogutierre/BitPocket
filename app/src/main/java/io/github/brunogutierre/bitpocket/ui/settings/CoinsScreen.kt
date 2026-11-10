package io.github.brunogutierre.bitpocket.ui.settings

import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.wallet.Coin
import io.github.brunogutierre.bitpocket.settings.CoinsViewModel
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

@Composable
fun CoinsScreen(
    viewModel: CoinsViewModel,
    onBack: () -> Unit,
) {
    val coins by viewModel.coins.collectAsStateWithLifecycle()
    PageScaffold(title = stringResource(R.string.coins_title), onBack = onBack, network = viewModel.network) {
        Text(stringResource(R.string.coins_body), style = MaterialTheme.typography.bodyMedium)
        if (coins.isEmpty()) Text(stringResource(R.string.coins_empty), style = MaterialTheme.typography.bodyMedium)
        coins.forEach { coin ->
            ListItem(
                headlineContent = { Text(BitcoinFormat.sats(coin.sats), style = MaterialTheme.monoTypography.amount) },
                supportingContent = { Text(coinStatus(coin)) },
                trailingContent = { Switch(checked = coin.frozen, onCheckedChange = { viewModel.setFrozen(coin, it) }) },
            )
        }
    }
}

/** "Frozen · 3 confirmations · 0a1b…:0". */
@Composable
fun coinStatus(coin: Coin): String {
    val confirmations =
        if (coin.confirmations == 0) {
            stringResource(R.string.history_pending)
        } else {
            pluralStringResource(R.plurals.history_confirmations, coin.confirmations, coin.confirmations)
        }
    val id = coin.outpoint.take(8) + "…:" + coin.outpoint.substringAfterLast(':')
    val parts = listOfNotNull(stringResource(R.string.coins_frozen).takeIf { coin.frozen }, confirmations, id)
    return parts.joinToString(" · ")
}
