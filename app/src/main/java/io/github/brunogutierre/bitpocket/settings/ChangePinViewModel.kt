package io.github.brunogutierre.bitpocket.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.session.PinPolicy
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

enum class ChangePinStage { CURRENT, NEW, CONFIRM, DONE }

data class ChangePinUiState(
    val stage: ChangePinStage = ChangePinStage.CURRENT,
    val entered: Int = 0,
    val working: Boolean = false,
    val wrongPin: Boolean = false,
    val weakPin: Boolean = false,
    val mismatch: Boolean = false,
    val failed: Boolean = false,
    val errorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
) {
    val keysEnabled: Boolean get() = !working && lockoutSeconds == 0L
}

/** Current PIN (checked first), new PIN (PinPolicy), confirmation, then the slot is re-encrypted. */
class ChangePinViewModel(
    private val walletAccess: WalletAccess,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val current = PinBuffer()
    private val chosen = PinBuffer()
    private val confirmation = PinBuffer()
    private val _state = MutableStateFlow(ChangePinUiState())
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }

    private val buffer: PinBuffer
        get() =
            when (_state.value.stage) {
                ChangePinStage.CURRENT -> current
                ChangePinStage.NEW -> chosen
                else -> confirmation
            }

    fun digit(digit: Char) {
        if (!_state.value.keysEnabled || _state.value.stage == ChangePinStage.DONE) return
        buffer.append(digit)
        _state.update { it.copy(entered = buffer.size, wrongPin = false, weakPin = false, mismatch = false, failed = false) }
        if (!buffer.isFull) return
        when (_state.value.stage) {
            ChangePinStage.CURRENT -> checkCurrent()
            ChangePinStage.NEW -> chooseNew()
            ChangePinStage.CONFIRM -> confirm()
            ChangePinStage.DONE -> Unit
        }
    }

    fun backspace() {
        buffer.removeLast()
        _state.update { it.copy(entered = buffer.size) }
    }

    private fun checkCurrent() =
        work({ walletAccess.verifyPin(it) }, current) { outcome ->
            if (outcome == SettingsOutcome.Done) {
                _state.update { it.copy(stage = ChangePinStage.NEW) }
            } else {
                current.clear()
                deny(outcome)
            }
        }

    private fun chooseNew() {
        val digits = chosen.toCharArray()
        val weak =
            try {
                PinPolicy.isTooWeak(digits)
            } finally {
                digits.wipe()
            }
        if (weak) {
            chosen.clear()
            _state.update { it.copy(entered = 0, weakPin = true, errorSignal = it.errorSignal + 1) }
        } else {
            _state.update { it.copy(stage = ChangePinStage.CONFIRM, entered = 0) }
        }
    }

    private fun confirm() {
        if (!chosen.contentEquals(confirmation)) {
            chosen.clear()
            confirmation.clear()
            _state.update { it.copy(stage = ChangePinStage.NEW, entered = 0, mismatch = true, errorSignal = it.errorSignal + 1) }
            return
        }
        val newPin = chosen.toCharArray()
        work({ walletAccess.changePin(it, newPin) }, current) { outcome ->
            newPin.wipe()
            if (outcome == SettingsOutcome.Done) {
                clearAll()
                _state.update { it.copy(stage = ChangePinStage.DONE) }
            } else {
                deny(outcome)
            }
        }
    }

    /** Runs [call] with a copy of [pin] off the main thread, then [then] on the main thread. */
    private fun work(
        call: (CharArray) -> SettingsOutcome,
        pin: PinBuffer,
        then: (SettingsOutcome) -> Unit,
    ) {
        val attempt = pin.toCharArray()
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { call(attempt) }
                } finally {
                    attempt.wipe()
                }
            _state.update { it.copy(working = false, entered = 0) }
            then(outcome)
        }
    }

    private fun deny(outcome: SettingsOutcome) {
        when (outcome) {
            is SettingsOutcome.WrongPin -> {
                clearAll()
                _state.update { ChangePinUiState(wrongPin = true, errorSignal = it.errorSignal + 1) }
                countdown.start(outcome.lockout)
            }

            is SettingsOutcome.LockedOut -> {
                clearAll()
                _state.update { ChangePinUiState(errorSignal = it.errorSignal) }
                countdown.start(outcome.remaining)
            }

            else -> {
                _state.update { it.copy(failed = true) }
            }
        }
    }

    private fun clearAll() {
        current.clear()
        chosen.clear()
        confirmation.clear()
    }

    override fun onCleared() = clearAll()
}
