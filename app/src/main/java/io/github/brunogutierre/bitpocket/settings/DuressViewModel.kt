package io.github.brunogutierre.bitpocket.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.session.PinPolicy
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
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

enum class DuressStage { INTRO, PIN, CHOOSE, CONFIRM, SAVING, DONE }

data class DuressUiState(
    val stage: DuressStage = DuressStage.INTRO,
    val enabled: Boolean = false,
    /** The flow removes the duress PIN instead of creating one. */
    val removing: Boolean = false,
    val entered: Int = 0,
    val wrongPin: Boolean = false,
    val weakPin: Boolean = false,
    val samePin: Boolean = false,
    val mismatch: Boolean = false,
    val failed: Boolean = false,
    val errorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
) {
    val keysEnabled: Boolean get() = stage in setOf(DuressStage.PIN, DuressStage.CHOOSE, DuressStage.CONFIRM) && lockoutSeconds == 0L
}

/**
 * Duress PIN setup (ADR 0009): the PIN, then a different, non-trivial duress PIN twice; a decoy
 * wallet with its own new seed goes into the other slot. The view model never knows which slot is
 * open: inside the decoy, the same flow runs and the core makes it harmless.
 */
class DuressViewModel(
    private val walletAccess: WalletAccess,
    session: Session,
    private val mnemonics: MnemonicService = MnemonicService(),
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(DuressUiState(enabled = session.wallet?.settings?.duress == true))
    val state: StateFlow<DuressUiState> = _state.asStateFlow()
    private val pin = PinBuffer()
    private val duress = PinBuffer()
    private val confirmation = PinBuffer()
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }

    private val buffer: PinBuffer
        get() =
            when (_state.value.stage) {
                DuressStage.CHOOSE -> duress
                DuressStage.CONFIRM -> confirmation
                else -> pin
            }

    fun start() = _state.update { it.copy(stage = DuressStage.PIN, removing = false) }

    fun startRemoval() = _state.update { it.copy(stage = DuressStage.PIN, removing = true) }

    fun digit(digit: Char) {
        if (!_state.value.keysEnabled) return
        buffer.append(digit)
        _state.update {
            it.copy(
                entered = buffer.size,
                wrongPin = false,
                weakPin = false,
                samePin = false,
                mismatch = false,
                failed = false,
            )
        }
        if (!buffer.isFull) return
        when (_state.value.stage) {
            DuressStage.PIN -> if (_state.value.removing) remove() else _state.update { it.copy(stage = DuressStage.CHOOSE, entered = 0) }
            DuressStage.CHOOSE -> chooseDuress()
            DuressStage.CONFIRM -> confirm()
            else -> Unit
        }
    }

    fun backspace() {
        buffer.removeLast()
        _state.update { it.copy(entered = buffer.size) }
    }

    private fun chooseDuress() {
        val digits = duress.toCharArray()
        val weak =
            try {
                PinPolicy.isTooWeak(digits)
            } finally {
                digits.wipe()
            }
        val same = duress.contentEquals(pin)
        if (weak || same) {
            duress.clear()
            _state.update { it.copy(entered = 0, weakPin = weak, samePin = same, errorSignal = it.errorSignal + 1) }
        } else {
            _state.update { it.copy(stage = DuressStage.CONFIRM, entered = 0) }
        }
    }

    private fun confirm() {
        if (!duress.contentEquals(confirmation)) {
            duress.clear()
            confirmation.clear()
            _state.update { it.copy(stage = DuressStage.CHOOSE, entered = 0, mismatch = true, errorSignal = it.errorSignal + 1) }
            return
        }
        val decoy = mnemonics.create().entropy
        val chosen = duress.toCharArray()
        submit { real ->
            try {
                walletAccess.setUpDuress(real, chosen, decoy)
            } finally {
                chosen.wipe()
                decoy.wipe()
            }
        }
    }

    private fun remove() = submit { real -> walletAccess.removeDuress(real) }

    /** Runs [call] with a copy of the PIN off the main thread, then shows the outcome. */
    private fun submit(call: (CharArray) -> SettingsOutcome) {
        val real = pin.toCharArray()
        val removing = _state.value.removing
        _state.update { it.copy(stage = DuressStage.SAVING) }
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { call(real) }
                } finally {
                    real.wipe()
                }
            clearAll()
            when (outcome) {
                SettingsOutcome.Done -> {
                    _state.update { it.copy(stage = DuressStage.DONE, enabled = !removing, entered = 0) }
                }

                is SettingsOutcome.WrongPin -> {
                    _state.update { it.copy(stage = DuressStage.PIN, entered = 0, wrongPin = true, errorSignal = it.errorSignal + 1) }
                    countdown.start(outcome.lockout)
                }

                is SettingsOutcome.LockedOut -> {
                    _state.update { it.copy(stage = DuressStage.PIN, entered = 0) }
                    countdown.start(outcome.remaining)
                }

                SettingsOutcome.Failed -> {
                    _state.update { it.copy(stage = DuressStage.PIN, entered = 0, failed = true) }
                }
            }
        }
    }

    private fun clearAll() {
        pin.clear()
        duress.clear()
        confirmation.clear()
    }

    override fun onCleared() = clearAll()
}
