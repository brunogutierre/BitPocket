package io.github.brunogutierre.bitpocket.ui.history

import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import io.github.brunogutierre.bitpocket.ui.theme.extendedColors
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Signed amount (green when incoming), direction, status and date. */
@Composable
fun TransactionRow(
    item: HistoryItem,
    modifier: Modifier = Modifier,
) {
    val incoming = item.netSats > 0
    ListItem(
        headlineContent = {
            Text(
                BitcoinFormat.signedSats(item.netSats),
                style = MaterialTheme.monoTypography.amount,
                color = if (incoming) MaterialTheme.extendedColors.success else Color.Unspecified,
            )
        },
        overlineContent = { Text(stringResource(if (incoming) R.string.history_received else R.string.history_sent)) },
        supportingContent = { Text(statusText(item)) },
        modifier = modifier,
    )
}

/** "Pending" or "6 confirmations · 9 Nov 2026, 14:03". */
@Composable
fun statusText(item: HistoryItem): String {
    val status =
        if (item.isPending) {
            stringResource(R.string.history_pending)
        } else {
            pluralStringResource(R.plurals.history_confirmations, item.confirmations, item.confirmations)
        }
    val time = item.timestampSec ?: return status
    return "$status · ${formatTime(time)}"
}

private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatTime(epochSeconds: Long): String = dateTimeFormat.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))
