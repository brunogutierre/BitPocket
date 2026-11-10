package io.github.brunogutierre.bitpocket.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.settings.GuardrailsStage
import io.github.brunogutierre.bitpocket.settings.GuardrailsUiState
import io.github.brunogutierre.bitpocket.settings.GuardrailsViewModel
import io.github.brunogutierre.bitpocket.ui.components.BannerSeverity
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PinPrompt
import io.github.brunogutierre.bitpocket.ui.components.PinPromptStatus
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

@Composable
fun GuardrailsScreen(
    viewModel: GuardrailsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.stage) {
        GuardrailsStage.EDIT, GuardrailsStage.SAVED -> {
            Editor(viewModel, state, onBack)
        }

        GuardrailsStage.PIN -> {
            PinPrompt(
                title = stringResource(R.string.lock_title),
                body = stringResource(R.string.guardrails_pin),
                status = PinPromptStatus(state.lockoutSeconds, state.failed, state.wrongPin),
                entered = state.pinEntered,
                enabled = state.pinKeysEnabled,
                errorSignal = state.pinErrorSignal,
                onDigit = viewModel::pinDigit,
                onBackspace = viewModel::pinBackspace,
                onBack = viewModel::cancelPin,
            )
        }
    }
}

@Composable
private fun Editor(
    viewModel: GuardrailsViewModel,
    state: GuardrailsUiState,
    onBack: () -> Unit,
) {
    PageScaffold(
        title = stringResource(R.string.guardrails_title),
        onBack = onBack,
        bottomActions = { PrimaryButton(stringResource(R.string.guardrails_save), onClick = viewModel::save) },
    ) {
        Text(stringResource(R.string.guardrails_intro), style = MaterialTheme.typography.bodyMedium)
        LimitField(stringResource(R.string.guardrails_per_tx), state.perTxText, state.invalidPerTx, viewModel::onPerTxChange)
        LimitField(stringResource(R.string.guardrails_window), state.windowText, state.invalidWindow, viewModel::onWindowChange)
        SwitchRow(stringResource(R.string.guardrails_confirm_new), state.confirmNewRecipients, viewModel::onConfirmNewRecipients)
        SwitchRow(stringResource(R.string.guardrails_confirm_large), state.confirmLargeShare, viewModel::onConfirmLargeShare)
        when {
            state.needsSync -> {
                WarningBanner(
                    stringResource(R.string.guardrails_sync_title),
                    stringResource(R.string.guardrails_sync_body),
                    severity = BannerSeverity.ERROR,
                )
            }

            state.scheduledAt != null -> {
                WarningBanner(
                    stringResource(R.string.guardrails_scheduled_title),
                    stringResource(R.string.guardrails_scheduled_body, state.scheduledAt),
                )
            }

            state.stage == GuardrailsStage.SAVED -> {
                Text(stringResource(R.string.guardrails_saved), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LimitField(
    label: String,
    value: String,
    invalid: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.guardrails_no_limit)) },
        suffix = { Text(stringResource(R.string.send_sats_suffix)) },
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.guardrails_invalid)) }) else null,
        textStyle = MaterialTheme.monoTypography.amount,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
