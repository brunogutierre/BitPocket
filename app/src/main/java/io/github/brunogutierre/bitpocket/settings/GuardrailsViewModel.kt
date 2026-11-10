package io.github.brunogutierre.bitpocket.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.core.settings.Guardrails
import io.github.brunogutierre.bitpocket.core.settings.GuardrailsPolicy
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.wallet.WalletPort
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

enum class GuardrailsStage { EDIT, PIN, SAVED }

data class GuardrailsUiState(
    val stage: GuardrailsStage = GuardrailsStage.EDIT,
    /** Empty = no limit. */
    val perTxText: String = "",
    val windowText: String = "",
    val confirmNewRecipients: Boolean = true,
    val confirmLargeShare: Boolean = true,
    val invalidPerTx: Boolean = false,
    val invalidWindow: Boolean = false,
    /** A loosening needs a synced chain tip. */
    val needsSync: Boolean = false,
    /** Set when the change (or part of it) waits until this block. */
    val scheduledAt: Int? = null,
    val pinEntered: Int = 0,
    val pinVerifying: Boolean = false,
    val wrongPin: Boolean = false,
    val pinErrorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
    val failed: Boolean = false,
) {
    val pinKeysEnabled: Boolean get() = !pinVerifying && lockoutSeconds == 0L
}

/** Edits the spending guardrails; saving needs the PIN, and loosening waits 144 blocks (ADR 0006). */
class GuardrailsViewModel(
    private val walletAccess: WalletAccess,
    private val session: Session,
    private val wallet: WalletPort,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(GuardrailsUiState())
    val state: StateFlow<GuardrailsUiState> = _state.asStateFlow()
    private val pin = PinBuffer()
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }
    private var pending: WalletSettings? = null

    init {
        viewModelScope.launch {
            val tip = wallet.spendingStats(Guardrails.WINDOW_BLOCKS).tipHeight
            val settings = session.wallet?.settings ?: WalletSettings()
            val inForce = GuardrailsPolicy.effective(settings, tip)
            // Show what was last requested: a pending loosening if any, else what is in force.
            val shown = settings.scheduledGuardrails?.takeIf { tip < it.activatesAtHeight }?.guardrails ?: inForce
            _state.update {
                it.copy(
                    perTxText = shown.perTxLimitSats?.toString().orEmpty(),
                    windowText = shown.windowLimitSats?.toString().orEmpty(),
                    confirmNewRecipients = shown.confirmNewRecipients,
                    confirmLargeShare = shown.confirmLargeShare,
                    scheduledAt = settings.scheduledGuardrails?.activatesAtHeight?.takeIf { at -> tip < at },
                )
            }
        }
    }

    fun onPerTxChange(text: String) = _state.update { it.copy(perTxText = text, invalidPerTx = false, needsSync = false) }

    fun onWindowChange(text: String) = _state.update { it.copy(windowText = text, invalidWindow = false, needsSync = false) }

    fun onConfirmNewRecipients(on: Boolean) = _state.update { it.copy(confirmNewRecipients = on, needsSync = false) }

    fun onConfirmLargeShare(on: Boolean) = _state.update { it.copy(confirmLargeShare = on, needsSync = false) }

    fun save() {
        val current = _state.value
        val perTx = parseLimit(current.perTxText)
        val window = parseLimit(current.windowText)
        if (perTx == INVALID || window == INVALID) {
            _state.update { it.copy(invalidPerTx = perTx == INVALID, invalidWindow = window == INVALID) }
            return
        }
        val requested = Guardrails(perTx, window, current.confirmNewRecipients, current.confirmLargeShare)
        viewModelScope.launch {
            val tip = wallet.spendingStats(Guardrails.WINDOW_BLOCKS).tipHeight
            val settings = session.wallet?.settings ?: WalletSettings()
            if (tip <= 0 && GuardrailsPolicy.loosens(settings, requested, tip)) {
                _state.update { it.copy(needsSync = true) }
                return@launch
            }
            pending = GuardrailsPolicy.change(settings, requested, tip)
            pin.clear()
            _state.update { it.copy(stage = GuardrailsStage.PIN, pinEntered = 0, wrongPin = false, failed = false) }
        }
    }

    fun cancelPin() {
        pin.clear()
        _state.update { it.copy(stage = GuardrailsStage.EDIT, pinEntered = 0) }
    }

    fun pinDigit(digit: Char) {
        if (_state.value.stage != GuardrailsStage.PIN || !_state.value.pinKeysEnabled) return
        pin.append(digit)
        _state.update { it.copy(pinEntered = pin.size, wrongPin = false, failed = false) }
        if (pin.isFull) apply()
    }

    fun pinBackspace() {
        pin.removeLast()
        _state.update { it.copy(pinEntered = pin.size) }
    }

    private fun apply() {
        val settings = pending ?: return
        val attempt = pin.toCharArray()
        pin.clear()
        _state.update { it.copy(pinVerifying = true) }
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { walletAccess.updateSettings(attempt, settings) }
                } finally {
                    attempt.wipe()
                }
            _state.update { it.copy(pinVerifying = false, pinEntered = 0) }
            when (outcome) {
                SettingsOutcome.Done -> {
                    _state.update { it.copy(stage = GuardrailsStage.SAVED, scheduledAt = settings.scheduledGuardrails?.activatesAtHeight) }
                }

                is SettingsOutcome.WrongPin -> {
                    _state.update { it.copy(wrongPin = true, pinErrorSignal = it.pinErrorSignal + 1) }
                    countdown.start(outcome.lockout)
                }

                is SettingsOutcome.LockedOut -> {
                    countdown.start(outcome.remaining)
                }

                SettingsOutcome.Failed -> {
                    _state.update { it.copy(failed = true) }
                }
            }
        }
    }

    override fun onCleared() = pin.clear()

    private companion object {
        const val INVALID = -1L

        /** Empty = no limit (null); otherwise a positive whole number of sats. */
        fun parseLimit(text: String): Long? {
            val digits = text.filterNot { it.isWhitespace() || it == '_' || it == ' ' }
            if (digits.isEmpty()) return null
            return digits.takeIf { it.length <= 16 && it.all(Char::isDigit) }?.toLong()?.takeIf { it > 0 } ?: INVALID
        }
    }
}
