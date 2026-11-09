package io.github.brunogutierre.bitpocket.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.biometric.BiometricFailure
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedResult
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.send.AmountError
import io.github.brunogutierre.bitpocket.core.send.AmountParser
import io.github.brunogutierre.bitpocket.core.send.AmountResult
import io.github.brunogutierre.bitpocket.core.send.ConfirmReason
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.GuardrailCheck
import io.github.brunogutierre.bitpocket.core.send.GuardrailVerdict
import io.github.brunogutierre.bitpocket.core.send.Lookalike
import io.github.brunogutierre.bitpocket.core.send.RecipientError
import io.github.brunogutierre.bitpocket.core.send.RecipientParser
import io.github.brunogutierre.bitpocket.core.send.RecipientResult
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.session.SeedOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.core.settings.Guardrails
import io.github.brunogutierre.bitpocket.core.settings.GuardrailsPolicy
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.security.LockoutCountdown
import io.github.brunogutierre.bitpocket.security.PinBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FeeSpeed { FAST, NORMAL, SLOW }

enum class SendStage {
    EDIT,

    /** The camera looks for a QR code with an address or bitcoin: link. */
    SCAN,
    REVIEW,

    /** The screen shows BiometricPrompt and reports back through [SendViewModel.onBiometricResult]. */
    AUTH_BIOMETRIC,
    AUTH_PIN,
    SENDING,
    DONE,
}

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
    /** How the guardrails judged [plan]. */
    val guardrail: GuardrailVerdict = GuardrailVerdict.Allowed,
    /** Confirmations the user ticked on Review. */
    val acknowledged: Set<ConfirmReason> = emptySet(),
    /** The known address the recipient looks like, when it is a possible poisoning address. */
    val lookalikeOf: String? = null,
    val pinEntered: Int = 0,
    val pinVerifying: Boolean = false,
    val wrongPin: Boolean = false,
    val pinErrorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
    val authFailed: Boolean = false,
    /** Why biometrics fell back to the PIN, if they did. */
    val biometricFailure: BiometricFailure? = null,
    /** The last send attempt could not reach the backend: nothing was sent. */
    val broadcastFailed: Boolean = false,
    val txid: String? = null,
) {
    val pinKeysEnabled: Boolean get() = !pinVerifying && lockoutSeconds == 0L

    /** Not blocked, and every confirmation the guardrails asked for is ticked. */
    val canConfirm: Boolean
        get() =
            when (val verdict = guardrail) {
                GuardrailVerdict.Allowed -> true
                is GuardrailVerdict.Blocked -> false
                is GuardrailVerdict.NeedsConfirmation -> acknowledged.containsAll(verdict.reasons)
            }

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
 * will leave the wallet, then the seed is obtained for this one signature (biometrics when set up,
 * else the PIN), the plan is signed, checked and broadcast, and the seed is wiped.
 * Inputs are validated only when the user asks to review, so typing is never interrupted.
 */
class SendViewModel(
    private val pocket: PocketWallet,
    private val walletAccess: WalletAccess,
    /** The session's biometric-sealed seed, or null when biometric signing is off. */
    val biometricSeed: ByteArray?,
    /** The session's settings, for the guardrails in force. */
    private val settings: WalletSettings = WalletSettings(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val network: SupportedNetwork = pocket.network
    private val _state = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = _state.asStateFlow()
    private val pin = PinBuffer()
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }

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

    fun startScan() = _state.update { it.copy(stage = SendStage.SCAN) }

    fun cancelScan() = _state.update { it.copy(stage = SendStage.EDIT) }

    /** A scanned QR fills the recipient (and an empty amount, for a BIP21 link with one). */
    fun onScanned(text: String) {
        if (_state.value.stage != SendStage.SCAN) return
        _state.update { it.copy(stage = SendStage.EDIT) }
        onAddressChange(text)
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
                val stats = pocket.spendingStats(Guardrails.WINDOW_BLOCKS)
                val verdict = GuardrailCheck.evaluate(GuardrailsPolicy.effective(settings, stats.tipHeight), plan, stats)
                val lookalike = Lookalike.find(plan.recipient, stats.pastRecipients + stats.ownAddresses)
                _state.update {
                    it.copy(
                        planning = false,
                        plan = plan,
                        guardrail = verdict,
                        lookalikeOf = lookalike,
                        acknowledged = emptySet(),
                        stage = SendStage.REVIEW,
                    )
                }
            } catch (e: SpendException) {
                _state.update { it.copy(planning = false, spendError = e.error) }
            }
        }
    }

    /** From REVIEW back to EDIT, keeping what was typed. */
    fun edit() = _state.update { it.copy(stage = SendStage.EDIT, plan = null, broadcastFailed = false, spendError = null) }

    fun acknowledge(
        reason: ConfirmReason,
        checked: Boolean,
    ) = _state.update { it.copy(acknowledged = if (checked) it.acknowledged + reason else it.acknowledged - reason) }

    /** "Confirm and send" on REVIEW. */
    fun confirm() {
        if (_state.value.plan == null || !_state.value.canConfirm) return
        val stage = if (biometricSeed != null) SendStage.AUTH_BIOMETRIC else SendStage.AUTH_PIN
        _state.update { it.copy(stage = stage, broadcastFailed = false, spendError = null, biometricFailure = null, authFailed = false) }
    }

    /** Leaves authentication without signing. */
    fun cancelAuth() {
        pin.clear()
        _state.update { it.copy(stage = SendStage.REVIEW, pinEntered = 0, wrongPin = false) }
    }

    fun onBiometricResult(result: BiometricSeedResult) {
        if (_state.value.stage != SendStage.AUTH_BIOMETRIC) return
        when (result) {
            is BiometricSeedResult.Opened -> sign(result.entropy)
            is BiometricSeedResult.Failed -> _state.update { it.copy(stage = SendStage.AUTH_PIN, biometricFailure = result.reason) }
        }
    }

    fun pinDigit(digit: Char) {
        if (_state.value.stage != SendStage.AUTH_PIN || !_state.value.pinKeysEnabled) return
        pin.append(digit)
        _state.update { it.copy(pinEntered = pin.size, wrongPin = false, authFailed = false) }
        if (pin.isFull) verifyPin()
    }

    fun pinBackspace() {
        pin.removeLast()
        _state.update { it.copy(pinEntered = pin.size) }
    }

    private fun verifyPin() {
        val attempt = pin.toCharArray()
        pin.clear()
        _state.update { it.copy(pinVerifying = true) }
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { walletAccess.seedForSigning(attempt) }
                } finally {
                    attempt.wipe()
                }
            _state.update { it.copy(pinVerifying = false, pinEntered = 0) }
            when (outcome) {
                is SeedOutcome.Seed -> {
                    sign(outcome.entropy)
                }

                is SeedOutcome.WrongPin -> {
                    _state.update { it.copy(wrongPin = true, pinErrorSignal = it.pinErrorSignal + 1) }
                    countdown.start(outcome.lockout)
                }

                is SeedOutcome.LockedOut -> {
                    countdown.start(outcome.remaining)
                }

                SeedOutcome.Failed -> {
                    _state.update { it.copy(authFailed = true) }
                }
            }
        }
    }

    /** Signs, checks and broadcasts; the seed is wiped whatever happens. */
    private fun sign(entropy: ByteArray) {
        val plan = _state.value.plan ?: return entropy.wipe()
        _state.update { it.copy(stage = SendStage.SENDING) }
        viewModelScope.launch {
            try {
                val txid = pocket.send(plan, entropy)
                _state.update { it.copy(stage = SendStage.DONE, txid = txid) }
            } catch (e: SpendException) {
                _state.update { it.copy(stage = SendStage.REVIEW, spendError = e.error) }
            } catch (e: ChainUnavailableException) {
                _state.update { it.copy(stage = SendStage.REVIEW, broadcastFailed = true) }
            } finally {
                entropy.wipe()
            }
        }
    }

    override fun onCleared() = pin.clear()
}
