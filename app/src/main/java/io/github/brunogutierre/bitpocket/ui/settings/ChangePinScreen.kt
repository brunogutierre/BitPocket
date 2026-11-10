package io.github.brunogutierre.bitpocket.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.settings.ChangePinStage
import io.github.brunogutierre.bitpocket.settings.ChangePinViewModel
import io.github.brunogutierre.bitpocket.ui.components.PinPrompt
import io.github.brunogutierre.bitpocket.ui.components.PinPromptStatus

@Composable
fun ChangePinScreen(
    viewModel: ChangePinViewModel,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.stage) { if (state.stage == ChangePinStage.DONE) onChanged() }
    val (title, body) =
        when (state.stage) {
            ChangePinStage.CURRENT -> R.string.change_pin_current to R.string.change_pin_current_body
            ChangePinStage.NEW, ChangePinStage.DONE -> R.string.pin_choose_title to R.string.pin_choose_body
            ChangePinStage.CONFIRM -> R.string.pin_confirm_title to R.string.change_pin_confirm_body
        }
    val bodyText =
        when {
            state.weakPin -> stringResource(R.string.pin_too_weak)
            state.mismatch -> stringResource(R.string.pin_mismatch)
            else -> stringResource(body)
        }
    PinPrompt(
        title = stringResource(title),
        body = bodyText,
        status = PinPromptStatus(state.lockoutSeconds, state.failed, state.wrongPin),
        entered = state.entered,
        enabled = state.keysEnabled,
        errorSignal = state.errorSignal,
        onDigit = viewModel::digit,
        onBackspace = viewModel::backspace,
        onBack = onBack,
    )
}
