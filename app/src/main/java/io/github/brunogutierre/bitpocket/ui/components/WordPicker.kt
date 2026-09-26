package io.github.brunogutierre.bitpocket.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.brunogutierre.bitpocket.ui.theme.BitPocketTheme
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.extendedColors
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

/** Result of the last pick, which colors the picked option. */
enum class PickResult { NONE, CORRECT, WRONG }

/**
 * Word options for a verification challenge (2 per row). The [selected] option turns
 * successContainer or errorContainer (with a shake) according to [result].
 */
@Composable
fun WordPicker(
    options: List<String>,
    selected: String?,
    result: PickResult,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorSignal: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = modifier.selectableGroup()) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { option ->
                    val isSelected = option == selected
                    val color =
                        when {
                            !isSelected || result == PickResult.NONE -> MaterialTheme.colorScheme.surfaceContainerHigh
                            result == PickResult.CORRECT -> MaterialTheme.extendedColors.successContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    Surface(
                        color = color,
                        shape = MaterialTheme.shapes.medium,
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (isSelected && result == PickResult.WRONG) Modifier.shake(errorSignal) else Modifier)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(option) }),
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.monoTypography.amount,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .heightIn(min = Spacing.minTouchTarget)
                                    .padding(Spacing.md),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WordPickerPreview() {
    BitPocketTheme {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WordPicker(listOf("thank", "zoo", "about", "legal"), selected = "thank", result = PickResult.CORRECT, onPick = {})
            WordPicker(listOf("thank", "zoo", "about", "legal"), selected = "zoo", result = PickResult.WRONG, onPick = {})
        }
    }
}
