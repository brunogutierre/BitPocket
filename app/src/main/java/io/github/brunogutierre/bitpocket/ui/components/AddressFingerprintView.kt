package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.fingerprint.AddressFingerprint
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

/** Identicon + three words of [address]; read by TalkBack as "Address fingerprint: w1 w2 w3". */
@Composable
fun AddressFingerprintView(
    address: String,
    modifier: Modifier = Modifier,
) {
    val fingerprint = remember(address) { AddressFingerprint.of(address) }
    val description = stringResource(R.string.fingerprint_description, fingerprint.words.joinToString(" "))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .padding(Spacing.md)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        Identicon(fingerprint)
        Column {
            Text(
                stringResource(R.string.fingerprint_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(fingerprint.words.joinToString(" "), style = MaterialTheme.monoTypography.amount)
        }
    }
}

@Composable
private fun Identicon(fingerprint: AddressFingerprint) {
    // Mid lightness reads on both light and dark surfaces.
    val color = Color.hsl(fingerprint.hue.toFloat(), saturation = 0.65f, lightness = 0.5f)
    Canvas(Modifier.size(40.dp)) {
        val cell = size.minDimension / AddressFingerprint.SIZE
        for (row in 0 until AddressFingerprint.SIZE) {
            for (column in 0 until AddressFingerprint.SIZE) {
                if (fingerprint[row, column]) {
                    drawRect(color, topLeft = Offset(column * cell, row * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddressFingerprintPreview() {
    BitPocketTheme { AddressFingerprintView("tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj") }
}
