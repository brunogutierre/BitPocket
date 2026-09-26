package io.github.brunogutierre.bitpocket.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.onboarding.RestoreAction
import io.github.brunogutierre.bitpocket.onboarding.RestoreError
import io.github.brunogutierre.bitpocket.onboarding.RestoreSeedViewModel
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

// The password keyboard type keeps IMEs from learning or suggesting recovery words (Compose does
// not expose IME_FLAG_NO_PERSONALIZED_LEARNING); suggestions come from the BIP39 list instead.
private val RecoveryWordKeyboard =
    KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Next,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestoreSeedScreen(
    viewModel: RestoreSeedViewModel,
    onBack: () -> Unit,
    onRestored: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.restored.collect { onRestored() } }
    OnboardingScaffold(
        title = stringResource(R.string.restore_title),
        onBack = onBack,
        bottomActions = {
            PrimaryButton(
                stringResource(R.string.restore_submit),
                onClick = { viewModel.onAction(RestoreAction.Submit) },
                enabled = state.complete,
            )
        },
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(12, 24).forEachIndexed { index, count ->
                SegmentedButton(
                    selected = state.wordCount == count,
                    onClick = { viewModel.onAction(RestoreAction.SetWordCount(count)) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(pluralStringResource(R.plurals.restore_word_count, count, count)) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.words.forEachIndexed { index, word ->
                AssistChip(onClick = {}, label = { Text("${index + 1} $word", style = MaterialTheme.monoTypography.small) })
            }
        }
        if (!state.complete) {
            OutlinedTextField(
                value = state.input,
                onValueChange = { viewModel.onAction(RestoreAction.InputChanged(it)) },
                label = { Text(stringResource(R.string.restore_word_label, state.words.size + 1, state.wordCount)) },
                singleLine = true,
                textStyle = MaterialTheme.monoTypography.amount,
                keyboardOptions = RecoveryWordKeyboard,
                keyboardActions =
                    KeyboardActions(
                        onNext = { state.suggestions.firstOrNull()?.let { viewModel.onAction(RestoreAction.AcceptWord(it)) } },
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = { viewModel.onAction(RestoreAction.AcceptWord(suggestion)) },
                        label = { Text(suggestion, style = MaterialTheme.monoTypography.amount) },
                    )
                }
            }
        }
        if (state.words.isNotEmpty()) {
            TextButton(onClick = { viewModel.onAction(RestoreAction.RemoveLast) }) { Text(stringResource(R.string.restore_remove_last)) }
        }
        state.error?.let { error ->
            val message =
                when (error) {
                    RestoreError.InvalidChecksum -> stringResource(R.string.restore_error_checksum)
                    is RestoreError.UnknownWords -> stringResource(R.string.restore_error_unknown, error.positions.joinToString())
                }
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
