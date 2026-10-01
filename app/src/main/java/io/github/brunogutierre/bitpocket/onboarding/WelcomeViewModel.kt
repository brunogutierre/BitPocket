package io.github.brunogutierre.bitpocket.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WelcomeUiState(
    val network: SupportedNetwork = SupportedNetwork.DEFAULT,
    val advancedExpanded: Boolean = false,
)

sealed interface WelcomeAction {
    data object ToggleAdvanced : WelcomeAction

    data class SelectNetwork(
        val network: SupportedNetwork,
    ) : WelcomeAction

    data object Create : WelcomeAction

    data object Restore : WelcomeAction
}

enum class WelcomeEvent { SHOW_NEW_SEED, RESTORE }

class WelcomeViewModel(
    private val draft: OnboardingDraft,
    private val mnemonics: MnemonicService,
) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()
    private val _events = Channel<WelcomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: WelcomeAction) {
        when (action) {
            WelcomeAction.ToggleAdvanced -> {
                _state.update { it.copy(advancedExpanded = !it.advancedExpanded) }
            }

            is WelcomeAction.SelectNetwork -> {
                _state.update { it.copy(network = action.network) }
            }

            WelcomeAction.Create -> {
                startDraft()
                val mnemonic = mnemonics.create()
                draft.setMnemonic(mnemonic.entropy, mnemonic.words)
                emit(WelcomeEvent.SHOW_NEW_SEED)
            }

            WelcomeAction.Restore -> {
                startDraft()
                emit(WelcomeEvent.RESTORE)
            }
        }
    }

    private fun startDraft() {
        draft.clear()
        draft.network = _state.value.network
    }

    private fun emit(event: WelcomeEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
