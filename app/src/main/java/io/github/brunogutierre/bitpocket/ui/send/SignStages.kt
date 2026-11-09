package io.github.brunogutierre.bitpocket.ui.send

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.biometric.BiometricFailure
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedOpener
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedResult
import io.github.brunogutierre.bitpocket.send.SendUiState
import io.github.brunogutierre.bitpocket.send.SendViewModel
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PinPrompt
import io.github.brunogutierre.bitpocket.ui.components.PinPromptStatus
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecureScreen
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.extendedColors
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

/** Runs BiometricPrompt once; any failure falls back to the PIN inside the view model. */
@Composable
internal fun BiometricStage(
    viewModel: SendViewModel,
    opener: BiometricSeedOpener,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.sign_biometric_title)
    val usePin = stringResource(R.string.sign_use_pin)
    LaunchedEffect(Unit) {
        val blob = viewModel.biometricSeed
        val result =
            if (blob == null || activity == null) {
                BiometricSeedResult.Failed(BiometricFailure.CANCELLED)
            } else {
                opener.open(activity, blob, title, usePin)
            }
        viewModel.onBiometricResult(result)
    }
    BackHandler(onBack = viewModel::cancelAuth)
    WaitingPage(stringResource(R.string.sign_biometric_title))
}

@Composable
internal fun PinStage(
    viewModel: SendViewModel,
    state: SendUiState,
) {
    val body =
        if (state.biometricFailure == BiometricFailure.INVALIDATED) {
            stringResource(R.string.sign_biometric_invalidated)
        } else {
            stringResource(R.string.sign_pin_body)
        }
    PinPrompt(
        title = stringResource(R.string.sign_pin_title),
        body = body,
        status = PinPromptStatus(state.lockoutSeconds, state.authFailed, state.wrongPin),
        entered = state.pinEntered,
        enabled = state.pinKeysEnabled,
        errorSignal = state.pinErrorSignal,
        onDigit = viewModel::pinDigit,
        onBackspace = viewModel::pinBackspace,
        onBack = viewModel::cancelAuth,
        network = viewModel.network,
    )
}

@Composable
internal fun SendingStage() {
    // Signing and broadcasting cannot be interrupted halfway.
    BackHandler {}
    WaitingPage(stringResource(R.string.sign_sending))
}

@Composable
internal fun DoneStage(
    state: SendUiState,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    PageScaffold(
        title = stringResource(R.string.sign_done_title),
        onBack = null,
        bottomActions = { PrimaryButton(stringResource(R.string.sign_done), onClick = onDone) },
    ) {
        Text(
            stringResource(R.string.sign_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.extendedColors.success,
        )
        Text(
            stringResource(R.string.tx_id),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer { Text(state.txid.orEmpty(), style = MaterialTheme.monoTypography.small) }
    }
}

@Composable
private fun WaitingPage(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Vault spends: type the 12 recovery words to co-sign; checked against the Vault's RECOVERY key. */
@Composable
internal fun RecoveryStage(
    viewModel: SendViewModel,
    state: SendUiState,
) {
    BackHandler(onBack = viewModel::cancelRecovery)
    SecureScreen {
        PageScaffold(
            title = stringResource(R.string.vault_sign_title),
            onBack = viewModel::cancelRecovery,
            network = viewModel.network,
            bottomActions = { PrimaryButton(stringResource(R.string.vault_sign_continue), onClick = viewModel::submitRecovery) },
        ) {
            Text(stringResource(R.string.vault_sign_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.recoveryText,
                onValueChange = viewModel::onRecoveryTextChange,
                label = { Text(stringResource(R.string.vault_recovery_title)) },
                isError = state.recoveryError,
                supportingText = if (state.recoveryError) ({ Text(stringResource(R.string.vault_sign_wrong_words)) }) else null,
                // Password type: keyboards must not learn or suggest recovery words.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
