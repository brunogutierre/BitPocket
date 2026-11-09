package io.github.brunogutierre.bitpocket.ui.send

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.brunogutierre.bitpocket.R
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedOpener
import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.send.AmountError
import io.github.brunogutierre.bitpocket.core.send.AmountParser
import io.github.brunogutierre.bitpocket.core.send.BlockReason
import io.github.brunogutierre.bitpocket.core.send.ConfirmReason
import io.github.brunogutierre.bitpocket.core.send.GuardrailVerdict
import io.github.brunogutierre.bitpocket.core.send.RecipientError
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.send.FeeSpeed
import io.github.brunogutierre.bitpocket.send.SendStage
import io.github.brunogutierre.bitpocket.send.SendUiState
import io.github.brunogutierre.bitpocket.send.SendViewModel
import io.github.brunogutierre.bitpocket.send.rateFor
import io.github.brunogutierre.bitpocket.ui.components.AddressFingerprintView
import io.github.brunogutierre.bitpocket.ui.components.BannerSeverity
import io.github.brunogutierre.bitpocket.ui.components.PageScaffold
import io.github.brunogutierre.bitpocket.ui.components.PrimaryButton
import io.github.brunogutierre.bitpocket.ui.components.SecondaryButton
import io.github.brunogutierre.bitpocket.ui.components.WarningBanner
import io.github.brunogutierre.bitpocket.ui.theme.Spacing
import io.github.brunogutierre.bitpocket.ui.theme.monoTypography

@Composable
fun SendScreen(
    viewModel: SendViewModel,
    biometricOpener: BiometricSeedOpener,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.stage) {
        SendStage.EDIT -> EditStage(viewModel, state, onBack)
        SendStage.SCAN -> ScanStage(onScanned = viewModel::onScanned, onCancel = viewModel::cancelScan)
        SendStage.REVIEW -> state.plan?.let { ReviewStage(viewModel, state, it) }
        SendStage.AUTH_BIOMETRIC -> BiometricStage(viewModel, biometricOpener)
        SendStage.AUTH_PIN -> PinStage(viewModel, state)
        SendStage.SENDING -> SendingStage()
        SendStage.DONE -> DoneStage(state, onDone)
    }
}

@Composable
private fun EditStage(
    viewModel: SendViewModel,
    state: SendUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    PageScaffold(
        title = stringResource(R.string.send_title),
        onBack = onBack,
        network = viewModel.network,
        bottomActions = {
            PrimaryButton(
                stringResource(R.string.send_review),
                onClick = viewModel::review,
                enabled = state.canReview,
                loading = state.planning,
            )
        },
    ) {
        OutlinedTextField(
            value = state.addressText,
            onValueChange = viewModel::onAddressChange,
            label = { Text(stringResource(R.string.send_address_label)) },
            trailingIcon = {
                // The clipboard is read only when the user asks for it.
                TextButton(onClick = { pasteText(context)?.let(viewModel::onAddressChange) }) { Text(stringResource(R.string.send_paste)) }
            },
            isError = state.addressError != null,
            supportingText = state.addressError?.let { { Text(stringResource(it.message())) } },
            textStyle = MaterialTheme.monoTypography.small,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(stringResource(R.string.send_scan), onClick = viewModel::startScan)
        OutlinedTextField(
            value = if (state.sendMax) "" else state.amountText,
            onValueChange = viewModel::onAmountChange,
            enabled = !state.sendMax,
            label = { Text(stringResource(R.string.send_amount_label)) },
            placeholder = { if (state.sendMax) Text(stringResource(R.string.send_amount_max_placeholder)) },
            suffix = { Text(stringResource(R.string.send_sats_suffix)) },
            isError = state.amountError != null,
            supportingText = state.amountError?.let { { Text(amountErrorText(it)) } },
            textStyle = MaterialTheme.monoTypography.amount,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text(stringResource(R.string.send_max), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.sendMax, onCheckedChange = viewModel::onSendMaxChange)
        }
        FeeSelector(viewModel, state)
        state.spendError?.let {
            WarningBanner(
                stringResource(R.string.send_failed_title),
                spendErrorText(it),
                severity = BannerSeverity.ERROR,
            )
        }
    }
}

@Composable
private fun FeeSelector(
    viewModel: SendViewModel,
    state: SendUiState,
) {
    Text(stringResource(R.string.send_fee_label), style = MaterialTheme.typography.titleMedium)
    val fees = state.fees
    if (fees == null) {
        if (state.feesFailed) {
            Text(stringResource(R.string.send_fees_failed), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::loadFees) { Text(stringResource(R.string.action_retry)) }
        } else {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val labels =
        mapOf(
            FeeSpeed.FAST to R.string.send_fee_fast,
            FeeSpeed.NORMAL to R.string.send_fee_normal,
            FeeSpeed.SLOW to R.string.send_fee_slow,
        )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        FeeSpeed.entries.forEachIndexed { index, speed ->
            SegmentedButton(
                selected = state.speed == speed,
                onClick = { viewModel.onSpeedChange(speed) },
                shape = SegmentedButtonDefaults.itemShape(index, FeeSpeed.entries.size),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(labels.getValue(speed)))
                    Text(stringResource(R.string.send_fee_rate, fees.rateFor(speed)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReviewStage(
    viewModel: SendViewModel,
    state: SendUiState,
    plan: SpendPlan,
) {
    BackHandler(onBack = viewModel::edit)
    PageScaffold(
        title = stringResource(R.string.send_review_title),
        onBack = viewModel::edit,
        network = viewModel.network,
        bottomActions = {
            PrimaryButton(stringResource(R.string.send_confirm), onClick = viewModel::confirm, enabled = state.canConfirm)
            SecondaryButton(stringResource(R.string.send_edit), onClick = viewModel::edit)
        },
    ) {
        ReviewRow(stringResource(R.string.send_review_to)) {
            Text(BitcoinFormat.addressChunks(plan.recipient).joinToString(" "), style = MaterialTheme.monoTypography.address)
            AddressFingerprintView(plan.recipient)
            Text(
                stringResource(R.string.send_review_fingerprint_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReviewRow(stringResource(R.string.send_review_amount)) { Amount(plan.amountSats) }
        ReviewRow(stringResource(R.string.send_review_fee, plan.feeRateSatPerVb)) { Amount(plan.feeSats) }
        ReviewRow(stringResource(R.string.send_review_total)) { Amount(plan.totalSats) }
        if (plan.changeSats > 0) {
            Text(
                stringResource(R.string.send_review_change, BitcoinFormat.sats(plan.changeSats)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GuardrailNotice(viewModel, state)
        when {
            state.broadcastFailed -> {
                WarningBanner(
                    stringResource(R.string.send_failed_title),
                    stringResource(R.string.sign_broadcast_failed),
                    severity = BannerSeverity.ERROR,
                )
            }

            state.spendError != null -> {
                WarningBanner(stringResource(R.string.send_failed_title), spendErrorText(state.spendError), severity = BannerSeverity.ERROR)
            }

            else -> {
                WarningBanner(stringResource(R.string.send_review_check_title), stringResource(R.string.send_review_check_body))
            }
        }
    }
}

/** A hard limit (red banner) or the confirmations to tick before sending. */
@Composable
private fun GuardrailNotice(
    viewModel: SendViewModel,
    state: SendUiState,
) {
    when (val verdict = state.guardrail) {
        GuardrailVerdict.Allowed -> {
            Unit
        }

        is GuardrailVerdict.Blocked -> {
            val allowed = BitcoinFormat.sats(verdict.allowedSats)
            val body =
                when (verdict.reason) {
                    BlockReason.PER_TX_LIMIT -> stringResource(R.string.guardrail_blocked_per_tx, allowed)
                    BlockReason.WINDOW_LIMIT -> stringResource(R.string.guardrail_blocked_window, allowed)
                }
            WarningBanner(stringResource(R.string.guardrail_blocked_title), body, severity = BannerSeverity.ERROR)
        }

        is GuardrailVerdict.NeedsConfirmation -> {
            state.lookalikeOf?.let { known ->
                WarningBanner(
                    stringResource(R.string.lookalike_title),
                    stringResource(R.string.lookalike_body, BitcoinFormat.addressChunks(known).joinToString(" ")),
                    severity = BannerSeverity.ERROR,
                )
            }
            verdict.reasons.sorted().forEach { reason ->
                val text =
                    when (reason) {
                        ConfirmReason.NEW_RECIPIENT -> R.string.guardrail_confirm_new_recipient
                        ConfirmReason.LARGE_SHARE -> R.string.guardrail_confirm_large_share
                        ConfirmReason.LOOKALIKE -> R.string.guardrail_confirm_lookalike
                    }
                val checked = reason in state.acknowledged
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { viewModel.acknowledge(reason, it) }),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(
    label: String,
    value: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        HorizontalDivider()
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
private fun Amount(sats: Long) {
    Column {
        Text(BitcoinFormat.sats(sats), style = MaterialTheme.monoTypography.amount)
        Text(BitcoinFormat.btc(sats), style = MaterialTheme.monoTypography.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun RecipientError.message(): Int =
    when (this) {
        RecipientError.EMPTY -> R.string.send_error_address_empty
        RecipientError.NOT_AN_ADDRESS -> R.string.send_error_not_an_address
        RecipientError.WRONG_NETWORK -> R.string.send_error_wrong_network
        RecipientError.BAD_URI -> R.string.send_error_bad_uri
    }

@Composable
private fun dustText(): String = stringResource(R.string.send_error_amount_dust, BitcoinFormat.sats(AmountParser.MIN_SATS))

@Composable
private fun amountErrorText(error: AmountError): String =
    when (error) {
        AmountError.EMPTY -> stringResource(R.string.send_error_amount_empty)
        AmountError.NOT_A_NUMBER -> stringResource(R.string.send_error_amount_number)
        AmountError.BELOW_DUST -> dustText()
    }

@Composable
private fun spendErrorText(error: SpendError): String =
    when (error) {
        SpendError.INSUFFICIENT_FUNDS -> {
            stringResource(R.string.send_error_insufficient)
        }

        SpendError.BELOW_DUST -> {
            dustText()
        }

        SpendError.INVALID_ADDRESS -> {
            stringResource(R.string.send_error_not_an_address)
        }

        SpendError.REJECTED -> {
            stringResource(R.string.send_error_rejected)
        }

        SpendError.UNSAFE_INPUT, SpendError.SIGNING_FAILED, SpendError.PLAN_MISMATCH, SpendError.WRONG_BRANCH, SpendError.OTHER -> {
            stringResource(
                R.string.send_error_other,
            )
        }
    }

private fun pasteText(context: Context): String? =
    context
        .getSystemService(ClipboardManager::class.java)
        .primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
