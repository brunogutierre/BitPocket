package io.github.brunogutierre.bitpocket.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.session.UnlockOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.security.PinBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration

data class LockUiState(
    val entered: Int = 0,
    val verifying: Boolean = false,
    val wrongPin: Boolean = false,
    val errorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
    val failed: Boolean = false,
) {
    val keysEnabled: Boolean get() = !verifying && lockoutSeconds == 0L
}

/**
 * PIN unlock. The countdown is display only: UnlockService enforces the lockout itself (the
 * attempts record survives restarts), so the keys are just disabled while it runs.
 */
class LockViewModel(
    private val walletAccess: WalletAccess,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val pin = PinBuffer()
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()
    private val _unlocked = Channel<Unit>(Channel.BUFFERED)
    val unlocked = _unlocked.receiveAsFlow()
    private var countdown: Job? = null

    fun digit(digit: Char) {
        if (!_state.value.keysEnabled) return
        pin.append(digit)
        _state.update { it.copy(entered = pin.size, wrongPin = false, failed = false) }
        if (pin.isFull) verify()
    }

    fun backspace() {
        pin.removeLast()
        _state.update { it.copy(entered = pin.size) }
    }

    private fun verify() {
        _state.update { it.copy(verifying = true) }
        val attempt = pin.toCharArray()
        pin.clear()
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { walletAccess.unlock(attempt) }
                } finally {
                    attempt.wipe()
                }
            _state.update { it.copy(verifying = false, entered = 0) }
            when (outcome) {
                UnlockOutcome.Unlocked -> {
                    _unlocked.send(Unit)
                }

                is UnlockOutcome.WrongPin -> {
                    _state.update { it.copy(wrongPin = true, errorSignal = it.errorSignal + 1) }
                    startCountdown(outcome.lockout)
                }

                is UnlockOutcome.LockedOut -> {
                    startCountdown(outcome.remaining)
                }

                UnlockOutcome.Failed -> {
                    _state.update { it.copy(failed = true) }
                }
            }
        }
    }

    private fun startCountdown(duration: Duration) {
        if (duration.isZero) return
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                var seconds = (duration.toMillis() + 999) / 1000
                while (seconds > 0) {
                    _state.update { it.copy(lockoutSeconds = seconds) }
                    delay(1000)
                    seconds--
                }
                _state.update { it.copy(lockoutSeconds = 0) }
            }
    }

    override fun onCleared() = pin.clear()
}
