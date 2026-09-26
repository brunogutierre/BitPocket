package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

/**
 * Numbered recovery words in 2 columns (1 column above 150% font scale). Until [revealed], the
 * words are not rendered at all (masked and blurred), so a glance or screenshot shows nothing.
 */
@Composable
fun SeedWordGrid(
    words: List<String>,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = if (LocalDensity.current.fontScale > 1.5f) 1 else 2
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        val hidden = stringResource(R.string.seed_hidden)
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier =
                Modifier
                    .then(if (revealed) Modifier else Modifier.blur(8.dp).clearAndSetSemantics { contentDescription = hidden }),
        ) {
            words.withIndex().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { (index, word) -> WordCell(index + 1, if (revealed) word else "•••••", Modifier.weight(1f)) }
                    if (row.size < columns) Box(Modifier.weight(1f))
                }
            }
        }
        if (!revealed) SecondaryButton(stringResource(R.string.seed_reveal), onClick = onReveal, modifier = Modifier.width(200.dp))
    }
}

@Composable
private fun WordCell(
    position: Int,
    word: String,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.seed_word_position, position) + ", $word"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            "$position",
            style = MaterialTheme.monoTypography.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
        Text(word, style = MaterialTheme.monoTypography.amount)
    }
}

@Preview(showBackground = true)
@Composable
private fun SeedWordGridPreview() {
    val words = "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" ")
    BitPocketTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            SeedWordGrid(words, revealed = true, onReveal = {})
            SeedWordGrid(words, revealed = false, onReveal = {})
        }
    }
}
