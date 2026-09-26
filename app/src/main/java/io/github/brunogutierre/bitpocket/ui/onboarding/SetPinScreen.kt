package io.github.brunogutierre.bitpocket.ui.onboarding

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.onboarding.PinStage
import io.github.brunogutierre.bitpocket.onboarding.SetPinViewModel
import io.github.brunogutierre.bitpocket.ui.components.PinPad

@Composable
fun SetPinScreen(
    viewModel: SetPinViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.created.collect { onCreated() } }
    val title = if (state.stage == PinStage.CHOOSE) R.string.pin_choose_title else R.string.pin_confirm_title
    OnboardingScaffold(title = stringResource(title), onBack = if (state.saving) null else onBack) {
        val message =
            when {
                state.saving -> R.string.pin_creating
                state.saveFailed -> R.string.pin_save_failed
                state.mismatch -> R.string.pin_mismatch
                state.weakPin -> R.string.pin_too_weak
                else -> R.string.pin_choose_body
            }
        val isError = state.saveFailed || state.mismatch || state.weakPin
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.saving) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            PinPad(
                enteredCount = state.entered,
                onDigit = viewModel::digit,
                onBackspace = viewModel::backspace,
                errorSignal = state.errorSignal,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
