package io.github.brunogutierre.bitpocket.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.seed.WordChallenge
import io.github.brunogutierre.bitpocket.ui.components.PickResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class VerifySeedUiState(
    val step: Int,
    val totalSteps: Int,
    val challenge: WordChallenge,
    val selected: String? = null,
    val result: PickResult = PickResult.NONE,
    val errorSignal: Int = 0,
)

/** Asks for 3 random words of the new mnemonic; a wrong pick shakes and can be retried. */
class VerifySeedViewModel(
    draft: OnboardingDraft,
    mnemonics: MnemonicService,
    random: Random = Random.Default,
) : ViewModel() {
    private val challenges = mnemonics.challenges(draft.words, random)
    private val _state = MutableStateFlow(VerifySeedUiState(step = 1, totalSteps = challenges.size, challenge = challenges.first()))
    val state: StateFlow<VerifySeedUiState> = _state.asStateFlow()
    private val _verified = Channel<Unit>(Channel.BUFFERED)
    val verified = _verified.receiveAsFlow()

    fun pick(word: String) {
        val current = _state.value
        if (current.result == PickResult.CORRECT) return
        if (word != current.challenge.answer) {
            _state.update { it.copy(selected = word, result = PickResult.WRONG, errorSignal = it.errorSignal + 1) }
            return
        }
        _state.update { it.copy(selected = word, result = PickResult.CORRECT) }
        viewModelScope.launch {
            delay(CORRECT_FEEDBACK_MS)
            if (current.step == challenges.size) {
                _verified.send(Unit)
            } else {
                _state.value =
                    VerifySeedUiState(step = current.step + 1, totalSteps = challenges.size, challenge = challenges[current.step])
            }
        }
    }

    private companion object {
        const val CORRECT_FEEDBACK_MS = 400L
    }
}
