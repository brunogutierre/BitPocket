package io.github.brunogutierre.bitpocket.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.wallet.ServerCheck
import io.github.brunogutierre.bitpocket.settings.ServerStage
import io.github.brunogutierre.bitpocket.settings.ServerViewModel
import io.github.brunogutierre.bitpocket.ui.components.BannerSeverity
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PinPrompt
import io.github.brunogutierre.bitpocket.ui.components.PinPromptStatus
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

@Composable
fun ServerScreen(
    viewModel: ServerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.stage == ServerStage.PIN) {
        PinPrompt(
            title = stringResource(R.string.lock_title),
            body = stringResource(R.string.server_pin),
            status = PinPromptStatus(state.lockoutSeconds, state.failed, state.wrongPin),
            entered = state.pinEntered,
            enabled = state.pinKeysEnabled,
            errorSignal = state.pinErrorSignal,
            onDigit = viewModel::pinDigit,
            onBackspace = viewModel::pinBackspace,
            onBack = viewModel::cancelPin,
        )
        return
    }
    PageScaffold(
        title = stringResource(R.string.server_title),
        onBack = onBack,
        network = viewModel.network,
        bottomActions = {
            PrimaryButton(
                stringResource(R.string.server_check_save),
                onClick = viewModel::save,
                loading =
                    state.stage == ServerStage.CHECKING,
            )
            SecondaryButton(stringResource(R.string.server_use_default), onClick = viewModel::useDefault)
        },
    ) {
        Text(stringResource(R.string.server_body, viewModel.network.esploraBaseUrl), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.urlText,
            onValueChange = viewModel::onUrlChange,
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(viewModel.network.esploraBaseUrl) },
            textStyle = MaterialTheme.monoTypography.small,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        state.check?.let { check ->
            val body =
                when (check) {
                    ServerCheck.NOT_HTTPS -> R.string.server_not_https
                    ServerCheck.WRONG_NETWORK -> R.string.server_wrong_network
                    ServerCheck.UNREACHABLE -> R.string.server_unreachable
                    ServerCheck.OK -> null
                }
            body?.let { WarningBanner(stringResource(R.string.server_rejected), stringResource(it), severity = BannerSeverity.ERROR) }
        }
        if (state.stage == ServerStage.SAVED) Text(stringResource(R.string.server_saved), style = MaterialTheme.typography.bodyMedium)
    }
}
