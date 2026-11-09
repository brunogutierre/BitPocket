package io.github.brunogutierre.bitpocket.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.BiometricSeedVault
import io.github.brunogutierre.bitpocket.core.seed.Bip39
import io.github.brunogutierre.bitpocket.core.session.SeedOutcome
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.core.settings.AutoLock
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
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

/** Settings actions that re-enter the PIN first. */
enum class PinAction { SHOW_PHRASE, ENABLE_BIOMETRICS, DISABLE_BIOMETRICS, AUTO_LOCK, WIPE }

data class SettingsUiState(
    val biometricOn: Boolean = false,
    val biometricAvailable: Boolean = false,
    val autoLock: AutoLock = AutoLock.DEFAULT,
    val hasVault: Boolean = false,
    val choosingAutoLock: Boolean = false,
    /** The auto-lock time waiting for the PIN. */
    val pendingAutoLock: AutoLock? = null,
    /** The PIN pad is shown for this action. */
    val action: PinAction? = null,
    val confirmingWipe: Boolean = false,
    val pinEntered: Int = 0,
    val pinVerifying: Boolean = false,
    val wrongPin: Boolean = false,
    val pinErrorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
    val failed: Boolean = false,
    /** PIN entered for ENABLE_BIOMETRICS: the screen now runs [SettingsViewModel.seal]. */
    val awaitingSeal: Boolean = false,
    val biometricSetupFailed: Boolean = false,
    /** Recovery words being shown (FLAG_SECURE screen). */
    val phrase: List<String>? = null,
    val wiped: Boolean = false,
) {
    val pinKeysEnabled: Boolean get() = !pinVerifying && !awaitingSeal && lockoutSeconds == 0L
}

/** Settings list plus the PIN-gated actions: recovery words, biometric signing, wipe. */
class SettingsViewModel(
    private val walletAccess: WalletAccess,
    private val session: Session,
    biometricAvailable: Boolean,
    private val wipeAll: () -> Unit,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val network: SupportedNetwork = session.wallet?.network ?: SupportedNetwork.DEFAULT
    private val _state =
        MutableStateFlow(
            SettingsUiState(
                biometricOn = session.wallet?.biometricSeed != null,
                biometricAvailable = biometricAvailable,
                autoLock = session.wallet?.settings?.autoLock ?: AutoLock.DEFAULT,
                hasVault = session.wallet?.settings?.vault != null,
            ),
        )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val pin = PinBuffer()
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }

    fun showRecoveryPhrase() = ask(PinAction.SHOW_PHRASE)

    fun setBiometricSigning(on: Boolean) = ask(if (on) PinAction.ENABLE_BIOMETRICS else PinAction.DISABLE_BIOMETRICS)

    /** Back on Settings after the Vault wizard: show what changed. */
    fun refresh() = _state.update { it.copy(hasVault = session.wallet?.settings?.vault != null) }

    fun openAutoLock() = _state.update { it.copy(choosingAutoLock = true) }

    fun dismissAutoLock() = _state.update { it.copy(choosingAutoLock = false) }

    /** Every security setting changes only after the PIN (ADR 0005). */
    fun chooseAutoLock(option: AutoLock) {
        _state.update { it.copy(choosingAutoLock = false) }
        if (option == _state.value.autoLock) return
        _state.update { it.copy(pendingAutoLock = option) }
        ask(PinAction.AUTO_LOCK)
    }

    fun requestWipe() = _state.update { it.copy(confirmingWipe = true) }

    fun dismissWipe() = _state.update { it.copy(confirmingWipe = false) }

    fun confirmWipe() {
        _state.update { it.copy(confirmingWipe = false) }
        ask(PinAction.WIPE)
    }

    fun cancelPin() {
        pin.clear()
        _state.update {
            it.copy(
                action = null,
                pinEntered = 0,
                wrongPin = false,
                failed = false,
                awaitingSeal = false,
                pendingAutoLock = null,
            )
        }
    }

    fun closePhrase() = _state.update { it.copy(phrase = null) }

    fun pinDigit(digit: Char) {
        val action = _state.value.action ?: return
        if (!_state.value.pinKeysEnabled) return
        pin.append(digit)
        _state.update { it.copy(pinEntered = pin.size, wrongPin = false, failed = false) }
        if (!pin.isFull) return
        if (action == PinAction.ENABLE_BIOMETRICS) {
            // The PIN stays in the buffer until the screen hands over a vault with the prompt.
            _state.update { it.copy(awaitingSeal = true) }
        } else {
            run(action)
        }
    }

    fun pinBackspace() {
        pin.removeLast()
        _state.update { it.copy(pinEntered = pin.size) }
    }

    /** Called by the screen (which owns the Activity) once [SettingsUiState.awaitingSeal] is set. */
    fun seal(vault: BiometricSeedVault) {
        if (!_state.value.awaitingSeal) return
        val attempt = pin.toCharArray()
        pin.clear()
        _state.update { it.copy(pinVerifying = true) }
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { walletAccess.setBiometricSigning(attempt, vault) }
                } finally {
                    attempt.wipe()
                }
            _state.update { it.copy(awaitingSeal = false, pinVerifying = false, pinEntered = 0) }
            when (outcome) {
                SettingsOutcome.Done -> finish { it.copy(biometricOn = true) }
                SettingsOutcome.Failed -> finish { it.copy(biometricOn = false, biometricSetupFailed = true) }
                else -> handleDenied(outcome)
            }
        }
    }

    private fun ask(action: PinAction) {
        pin.clear()
        _state.update { it.copy(action = action, pinEntered = 0, wrongPin = false, failed = false, biometricSetupFailed = false) }
    }

    private fun run(action: PinAction) {
        val attempt = pin.toCharArray()
        pin.clear()
        _state.update { it.copy(pinVerifying = true) }
        viewModelScope.launch {
            try {
                when (action) {
                    PinAction.SHOW_PHRASE -> {
                        showPhrase(attempt)
                    }

                    PinAction.DISABLE_BIOMETRICS -> {
                        settle(
                            withContext(workDispatcher) {
                                walletAccess.setBiometricSigning(attempt, null)
                            },
                        ) { it.copy(biometricOn = false) }
                    }

                    PinAction.WIPE -> {
                        settle(withContext(workDispatcher) { walletAccess.verifyPin(attempt) }) { wipe(it) }
                    }

                    PinAction.AUTO_LOCK -> {
                        saveAutoLock(attempt)
                    }

                    PinAction.ENABLE_BIOMETRICS -> {
                        Unit
                    }
                }
            } finally {
                attempt.wipe()
            }
        }
    }

    private suspend fun showPhrase(attempt: CharArray) {
        when (val outcome = withContext(workDispatcher) { walletAccess.seedForSigning(attempt) }) {
            is SeedOutcome.Seed -> {
                val words =
                    try {
                        Bip39.toWords(outcome.entropy)
                    } finally {
                        outcome.entropy.wipe()
                    }
                finish { it.copy(phrase = words) }
            }

            is SeedOutcome.WrongPin -> {
                handleDenied(SettingsOutcome.WrongPin(outcome.lockout))
            }

            is SeedOutcome.LockedOut -> {
                handleDenied(SettingsOutcome.LockedOut(outcome.remaining))
            }

            SeedOutcome.Failed -> {
                handleDenied(SettingsOutcome.Failed)
            }
        }
    }

    private suspend fun saveAutoLock(attempt: CharArray) {
        val option = _state.value.pendingAutoLock ?: return
        val settings = (session.wallet?.settings ?: WalletSettings()).copy(autoLock = option)
        settle(withContext(workDispatcher) { walletAccess.updateSettings(attempt, settings) }) {
            it.copy(autoLock = option, pendingAutoLock = null)
        }
    }

    private fun wipe(state: SettingsUiState): SettingsUiState {
        wipeAll()
        return state.copy(wiped = true)
    }

    private fun settle(
        outcome: SettingsOutcome,
        onDone: (SettingsUiState) -> SettingsUiState,
    ) = if (outcome == SettingsOutcome.Done) finish(onDone) else handleDenied(outcome)

    private fun finish(change: (SettingsUiState) -> SettingsUiState) =
        _state.update { change(it.copy(action = null, pinVerifying = false, pinEntered = 0)) }

    private fun handleDenied(outcome: SettingsOutcome) {
        _state.update { it.copy(pinVerifying = false, pinEntered = 0) }
        when (outcome) {
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

            SettingsOutcome.Done -> {
                Unit
            }
        }
    }

    override fun onCleared() = pin.clear()
}
