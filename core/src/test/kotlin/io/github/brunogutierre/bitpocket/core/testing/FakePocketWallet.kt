package io.github.brunogutierre.bitpocket.core.testing

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.core.wallet.ReceiveAddress
import io.github.brunogutierre.bitpocket.core.wallet.WalletOpener

class FakePocketWallet(
    override val network: SupportedNetwork = SupportedNetwork.SIGNET,
) : PocketWallet {
    var closed = false
    var offline = false

    override suspend fun balance() = Balance(0, 0)

    override suspend fun nextReceiveAddress() = ReceiveAddress(0, "tb1q")

    override suspend fun freshReceiveAddress(): ReceiveAddress? = null

    var historyItems = emptyList<HistoryItem>()

    override suspend fun history() = historyItems

    override suspend fun transaction(txid: String) = historyItems.firstOrNull { it.txid == txid }

    override suspend fun sync() = Unit

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

/** Opens a [FakePocketWallet] per unlock (or fails when [failure] is set). */
class FakeWalletOpener : WalletOpener {
    val opened = mutableListOf<FakePocketWallet>()

    /** The payloads it was given (the same objects, so tests can check they were wiped). */
    val payloads = mutableListOf<SlotPayload>()
    var failure: Exception? = null

    override fun open(payload: SlotPayload): FakePocketWallet {
        failure?.let { throw it }
        payloads += payload
        return FakePocketWallet(payload.network).also { opened += it }
    }
}
