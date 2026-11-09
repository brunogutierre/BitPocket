package io.github.brunogutierre.bitpocket.ui.send

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.biometric.BiometricFailure
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedOpener
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedResult
import io.github.brunogutierre.bitpocket.send.SendUiState
import io.github.brunogutierre.bitpocket.send.SendViewModel
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PinPad
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
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
    BackHandler(onBack = viewModel::cancelAuth)
    PageScaffold(title = stringResource(R.string.sign_pin_title), onBack = viewModel::cancelAuth, network = viewModel.network) {
        val message =
            when {
                state.lockoutSeconds > 0 -> stringResource(R.string.lock_locked_out, formatCountdown(state.lockoutSeconds))
                state.authFailed -> stringResource(R.string.lock_failed)
                state.wrongPin -> stringResource(R.string.lock_wrong)
                state.biometricFailure == BiometricFailure.INVALIDATED -> stringResource(R.string.sign_biometric_invalidated)
                else -> stringResource(R.string.sign_pin_body)
            }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        PinPad(
            enteredCount = state.pinEntered,
            onDigit = viewModel::pinDigit,
            onBackspace = viewModel::pinBackspace,
            enabled = state.pinKeysEnabled,
            errorSignal = state.pinErrorSignal,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
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

private fun formatCountdown(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)
