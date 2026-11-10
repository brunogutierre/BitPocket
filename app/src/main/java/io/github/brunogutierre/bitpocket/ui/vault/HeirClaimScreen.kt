package io.github.brunogutierre.bitpocket.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.ui.components.BannerSeverity
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecureScreen
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography
import io.github.brunogutierre.bitpocket.vault.HeirClaimError
import io.github.brunogutierre.bitpocket.vault.HeirClaimStage
import io.github.brunogutierre.bitpocket.vault.HeirClaimUiState
import io.github.brunogutierre.bitpocket.vault.HeirClaimViewModel

@Composable
fun HeirClaimScreen(
    viewModel: HeirClaimViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.stage) {
        HeirClaimStage.PICK_FILE -> PickFile(viewModel, state, onClose)
        HeirClaimStage.WORDS -> SecureScreen { Words(viewModel, state, onClose) }
        HeirClaimStage.SCANNING, HeirClaimStage.SENDING -> Waiting(state.stage)
        HeirClaimStage.COINS -> Coins(viewModel, state, onClose)
        HeirClaimStage.REVIEW -> Review(viewModel, state)
        HeirClaimStage.DONE -> Done(state, onClose)
    }
}

@Composable
private fun PickFile(
    viewModel: HeirClaimViewModel,
    state: HeirClaimUiState,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri
                ?.let { runCatching { context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() } }.getOrNull() }
                ?.let(viewModel::onBackupPicked)
        }
    PageScaffold(
        title = stringResource(R.string.heir_title),
        onBack = onClose,
        bottomActions = { PrimaryButton(stringResource(R.string.heir_pick_file), onClick = { pick.launch(arrayOf("*/*")) }) },
    ) {
        Text(stringResource(R.string.heir_intro), style = MaterialTheme.typography.bodyLarge)
        ErrorBanner(state.error)
    }
}

@Composable
private fun Words(
    viewModel: HeirClaimViewModel,
    state: HeirClaimUiState,
    onClose: () -> Unit,
) {
    PageScaffold(
        title = stringResource(R.string.vault_heir_title),
        onBack = onClose,
        bottomActions = { PrimaryButton(stringResource(R.string.vault_sign_continue), onClick = viewModel::submitWords) },
    ) {
        Text(stringResource(R.string.heir_words_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.wordsText,
            onValueChange = viewModel::onWordsChange,
            label = { Text(stringResource(R.string.vault_heir_title)) },
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorBanner(state.error)
    }
}

@Composable
private fun Waiting(stage: HeirClaimStage) {
    BackHandler {}
    PageScaffold(title = stringResource(R.string.heir_title), onBack = null) {
        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        Text(
            stringResource(if (stage == HeirClaimStage.SCANNING) R.string.heir_scanning else R.string.sign_sending),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Coins(
    viewModel: HeirClaimViewModel,
    state: HeirClaimUiState,
    onClose: () -> Unit,
) {
    PageScaffold(
        title = stringResource(R.string.heir_title),
        onBack = onClose,
        network = state.network,
        bottomActions = {
            PrimaryButton(stringResource(R.string.send_review), onClick = viewModel::review, enabled = state.maturedSats > 0)
            if (state.error == HeirClaimError.OFFLINE) SecondaryButton(stringResource(R.string.action_retry), onClick = viewModel::scan)
        },
    ) {
        Text(stringResource(R.string.heir_claimable), style = MaterialTheme.typography.labelLarge)
        Text(BitcoinFormat.sats(state.maturedSats), style = MaterialTheme.monoTypography.balance)
        if (state.coins.isEmpty()) Text(stringResource(R.string.heir_no_coins), style = MaterialTheme.typography.bodyMedium)
        state.coins.forEach { coin ->
            ListItem(
                headlineContent = { Text(BitcoinFormat.sats(coin.sats), style = MaterialTheme.monoTypography.amount) },
                supportingContent = {
                    Text(
                        if (coin.matured) {
                            stringResource(R.string.heir_coin_ready)
                        } else {
                            stringResource(R.string.heir_coin_waiting, coin.blocksLeft, (coin.blocksLeft + 143) / 144)
                        },
                    )
                },
            )
        }
        OutlinedTextField(
            value = state.addressText,
            onValueChange = viewModel::onAddressChange,
            label = { Text(stringResource(R.string.heir_destination)) },
            textStyle = MaterialTheme.monoTypography.small,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorBanner(state.error)
    }
}

@Composable
private fun Review(
    viewModel: HeirClaimViewModel,
    state: HeirClaimUiState,
) {
    val plan = state.plan ?: return
    BackHandler(onBack = viewModel::backToCoins)
    PageScaffold(
        title = stringResource(R.string.send_review_title),
        onBack = viewModel::backToCoins,
        network = state.network,
        bottomActions = { PrimaryButton(stringResource(R.string.heir_claim), onClick = viewModel::claim) },
    ) {
        Text(stringResource(R.string.send_review_to), style = MaterialTheme.typography.labelLarge)
        Text(BitcoinFormat.addressChunks(plan.recipient).joinToString(" "), style = MaterialTheme.monoTypography.address)
        Text(stringResource(R.string.send_review_amount), style = MaterialTheme.typography.labelLarge)
        Text(BitcoinFormat.sats(plan.amountSats), style = MaterialTheme.monoTypography.amount)
        Text(stringResource(R.string.send_review_fee, plan.feeRateSatPerVb), style = MaterialTheme.typography.labelLarge)
        Text(BitcoinFormat.sats(plan.feeSats), style = MaterialTheme.monoTypography.amount)
        ErrorBanner(state.error)
    }
}

@Composable
private fun Done(
    state: HeirClaimUiState,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    PageScaffold(
        title = stringResource(R.string.sign_done_title),
        onBack = null,
        bottomActions = { PrimaryButton(stringResource(R.string.sign_done), onClick = onClose) },
    ) {
        Text(stringResource(R.string.heir_done), style = MaterialTheme.typography.bodyLarge)
        SelectionContainer { Text(state.txid.orEmpty(), style = MaterialTheme.monoTypography.small) }
    }
}

@Composable
private fun ErrorBanner(error: HeirClaimError?) {
    val body =
        when (error ?: return) {
            HeirClaimError.INVALID_BACKUP -> R.string.heir_error_backup
            HeirClaimError.NOT_THE_HEIR -> R.string.heir_error_not_heir
            HeirClaimError.BAD_WORDS -> R.string.heir_error_words
            HeirClaimError.OFFLINE -> R.string.home_sync_failed_body
            HeirClaimError.BAD_ADDRESS -> R.string.send_error_not_an_address
            HeirClaimError.NOTHING_MATURED -> R.string.heir_error_nothing
            HeirClaimError.FAILED -> R.string.send_error_other
        }
    WarningBanner(stringResource(R.string.vault_error_title), stringResource(body), severity = BannerSeverity.ERROR)
}
