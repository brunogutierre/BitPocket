package io.github.brunogutierre.bitpocket.ui.vault

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.ui.components.BannerSeverity
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PinPrompt
import io.github.brunogutierre.bitpocket.ui.components.PinPromptStatus
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecureScreen
import io.github.brunogutierre.bitpocket.ui.components.SeedWordGrid
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.components.WordPicker
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.vault.VaultKeyStep
import io.github.brunogutierre.bitpocket.vault.VaultSetupError
import io.github.brunogutierre.bitpocket.vault.VaultSetupStage
import io.github.brunogutierre.bitpocket.vault.VaultSetupUiState
import io.github.brunogutierre.bitpocket.vault.VaultSetupViewModel

/** File name offered when saving the Vault backup. */
const val VAULT_BACKUP_FILE_NAME = "bitpocket-vault-backup.bpvb"

/** Writes [bytes] to a document the user picked; false if the provider refused. */
fun writeDocument(
    context: Context,
    uri: Uri,
    bytes: ByteArray,
): Boolean = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)

@Composable
fun VaultSetupScreen(
    viewModel: VaultSetupViewModel,
    importing: Boolean,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val bytes =
                uri?.let {
                    runCatching {
                        context.contentResolver.openInputStream(it)?.use { input ->
                            input.readBytes()
                        }
                    }.getOrNull()
                }
            if (bytes == null) onClose() else viewModel.startImport(bytes)
        }
    LaunchedEffect(importing) { if (importing && !state.importing) pickBackup.launch(arrayOf("*/*")) }
    when (state.stage) {
        VaultSetupStage.INTRO -> if (importing) Unit else IntroStep(viewModel, onClose)
        VaultSetupStage.TIMELOCK -> TimelockStep(viewModel, state, onClose)
        VaultSetupStage.WORDS -> SecureScreen { WordsStep(viewModel, state, onClose) }
        VaultSetupStage.VERIFY -> SecureScreen { VerifyStep(viewModel, state, onClose) }
        VaultSetupStage.PIN -> PinStep(viewModel, state, onClose)
        VaultSetupStage.SAVING -> SavingStep()
        VaultSetupStage.BACKUP -> BackupStep(viewModel, state, onClose)
        VaultSetupStage.FAILED -> FailedStep(state.error, onClose)
    }
}

@Composable
private fun IntroStep(
    viewModel: VaultSetupViewModel,
    onClose: () -> Unit,
) {
    PageScaffold(
        title = stringResource(R.string.vault_setup_title),
        onBack = onClose,
        bottomActions = { PrimaryButton(stringResource(R.string.vault_setup_start), onClick = viewModel::next) },
    ) {
        Text(stringResource(R.string.vault_intro_body), style = MaterialTheme.typography.bodyLarge)
        WarningBanner(stringResource(R.string.vault_intro_risk_title), stringResource(R.string.vault_intro_risk_body))
    }
}

@Composable
private fun TimelockStep(
    viewModel: VaultSetupViewModel,
    state: VaultSetupUiState,
    onClose: () -> Unit,
) {
    PageScaffold(
        title = stringResource(R.string.vault_timelock_title),
        onBack = onClose,
        bottomActions = { PrimaryButton(stringResource(R.string.vault_next), onClick = viewModel::next) },
    ) {
        Text(stringResource(R.string.vault_timelock_body), style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
            HeirTimelock.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(state.timelock == option, role = Role.RadioButton, onClick = { viewModel.chooseTimelock(option) })
                            .padding(vertical = Spacing.sm),
                ) {
                    RadioButton(selected = state.timelock == option, onClick = null)
                    Text(stringResource(option.label()), modifier = Modifier.padding(start = Spacing.md))
                }
            }
        }
    }
}

@Composable
private fun WordsStep(
    viewModel: VaultSetupViewModel,
    state: VaultSetupUiState,
    onClose: () -> Unit,
) {
    val (title, body) =
        when (state.keyStep) {
            VaultKeyStep.RECOVERY -> R.string.vault_recovery_title to R.string.vault_recovery_body
            VaultKeyStep.HEIR -> R.string.vault_heir_title to R.string.vault_heir_body
        }
    PageScaffold(
        title = stringResource(title),
        onBack = onClose,
        bottomActions = { PrimaryButton(stringResource(R.string.create_done), onClick = viewModel::next, enabled = state.revealed) },
    ) {
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        WarningBanner(
            stringResource(R.string.create_warning_title),
            stringResource(R.string.create_warning_body),
            severity = BannerSeverity.ERROR,
        )
        SeedWordGrid(state.words, revealed = state.revealed, onReveal = viewModel::reveal)
    }
}

@Composable
private fun VerifyStep(
    viewModel: VaultSetupViewModel,
    state: VaultSetupUiState,
    onClose: () -> Unit,
) {
    val challenge = state.challenge ?: return
    PageScaffold(title = stringResource(R.string.verify_title), onBack = onClose) {
        Text(
            stringResource(R.string.verify_step, state.challengeStep + 1, state.challengeCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.verify_prompt, challenge.position), style = MaterialTheme.typography.headlineSmall)
        WordPicker(challenge.options, state.selected, state.pickResult, viewModel::pick, errorSignal = state.pickErrorSignal)
    }
}

@Composable
private fun PinStep(
    viewModel: VaultSetupViewModel,
    state: VaultSetupUiState,
    onClose: () -> Unit,
) {
    PinPrompt(
        title = stringResource(R.string.lock_title),
        body = stringResource(if (state.importing) R.string.vault_pin_import else R.string.vault_pin_setup),
        status = PinPromptStatus(lockoutSeconds = state.lockoutSeconds, wrongPin = state.wrongPin),
        entered = state.pinEntered,
        enabled = state.pinKeysEnabled,
        errorSignal = state.pinErrorSignal,
        onDigit = viewModel::pinDigit,
        onBackspace = viewModel::pinBackspace,
        onBack = onClose,
    )
}

@Composable
private fun SavingStep() {
    BackHandler {}
    PageScaffold(title = stringResource(R.string.vault_setup_title), onBack = null) {
        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        Text(stringResource(R.string.vault_saving), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BackupStep(
    viewModel: VaultSetupViewModel,
    state: VaultSetupUiState,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val bytes = viewModel.backup
            if (uri != null && bytes != null && writeDocument(context, uri, bytes)) viewModel.onBackupExported()
        }
    BackHandler(onBack = onClose)
    PageScaffold(
        title = stringResource(if (state.importing) R.string.vault_imported_title else R.string.vault_ready_title),
        onBack = null,
        bottomActions = {
            PrimaryButton(stringResource(R.string.vault_backup_save), onClick = { save.launch(VAULT_BACKUP_FILE_NAME) })
            SecondaryButton(stringResource(R.string.sign_done), onClick = onClose)
        },
    ) {
        Text(stringResource(R.string.vault_backup_body), style = MaterialTheme.typography.bodyLarge)
        if (state.backupExported) {
            Text(
                stringResource(R.string.vault_backup_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FailedStep(
    error: VaultSetupError?,
    onClose: () -> Unit,
) {
    val body =
        when (error) {
            VaultSetupError.ALREADY_SET_UP -> R.string.vault_error_exists
            VaultSetupError.NOT_THIS_WALLET -> R.string.vault_error_not_ours
            else -> R.string.lock_failed
        }
    PageScaffold(
        title = stringResource(R.string.vault_setup_title),
        onBack = onClose,
        bottomActions = { PrimaryButton(stringResource(R.string.sign_done), onClick = onClose) },
    ) {
        WarningBanner(stringResource(R.string.vault_error_title), stringResource(body), severity = BannerSeverity.ERROR)
    }
}

fun HeirTimelock.label(): Int =
    when (this) {
        HeirTimelock.HOUR -> R.string.timelock_hour
        HeirTimelock.DAY -> R.string.timelock_day
        HeirTimelock.MONTH -> R.string.timelock_month
        HeirTimelock.YEAR -> R.string.timelock_year
    }
