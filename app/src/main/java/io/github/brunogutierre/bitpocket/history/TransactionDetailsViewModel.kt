package io.github.brunogutierre.bitpocket.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import io.github.brunogutierre.bitpocket.core.wallet.WalletPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TransactionDetailsUiState {
    data object Loading : TransactionDetailsUiState

    data class Loaded(
        val item: HistoryItem,
    ) : TransactionDetailsUiState

    /** The wallet no longer knows it (e.g. an unconfirmed transaction was replaced). */
    data object NotFound : TransactionDetailsUiState
}

class TransactionDetailsViewModel(
    private val wallet: WalletPort,
    val txid: String,
) : ViewModel() {
    val network: SupportedNetwork = wallet.network
    private val _state = MutableStateFlow<TransactionDetailsUiState>(TransactionDetailsUiState.Loading)
    val state: StateFlow<TransactionDetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = wallet.transaction(txid)?.let { TransactionDetailsUiState.Loaded(it) } ?: TransactionDetailsUiState.NotFound
        }
    }

    fun explorerUrl(): String = network.explorerTxUrl(txid)
}
