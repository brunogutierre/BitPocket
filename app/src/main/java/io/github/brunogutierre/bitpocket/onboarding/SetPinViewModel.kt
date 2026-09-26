package io.github.brunogutierre.bitpocket.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.security.PinBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PinStage { CHOOSE, CONFIRM }

data class SetPinUiState(
    val stage: PinStage = PinStage.CHOOSE,
    val entered: Int = 0,
    val mismatch: Boolean = false,
    val errorSignal: Int = 0,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Choose + confirm the PIN, then save the wallet (slow Argon2id + Keystore, off the main thread). */
class SetPinViewModel(
    private val draft: OnboardingDraft,
    private val walletAccess: WalletAccess,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val chosen = PinBuffer()
    private val confirmation = PinBuffer()
    private val _state = MutableStateFlow(SetPinUiState())
    val state: StateFlow<SetPinUiState> = _state.asStateFlow()
    private val _created = Channel<Unit>(Channel.BUFFERED)
    val created = _created.receiveAsFlow()

    private val current get() = if (_state.value.stage == PinStage.CHOOSE) chosen else confirmation

    fun digit(digit: Char) {
        if (_state.value.saving) return
        current.append(digit)
        _state.update { it.copy(entered = current.size, mismatch = false, saveFailed = false) }
        if (!current.isFull) return
        when {
            _state.value.stage == PinStage.CHOOSE -> _state.update { it.copy(stage = PinStage.CONFIRM, entered = 0) }
            chosen.contentEquals(confirmation) -> save()
            else -> restart(mismatch = true)
        }
    }

    fun backspace() {
        if (_state.value.saving) return
        current.removeLast()
        _state.update { it.copy(entered = current.size) }
    }

    private fun save() {
        val entropy = draft.entropy ?: return restart(mismatch = false)
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val pin = chosen.toCharArray()
            try {
                withContext(workDispatcher) { walletAccess.create(pin, entropy, draft.network) }
                draft.clear()
                clearPins()
                _created.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                restart(mismatch = false)
                _state.update { it.copy(saveFailed = true) }
            } finally {
                pin.wipe()
            }
        }
    }

    private fun restart(mismatch: Boolean) {
        clearPins()
        _state.update {
            SetPinUiState(mismatch = mismatch, errorSignal = if (mismatch) it.errorSignal + 1 else it.errorSignal)
        }
    }

    private fun clearPins() {
        chosen.clear()
        confirmation.clear()
    }

    override fun onCleared() = clearPins()
}
