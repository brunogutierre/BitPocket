package io.github.brunogutierre.bitpocket

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.core.wallet.ReceiveAddress

class FakePocketWallet(
    override val network: SupportedNetwork = SupportedNetwork.SIGNET,
) : PocketWallet {
    var closed = false

    override suspend fun balance() = Balance(0, 0)

    override suspend fun nextReceiveAddress() = ReceiveAddress(0, "tb1q")

    override suspend fun sync() = Unit

    override fun close() {
        closed = true
    }
}
