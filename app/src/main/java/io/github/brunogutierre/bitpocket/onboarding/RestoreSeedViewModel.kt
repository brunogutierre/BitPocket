package io.github.brunogutierre.bitpocket.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.seed.RestoreResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RestoreError {
    /** [positions] are 1-based. */
    data class UnknownWords(
        val positions: List<Int>,
    ) : RestoreError

    data object InvalidChecksum : RestoreError
}

data class RestoreSeedUiState(
    val wordCount: Int = 12,
    val words: List<String> = emptyList(),
    val input: String = "",
    val suggestions: List<String> = emptyList(),
    val error: RestoreError? = null,
) {
    val complete: Boolean get() = words.size == wordCount
}

sealed interface RestoreAction {
    data class SetWordCount(
        val count: Int,
    ) : RestoreAction

    data class InputChanged(
        val text: String,
    ) : RestoreAction

    data class AcceptWord(
        val word: String,
    ) : RestoreAction

    data object RemoveLast : RestoreAction

    data object Submit : RestoreAction
}

/** Word-by-word restore with wordlist suggestions; only wordlist words can be added. */
class RestoreSeedViewModel(
    private val draft: OnboardingDraft,
    private val mnemonics: MnemonicService,
) : ViewModel() {
    private val _state = MutableStateFlow(RestoreSeedUiState())
    val state: StateFlow<RestoreSeedUiState> = _state.asStateFlow()
    private val _restored = Channel<Unit>(Channel.BUFFERED)
    val restored = _restored.receiveAsFlow()

    fun onAction(action: RestoreAction) {
        when (action) {
            is RestoreAction.SetWordCount -> {
                _state.update { it.copy(wordCount = action.count, words = it.words.take(action.count), error = null) }
            }

            is RestoreAction.InputChanged -> {
                onInput(action.text)
            }

            is RestoreAction.AcceptWord -> {
                _state.update {
                    if (it.complete || !isWord(action.word)) {
                        it
                    } else {
                        it.copy(words = it.words + action.word, input = "", suggestions = emptyList(), error = null)
                    }
                }
            }

            RestoreAction.RemoveLast -> {
                _state.update { it.copy(words = it.words.dropLast(1), error = null) }
            }

            RestoreAction.Submit -> {
                submit()
            }
        }
    }

    /**
     * Whitespace completes a word, so typing "word " or pasting several words moves on. Complete
     * words are accepted while they are in the wordlist; the rest stays in the input.
     */
    private fun onInput(raw: String) {
        val text = raw.lowercase().filter { it.isLetter() || it.isWhitespace() }
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val completeCount = if (text.isNotEmpty() && text.last().isWhitespace()) tokens.size else tokens.size - 1
        var accepted = 0
        while (accepted < completeCount && !_state.value.complete && isWord(tokens[accepted])) {
            onAction(RestoreAction.AcceptWord(tokens[accepted]))
            accepted++
        }
        val remaining = tokens.drop(accepted).joinToString(" ")
        _state.update { it.copy(input = remaining, suggestions = mnemonics.suggestions(remaining)) }
    }

    private fun isWord(word: String) = word in mnemonics.suggestions(word)

    private fun submit() {
        val current = _state.value
        if (!current.complete) return
        when (val result = mnemonics.restore(current.words)) {
            is RestoreResult.Valid -> {
                draft.setMnemonic(result.entropy, current.words)
                viewModelScope.launch { _restored.send(Unit) }
            }

            is RestoreResult.UnknownWords -> {
                _state.update { it.copy(error = RestoreError.UnknownWords(result.positions)) }
            }

            RestoreResult.InvalidChecksum -> {
                _state.update { it.copy(error = RestoreError.InvalidChecksum) }
            }

            // The UI only allows 12 or 24 words, so the count is always valid here.
            is RestoreResult.InvalidWordCount -> {
                Unit
            }
        }
    }
}
