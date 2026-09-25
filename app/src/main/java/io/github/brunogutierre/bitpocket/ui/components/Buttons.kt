package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme
import io.github.brunogutierre.bitpocket.ui.theme.Spacing

private val ButtonMinHeight = 56.dp

/** Full-width primary action (56 dp, radius 12). While [loading], it shows a spinner and is inert. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonMinHeight),
    ) { ButtonContent(text, loading) }
}

/** Full-width secondary action with the same geometry as [PrimaryButton]. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonMinHeight),
    ) { ButtonContent(text, loading) }
}

@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
) {
    if (loading) {
        val description = stringResource(R.string.loading)
        CircularProgressIndicator(
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp).semantics { contentDescription = description },
        )
    } else {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun ButtonsPreview() {
    BitPocketTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PrimaryButton("Create wallet", onClick = {})
            PrimaryButton("Unlocking", onClick = {}, loading = true)
            PrimaryButton("Disabled", onClick = {}, enabled = false)
            SecondaryButton("Restore wallet", onClick = {})
        }
    }
}
