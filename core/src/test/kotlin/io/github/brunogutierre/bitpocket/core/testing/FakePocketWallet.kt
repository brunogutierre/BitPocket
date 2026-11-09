package io.github.brunogutierre.bitpocket.core.testing

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.core.wallet.ReceiveAddress
import io.github.brunogutierre.bitpocket.core.wallet.WalletOpener

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

/** Opens a [FakePocketWallet] per unlock (or fails when [failure] is set). */
class FakeWalletOpener : WalletOpener {
    val opened = mutableListOf<FakePocketWallet>()
    var failure: Exception? = null

    override fun open(payload: SlotPayload): FakePocketWallet {
        failure?.let { throw it }
        return FakePocketWallet(payload.network).also { opened += it }
    }
}
