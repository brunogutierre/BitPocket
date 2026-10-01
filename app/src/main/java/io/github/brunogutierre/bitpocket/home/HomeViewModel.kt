package io.github.brunogutierre.bitpocket.home

import androidx.lifecycle.ViewModel
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.session.Session

/** Placeholder home: shows the wallet network and locks the session (balance comes in wallet-sync). */
class HomeViewModel(
    private val session: Session,
) : ViewModel() {
    val network: SupportedNetwork = session.wallet?.payload?.network ?: SupportedNetwork.DEFAULT

    fun lock() = session.lock()
}
