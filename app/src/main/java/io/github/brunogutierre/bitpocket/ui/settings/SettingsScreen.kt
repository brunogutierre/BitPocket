package io.github.brunogutierre.bitpocket.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.biometric.PromptBiometricVault
import io.github.brunogutierre.bitpocket.biometric.SeedCipher
import io.github.brunogutierre.bitpocket.settings.PinAction
import io.github.brunogutierre.bitpocket.settings.SettingsUiState
import io.github.brunogutierre.bitpocket.settings.SettingsViewModel
import io.github.brunogutierre.bitpocket.ui.components.BannerSeverity
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PinPrompt
import io.github.brunogutierre.bitpocket.ui.components.PinPromptStatus
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecureScreen
import io.github.brunogutierre.bitpocket.ui.components.SeedWordGrid
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    seedCipher: SeedCipher,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onLock: () -> Unit,
    onWiped: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.wiped) { if (state.wiped) onWiped() }
    val phrase = state.phrase
    val action = state.action
    when {
        phrase != null -> RecoveryPhrase(phrase, viewModel::closePhrase)
        action != null -> PinStep(viewModel, state, action, seedCipher)
        else -> SettingsList(viewModel, state, onBack, onChangePin, onLock)
    }
}

@Composable
private fun SettingsList(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onLock: () -> Unit,
) {
    val context = LocalContext.current
    val version =
        remember {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        }
    PageScaffold(title = stringResource(R.string.settings_title), onBack = onBack, network = viewModel.network) {
        Column {
            SectionTitle(stringResource(R.string.settings_security))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_biometric)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (state.biometricAvailable) R.string.settings_biometric_body else R.string.settings_biometric_unavailable,
                        ),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.biometricOn,
                        onCheckedChange = viewModel::setBiometricSigning,
                        enabled = state.biometricAvailable || state.biometricOn,
                    )
                },
            )
            Entry(
                stringResource(R.string.settings_show_phrase),
                stringResource(R.string.settings_show_phrase_body),
                viewModel::showRecoveryPhrase,
            )
            Entry(stringResource(R.string.settings_change_pin), null, onChangePin)
            Entry(stringResource(R.string.home_lock), stringResource(R.string.settings_lock_body), onLock)
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_network)) },
                supportingContent = { Text(viewModel.network.displayName) },
            )
            ListItem(headlineContent = { Text(stringResource(R.string.settings_version)) }, supportingContent = { Text(version) })
            HorizontalDivider()
            Entry(
                stringResource(R.string.settings_wipe),
                stringResource(R.string.settings_wipe_body),
                viewModel::requestWipe,
                danger = true,
            )
        }
        if (state.biometricSetupFailed) {
            WarningBanner(stringResource(R.string.settings_biometric_failed_title), stringResource(R.string.settings_biometric_failed_body))
        }
    }
    if (state.confirmingWipe) {
        AlertDialog(
            onDismissRequest = viewModel::dismissWipe,
            title = { Text(stringResource(R.string.settings_wipe_confirm_title)) },
            text = { Text(stringResource(R.string.settings_wipe_confirm_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmWipe) { Text(stringResource(R.string.settings_wipe)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissWipe) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PinStep(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    action: PinAction,
    seedCipher: SeedCipher,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.settings_biometric_prompt)
    val cancel = stringResource(R.string.action_cancel)
    LaunchedEffect(state.awaitingSeal) {
        if (state.awaitingSeal && activity != null) viewModel.seal(PromptBiometricVault(activity, seedCipher, title, cancel))
    }
    val body =
        when (action) {
            PinAction.SHOW_PHRASE -> R.string.settings_pin_phrase
            PinAction.ENABLE_BIOMETRICS -> R.string.settings_pin_biometric_on
            PinAction.DISABLE_BIOMETRICS -> R.string.settings_pin_biometric_off
            PinAction.WIPE -> R.string.settings_pin_wipe
        }
    PinPrompt(
        title = stringResource(R.string.lock_title),
        body = stringResource(body),
        status = PinPromptStatus(state.lockoutSeconds, state.failed, state.wrongPin),
        entered = state.pinEntered,
        enabled = state.pinKeysEnabled,
        errorSignal = state.pinErrorSignal,
        onDigit = viewModel::pinDigit,
        onBackspace = viewModel::pinBackspace,
        onBack = viewModel::cancelPin,
        network = viewModel.network,
    )
}

/** The recovery words, behind FLAG_SECURE and masked until revealed. */
@Composable
private fun RecoveryPhrase(
    words: List<String>,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var revealed by remember { mutableStateOf(false) }
    SecureScreen {
        PageScaffold(
            title = stringResource(R.string.create_title),
            onBack = onClose,
            bottomActions = { PrimaryButton(stringResource(R.string.sign_done), onClick = onClose) },
        ) {
            WarningBanner(
                stringResource(R.string.create_warning_title),
                stringResource(R.string.create_warning_body),
                severity = BannerSeverity.ERROR,
            )
            SeedWordGrid(words, revealed = revealed, onReveal = { revealed = true })
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Entry(
    title: String,
    body: String?,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = body?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
