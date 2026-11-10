package io.github.brunogutierre.bitpocket.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.brunogutierre.bitpocket.core.format.Bip21
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.qr.QrCode
import io.github.brunogutierre.bitpocket.core.qr.QrMatrix
import io.github.brunogutierre.bitpocket.core.wallet.ReceiveAddress
import io.github.brunogutierre.bitpocket.core.wallet.WalletPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReceiveUiState(
    val address: ReceiveAddress? = null,
    val qr: QrMatrix? = null,
    /** True after "New address" was refused because too many revealed addresses are unused. */
    val atUnusedLimit: Boolean = false,
)

/** Shows the first unused address (stable across visits); "New address" reveals another one. */
class ReceiveViewModel(
    private val wallet: WalletPort,
) : ViewModel() {
    val network: SupportedNetwork = wallet.network
    private val _state = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { show(wallet.nextReceiveAddress()) }
    }

    fun newAddress() {
        viewModelScope.launch {
            val fresh = wallet.freshReceiveAddress()
            if (fresh == null) _state.update { it.copy(atUnusedLimit = true) } else show(fresh)
        }
    }

    /** Plain `bitcoin:` URI for the share sheet. */
    fun shareText(): String? = _state.value.address?.let { Bip21.uri(it.address) }

    private fun show(address: ReceiveAddress) {
        _state.update { ReceiveUiState(address = address, qr = QrCode.encode(Bip21.qrUri(address.address))) }
    }
}
