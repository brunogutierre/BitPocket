package io.github.brunogutierre.bitpocket.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.wallet.Coin
import io.github.brunogutierre.bitpocket.core.wallet.WalletPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coin control: a pocket's coins, each of which can be frozen (never spent) or released. */
class CoinsViewModel(
    private val wallet: WalletPort,
) : ViewModel() {
    val network = wallet.network
    private val _coins = MutableStateFlow<List<Coin>>(emptyList())
    val coins: StateFlow<List<Coin>> = _coins.asStateFlow()

    init {
        viewModelScope.launch { _coins.value = wallet.coins() }
    }

    fun setFrozen(
        coin: Coin,
        frozen: Boolean,
    ) {
        viewModelScope.launch {
            wallet.setFrozen(coin.outpoint, frozen)
            _coins.value = wallet.coins()
        }
    }
}
