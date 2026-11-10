package io.github.brunogutierre.bitpocket.ui.settings

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.settings.DuressStage
import io.github.brunogutierre.bitpocket.settings.DuressViewModel
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PinPrompt
import io.github.brunogutierre.bitpocket.ui.components.PinPromptStatus
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner

@Composable
fun DuressScreen(
    viewModel: DuressViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.stage) {
        DuressStage.INTRO -> {
            PageScaffold(
                title = stringResource(R.string.duress_title),
                onBack = onClose,
                bottomActions = {
                    if (state.enabled) {
                        SecondaryButton(stringResource(R.string.duress_remove), onClick = viewModel::startRemoval)
                    } else {
                        PrimaryButton(stringResource(R.string.duress_set_up), onClick = viewModel::start)
                    }
                },
            ) {
                Text(stringResource(R.string.duress_intro), style = MaterialTheme.typography.bodyLarge)
                WarningBanner(stringResource(R.string.duress_limits_title), stringResource(R.string.duress_limits_body))
                Text(
                    stringResource(if (state.enabled) R.string.duress_status_on else R.string.duress_status_off),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        DuressStage.PIN, DuressStage.CHOOSE, DuressStage.CONFIRM -> {
            val (title, body) =
                when (state.stage) {
                    DuressStage.PIN -> R.string.lock_title to if (state.removing) R.string.duress_pin_remove else R.string.duress_pin_body
                    DuressStage.CHOOSE -> R.string.duress_choose_title to R.string.duress_choose_body
                    else -> R.string.pin_confirm_title to R.string.change_pin_confirm_body
                }
            val message =
                when {
                    state.weakPin -> stringResource(R.string.pin_too_weak)
                    state.samePin -> stringResource(R.string.duress_same_pin)
                    state.mismatch -> stringResource(R.string.pin_mismatch)
                    else -> stringResource(body)
                }
            PinPrompt(
                title = stringResource(title),
                body = message,
                status = PinPromptStatus(state.lockoutSeconds, state.failed, state.wrongPin),
                entered = state.entered,
                enabled = state.keysEnabled,
                errorSignal = state.errorSignal,
                onDigit = viewModel::digit,
                onBackspace = viewModel::backspace,
                onBack = onClose,
            )
        }

        DuressStage.SAVING -> {
            PageScaffold(title = stringResource(R.string.duress_title), onBack = null) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }

        DuressStage.DONE -> {
            PageScaffold(
                title = stringResource(R.string.duress_title),
                onBack = onClose,
                bottomActions = { PrimaryButton(stringResource(R.string.sign_done), onClick = onClose) },
            ) {
                Text(
                    stringResource(if (state.enabled) R.string.duress_done_on else R.string.duress_done_off),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
