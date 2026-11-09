package io.github.brunogutierre.bitpocket.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    /** Null until the saved balance is read. */
    val balance: Balance? = null,
    val syncing: Boolean = false,
    val syncFailed: Boolean = false,
)

/**
 * Spending pocket overview. The saved balance shows at once; a sync starts right after unlock
 * (this ViewModel lives as long as Home is on the back stack) and on pull-to-refresh.
 */
class HomeViewModel(
    private val pocket: PocketWallet,
    private val lockSession: () -> Unit,
) : ViewModel() {
    val network: SupportedNetwork = pocket.network
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { readBalance() }
        sync()
    }

    fun sync() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val failed =
                try {
                    pocket.sync()
                    false
                } catch (e: ChainUnavailableException) {
                    true
                }
            readBalance()
            _state.update { it.copy(syncing = false, syncFailed = failed) }
        }
    }

    fun lock() = lockSession()

    private suspend fun readBalance() {
        val balance = pocket.balance()
        _state.update { it.copy(balance = balance) }
    }
}
