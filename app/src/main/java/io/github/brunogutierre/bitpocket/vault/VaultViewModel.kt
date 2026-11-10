package io.github.brunogutierre.bitpocket.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.vault.Heartbeat
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import io.github.brunogutierre.bitpocket.core.wallet.VaultPocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val balance: Balance? = null,
    val history: List<HistoryItem> = emptyList(),
    val heartbeat: Heartbeat? = null,
)

/** The Vault pocket: balance and history (synced from Home), and its heir waiting time. */
class VaultViewModel(
    private val vault: VaultPocket,
    val config: VaultConfig,
) : ViewModel() {
    val network: SupportedNetwork = vault.network
    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val balance = vault.balance()
            val history = vault.history()
            val heartbeat = vault.heartbeat()
            _state.update { it.copy(balance = balance, history = history, heartbeat = heartbeat) }
        }
    }
}
