package io.github.brunogutierre.bitpocket.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.send.AmountError
import io.github.brunogutierre.bitpocket.core.send.AmountParser
import io.github.brunogutierre.bitpocket.core.send.AmountResult
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.RecipientError
import io.github.brunogutierre.bitpocket.core.send.RecipientParser
import io.github.brunogutierre.bitpocket.core.send.RecipientResult
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeeSpeed { FAST, NORMAL, SLOW }

enum class SendStage { EDIT, REVIEW }

data class SendUiState(
    val stage: SendStage = SendStage.EDIT,
    val addressText: String = "",
    val amountText: String = "",
    val sendMax: Boolean = false,
    val fees: FeeOptions? = null,
    val feesFailed: Boolean = false,
    val speed: FeeSpeed = FeeSpeed.NORMAL,
    val addressError: RecipientError? = null,
    val amountError: AmountError? = null,
    val spendError: SpendError? = null,
    val planning: Boolean = false,
    val plan: SpendPlan? = null,
) {
    val feeRate: Long? get() = fees?.rateFor(speed)
    val canReview: Boolean get() = fees != null && !planning
}

fun FeeOptions.rateFor(speed: FeeSpeed): Long =
    when (speed) {
        FeeSpeed.FAST -> fast
        FeeSpeed.NORMAL -> normal
        FeeSpeed.SLOW -> slow
    }

/**
 * Send flow: EDIT (recipient, amount, fee speed) builds a [SpendPlan], REVIEW shows exactly what
 * will leave the wallet. Inputs are validated only when the user asks to review, so typing is
 * never interrupted by errors.
 */
class SendViewModel(
    private val pocket: PocketWallet,
) : ViewModel() {
    val network: SupportedNetwork = pocket.network
    private val _state = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    init {
        loadFees()
    }

    fun loadFees() {
        _state.update { it.copy(feesFailed = false) }
        viewModelScope.launch {
            try {
                val fees = pocket.feeRates()
                _state.update { it.copy(fees = fees) }
            } catch (e: ChainUnavailableException) {
                _state.update { it.copy(feesFailed = true) }
            }
        }
    }

    /** A pasted BIP21 URI may carry an amount: it fills an empty amount field. */
    fun onAddressChange(text: String) {
        val uriAmount = (RecipientParser.parse(text, network) as? RecipientResult.Valid)?.recipient?.amountSats
        _state.update {
            it.copy(
                addressText = text,
                addressError = null,
                spendError = null,
                amountText = if (uriAmount != null && it.amountText.isBlank() && !it.sendMax) uriAmount.toString() else it.amountText,
            )
        }
    }

    fun onAmountChange(text: String) = _state.update { it.copy(amountText = text, amountError = null, spendError = null) }

    fun onSendMaxChange(max: Boolean) = _state.update { it.copy(sendMax = max, amountError = null, spendError = null) }

    fun onSpeedChange(speed: FeeSpeed) = _state.update { it.copy(speed = speed, spendError = null) }

    fun review() {
        val current = _state.value
        val feeRate = current.feeRate ?: return
        if (current.planning) return
        val recipient = RecipientParser.parse(current.addressText, network)
        val amount = if (current.sendMax) null else AmountParser.parseSats(current.amountText)
        val addressError = (recipient as? RecipientResult.Invalid)?.error
        val amountError = (amount as? AmountResult.Invalid)?.error
        if (addressError != null || amountError != null) {
            _state.update { it.copy(addressError = addressError, amountError = amountError) }
            return
        }
        val address = (recipient as RecipientResult.Valid).recipient.address
        val sendAmount = if (amount is AmountResult.Valid) SendAmount.Exact(amount.sats) else SendAmount.Max
        _state.update { it.copy(planning = true, spendError = null) }
        viewModelScope.launch {
            try {
                val plan = pocket.planSpend(SpendRequest(address, sendAmount, feeRate))
                _state.update { it.copy(planning = false, plan = plan, stage = SendStage.REVIEW) }
            } catch (e: SpendException) {
                _state.update { it.copy(planning = false, spendError = e.error) }
            }
        }
    }

    /** From REVIEW back to EDIT, keeping what was typed. */
    fun edit() = _state.update { it.copy(stage = SendStage.EDIT, plan = null) }
}
