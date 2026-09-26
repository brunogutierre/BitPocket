package io.github.brunogutierre.bitpocket.onboarding

import androidx.lifecycle.ViewModel
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CreateSeedUiState(
    val words: List<String>,
    val revealed: Boolean = false,
) {
    /** "I wrote it down" only makes sense once the words were shown. */
    val canContinue: Boolean get() = revealed
}

class CreateSeedViewModel(
    draft: OnboardingDraft,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateSeedUiState(words = draft.words))
    val state: StateFlow<CreateSeedUiState> = _state.asStateFlow()

    fun reveal() = _state.update { it.copy(revealed = true) }
}
