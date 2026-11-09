package io.github.brunogutierre.bitpocket

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.send.SpendingStats
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
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

    var historyItems = emptyList<HistoryItem>()

    override suspend fun history() = historyItems

    override suspend fun transaction(txid: String) = historyItems.firstOrNull { it.txid == txid }

    var stats = SpendingStats(tipHeight = 1_000, spentInWindowSats = 0, pastRecipients = emptySet(), balanceSats = 0)

    override suspend fun spendingStats(windowBlocks: Int) = stats

    override suspend fun sync() {
        syncs++
        syncGate?.await()
        if (offline) throw ChainUnavailableException(RuntimeException("offline"))
        balanceAfterSync?.let { balance = it }
    }

    var fees = FeeOptions(fast = 5, normal = 2, slow = 1)

    override suspend fun feeRates(): FeeOptions {
        if (offline) throw ChainUnavailableException(RuntimeException("offline"))
        return fees
    }

    /** Plans handed out by [planSpend]; the request is recorded. Empty = insufficient funds. */
    val plans = ArrayDeque<SpendPlan>()
    var lastSpendRequest: SpendRequest? = null

    override suspend fun planSpend(request: SpendRequest): SpendPlan {
        lastSpendRequest = request
        return plans.removeFirstOrNull() ?: throw SpendException(SpendError.INSUFFICIENT_FUNDS)
    }

    /** Seeds passed to [send] (copied, since callers wipe theirs); [sendError] fails the send. */
    val sentWith = mutableListOf<ByteArray>()
    var sendError: Exception? = null

    override suspend fun send(
        plan: SpendPlan,
        seedEntropy: ByteArray,
    ): String {
        sendError?.let { throw it }
        sentWith += seedEntropy.copyOf()
        return "txid-${sentWith.size}"
    }

    override fun close() {
        closed = true
    }
}
