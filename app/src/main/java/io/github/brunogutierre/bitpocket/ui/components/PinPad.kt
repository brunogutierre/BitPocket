package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme
import io.github.brunogutierre.bitpocket.ui.theme.Spacing

const val PIN_LENGTH = 6

private val KeySize = 72.dp
private val DotSize = 12.dp

/**
 * PIN entry: [PIN_LENGTH] progress dots above a 3x4 keypad. It never holds or announces the
 * digits: callers keep them, TalkBack only hears "n of 6 digits entered", and every key behaves
 * the same whatever the PIN. [errorSignal] replays a shake when it changes.
 */
@Composable
fun PinPad(
    enteredCount: Int,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorSignal: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        modifier = modifier,
    ) {
        PinDots(enteredCount, Modifier.shake(errorSignal))
        val rows = listOf("123", "456", "789")
        rows.forEach { row ->
            KeyRow {
                row.forEach { digit ->
                    PinKey(enabled, onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onDigit(digit)
                    }) { Text(digit.toString(), style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
        KeyRow {
            Spacer(Modifier.size(KeySize))
            PinKey(enabled, onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onDigit('0')
            }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
            PinKey(enabled, onClick = onBackspace, background = Color.Transparent) {
                Icon(painterResource(R.drawable.ic_backspace), contentDescription = stringResource(R.string.pin_delete))
            }
        }
    }
}

@Composable
private fun PinDots(
    enteredCount: Int,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(R.plurals.pin_progress, enteredCount, enteredCount, PIN_LENGTH)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        repeat(PIN_LENGTH) { index ->
            val filled = index < enteredCount
            Box(
                Modifier
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

@Composable
private fun KeyRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) { content() }
}

@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(KeySize)
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, onClick = onClick),
    ) { content() }
}

@Preview(showBackground = true)
@Composable
private fun PinPadPreview() {
    BitPocketTheme { PinPad(enteredCount = 3, onDigit = {}, onBackspace = {}) }
}
