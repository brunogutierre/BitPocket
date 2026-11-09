package io.github.brunogutierre.bitpocket

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.core.wallet.ReceiveAddress
import kotlinx.coroutines.CompletableDeferred

/** A pocket whose sync either completes, fails like an offline server, or waits for [syncGate]. */
class FakePocketWallet(
    override val network: SupportedNetwork = SupportedNetwork.SIGNET,
) : PocketWallet {
    var balance = Balance(0, 0)
    var balanceAfterSync: Balance? = null
    var offline = false
    var syncGate: CompletableDeferred<Unit>? = null
    var syncs = 0
    var closed = false

    override suspend fun balance() = balance

    /** Addresses handed out by [freshReceiveAddress], in order; empty = at the unused limit. */
    val freshAddresses = ArrayDeque<ReceiveAddress>()

    override suspend fun nextReceiveAddress() = ReceiveAddress(0, "tb1qfirst")

    override suspend fun freshReceiveAddress(): ReceiveAddress? = freshAddresses.removeFirstOrNull()

    override suspend fun sync() {
        syncs++
        syncGate?.await()
        if (offline) throw ChainUnavailableException(RuntimeException("offline"))
        balanceAfterSync?.let { balance = it }
    }

    override fun close() {
        closed = true
    }
}
