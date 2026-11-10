package io.github.brunogutierre.bitpocket.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.biometric.BiometricFailure
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedResult
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.seed.RestoreResult
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
import io.github.brunogutierre.bitpocket.core.vault.VaultKeys
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.Coin
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.core.wallet.VaultPort
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

    /** Vault spends: the recovery words are typed to co-sign (ADR 0007). */
    RECOVERY,

    /** Coin control: choose which coins pay. */
    COINS,
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
    val recoveryText: String = "",
    /** Coins the user can pick (confirmed, not frozen), and the picked ones; empty = automatic. */
    val availableCoins: List<Coin> = emptyList(),
    val selectedCoins: Set<String> = emptySet(),
    /** The typed recovery words are not a valid phrase, or not this Vault's recovery key. */
    val recoveryError: Boolean = false,
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
    /** An address to start with, e.g. the Vault's for "Move to Vault". */
    private val prefill: (suspend () -> String?)? = null,
    /** Set when spending from the Vault: its RECOVERY public key, to check the typed words. */
    private val vaultRecoveryKey: String? = null,
    /** Start with "send everything" on (the Vault refresh sends all coins to itself). */
    sendAll: Boolean = false,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val network: SupportedNetwork = pocket.network
    private val _state = MutableStateFlow(SendUiState(sendMax = sendAll))
    val state: StateFlow<SendUiState> = _state.asStateFlow()
    private val pin = PinBuffer()
    private var recoverySeed: ByteArray? = null
    private val vault: VaultPort? get() = if (vaultRecoveryKey != null) pocket as VaultPort else null
    val isVault: Boolean get() = vaultRecoveryKey != null
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }

    init {
        loadFees()
        prefill?.let { viewModelScope.launch { it()?.let(::onAddressChange) } }
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

    fun chooseCoins() {
        viewModelScope.launch {
            val usable = pocket.coins().filter { !it.frozen && it.confirmations > 0 }
            _state.update { it.copy(stage = SendStage.COINS, availableCoins = usable) }
        }
    }

    fun toggleCoin(outpoint: String) =
        _state.update {
            it.copy(
                selectedCoins =
                    if (outpoint in
                        it.selectedCoins
                    ) {
                        it.selectedCoins - outpoint
                    } else {
                        it.selectedCoins + outpoint
                    },
                spendError = null,
            )
        }

    /** Back to the form with the selection (empty = let the wallet choose). */
    fun doneChoosingCoins() = _state.update { it.copy(stage = SendStage.EDIT) }

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
                val request = SpendRequest(address, sendAmount, feeRate, current.selectedCoins.toList().ifEmpty { null })
                val plan = vault?.planVaultSpend(request) ?: pocket.planSpend(request)
                val stats = pocket.spendingStats(Guardrails.WINDOW_BLOCKS)
                // The Vault already needs the offline recovery key: guardrails apply to Spending only.
                val guardrails =
                    if (isVault) {
                        Guardrails(
                            confirmNewRecipients = false,
                            confirmLargeShare = false,
                        )
                    } else {
                        GuardrailsPolicy.effective(settings, stats.tipHeight)
                    }
                val verdict = GuardrailCheck.evaluate(guardrails, plan, stats)
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
        if (isVault && recoverySeed == null) {
            _state.update { it.copy(stage = SendStage.RECOVERY, broadcastFailed = false, spendError = null, recoveryError = false) }
            return
        }
        val stage = if (biometricSeed != null) SendStage.AUTH_BIOMETRIC else SendStage.AUTH_PIN
        _state.update { it.copy(stage = stage, broadcastFailed = false, spendError = null, biometricFailure = null, authFailed = false) }
    }

    fun onRecoveryTextChange(text: String) = _state.update { it.copy(recoveryText = text, recoveryError = false) }

    /** Checks the typed words against the Vault's RECOVERY key, keeps the seed, then asks for the phone's. */
    fun submitRecovery() {
        val words =
            _state.value.recoveryText
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
        val seed = (MnemonicService().restore(words) as? RestoreResult.Valid)?.entropy
        if (seed == null || VaultKeys.publicKey(seed, network) != vaultRecoveryKey) {
            seed?.wipe()
            _state.update { it.copy(recoveryError = true) }
            return
        }
        recoverySeed = seed
        // The words leave the state as soon as they are checked.
        _state.update { it.copy(recoveryText = "") }
        confirm()
    }

    fun cancelRecovery() = _state.update { it.copy(stage = SendStage.REVIEW, recoveryText = "", recoveryError = false) }

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
                val recovery = recoverySeed
                val vaultPort = vault
                val txid =
                    if (vaultPort != null &&
                        recovery != null
                    ) {
                        vaultPort.sendFromVault(plan, entropy, recovery)
                    } else {
                        pocket.send(plan, entropy)
                    }
                _state.update { it.copy(stage = SendStage.DONE, txid = txid) }
            } catch (e: SpendException) {
                _state.update { it.copy(stage = SendStage.REVIEW, spendError = e.error) }
            } catch (e: ChainUnavailableException) {
                _state.update { it.copy(stage = SendStage.REVIEW, broadcastFailed = true) }
            } finally {
                entropy.wipe()
                wipeRecovery()
            }
        }
    }

    private fun wipeRecovery() {
        recoverySeed?.wipe()
        recoverySeed = null
    }

    override fun onCleared() {
        pin.clear()
        wipeRecovery()
    }
}
