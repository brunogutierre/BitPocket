package io.github.brunogutierre.bitpocket.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.wallet.EsploraServer
import io.github.brunogutierre.bitpocket.core.wallet.ServerCheck
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

enum class ServerStage { EDIT, CHECKING, PIN, SAVED }

data class ServerUiState(
    val stage: ServerStage = ServerStage.EDIT,
    /** Empty = the network's default server. */
    val urlText: String = "",
    val check: ServerCheck? = null,
    val pinEntered: Int = 0,
    val wrongPin: Boolean = false,
    val pinErrorSignal: Int = 0,
    val lockoutSeconds: Long = 0,
    val failed: Boolean = false,
) {
    val pinKeysEnabled: Boolean get() = stage == ServerStage.PIN && lockoutSeconds == 0L
}

/** Custom Esplora server (ADR 0010): checked (https, genesis block), saved after the PIN. */
class ServerViewModel(
    private val walletAccess: WalletAccess,
    private val session: Session,
    private val checkServer: (url: String, network: SupportedNetwork) -> ServerCheck,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val network: SupportedNetwork = session.wallet?.network ?: SupportedNetwork.DEFAULT
    private val _state =
        MutableStateFlow(
            ServerUiState(
                urlText =
                    session.wallet
                        ?.settings
                        ?.esploraUrl
                        .orEmpty(),
            ),
        )
    val state: StateFlow<ServerUiState> = _state.asStateFlow()
    private val pin = PinBuffer()
    private val countdown = LockoutCountdown(viewModelScope) { seconds -> _state.update { it.copy(lockoutSeconds = seconds) } }
    private var pending: String? = null

    fun onUrlChange(text: String) = _state.update { it.copy(urlText = text, check = null) }

    fun useDefault() {
        _state.update { it.copy(urlText = "", check = null) }
        save()
    }

    /** Checks a custom URL first; an empty one goes back to the default server. */
    fun save() {
        val text = _state.value.urlText
        if (text.isBlank()) return askPin(null)
        _state.update { it.copy(stage = ServerStage.CHECKING, check = null) }
        viewModelScope.launch {
            val check = withContext(ioDispatcher) { checkServer(text, network) }
            if (check ==
                ServerCheck.OK
            ) {
                askPin(EsploraServer.normalize(text))
            } else {
                _state.update { it.copy(stage = ServerStage.EDIT, check = check) }
            }
        }
    }

    fun cancelPin() {
        pin.clear()
        _state.update { it.copy(stage = ServerStage.EDIT, pinEntered = 0) }
    }

    fun pinDigit(digit: Char) {
        if (!_state.value.pinKeysEnabled) return
        pin.append(digit)
        _state.update { it.copy(pinEntered = pin.size, wrongPin = false, failed = false) }
        if (pin.isFull) apply()
    }

    fun pinBackspace() {
        pin.removeLast()
        _state.update { it.copy(pinEntered = pin.size) }
    }

    private fun askPin(url: String?) {
        pending = url
        pin.clear()
        _state.update { it.copy(stage = ServerStage.PIN, pinEntered = 0) }
    }

    private fun apply() {
        val attempt = pin.toCharArray()
        pin.clear()
        val settings = (session.wallet?.settings ?: WalletSettings()).copy(esploraUrl = pending)
        viewModelScope.launch {
            val outcome =
                try {
                    withContext(workDispatcher) { walletAccess.updateSettings(attempt, settings) }
                } finally {
                    attempt.wipe()
                }
            _state.update { it.copy(pinEntered = 0) }
            when (outcome) {
                SettingsOutcome.Done -> {
                    _state.update { it.copy(stage = ServerStage.SAVED) }
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
}
