package io.github.brunogutierre.bitpocket.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.vault.Heartbeat
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.core.wallet.VaultPocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    /** Null until the saved balance is read. */
    val balance: Balance? = null,
    val history: List<HistoryItem> = emptyList(),
    val syncing: Boolean = false,
    val syncFailed: Boolean = false,
    /** Null while there is no Vault (Home then offers to set it up). */
    val vaultBalance: Balance? = null,
    val vaultHeartbeat: Heartbeat? = null,
)

/**
 * Spending pocket overview. The saved balance and history show at once; a sync starts right after unlock
 * (this ViewModel lives as long as Home is on the back stack) and on pull-to-refresh.
 */
class HomeViewModel(
    private val pocket: PocketWallet,
    private val lockSession: () -> Unit,
    /** The Vault pocket, read each time: it can be set up while Home is on the back stack. */
    private val vault: () -> VaultPocket? = { null },
) : ViewModel() {
    val network: SupportedNetwork = pocket.network
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { readWallet() }
        sync()
    }

    fun sync() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val failed =
                try {
                    pocket.sync()
                    vault()?.sync()
                    false
                } catch (e: ChainUnavailableException) {
                    true
                }
            readWallet()
            _state.update { it.copy(syncing = false, syncFailed = failed) }
        }
    }

    /** Re-reads the saved balance and history (no network). */
    fun reload() {
        viewModelScope.launch { readWallet() }
    }

    fun lock() = lockSession()

    private suspend fun readWallet() {
        val balance = pocket.balance()
        val history = pocket.history()
        val vaultPocket = vault()
        val vaultBalance = vaultPocket?.balance()
        val heartbeat = vaultPocket?.heartbeat()
        _state.update { it.copy(balance = balance, history = history, vaultBalance = vaultBalance, vaultHeartbeat = heartbeat) }
    }
}
