package io.github.brunogutierre.bitpocket.core.testing

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
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
import io.github.brunogutierre.bitpocket.core.wallet.VaultPocket
import io.github.brunogutierre.bitpocket.core.wallet.WalletOpener

class FakePocketWallet(
    override val network: SupportedNetwork = SupportedNetwork.SIGNET,
) : VaultPocket {
    var closed = false
    var offline = false

    override suspend fun balance() = Balance(0, 0)

    override suspend fun nextReceiveAddress() = ReceiveAddress(0, "tb1q")

    override suspend fun freshReceiveAddress(): ReceiveAddress? = null

    var historyItems = emptyList<HistoryItem>()

    override suspend fun history() = historyItems

    override suspend fun transaction(txid: String) = historyItems.firstOrNull { it.txid == txid }

    var stats = SpendingStats(tipHeight = 1_000, spentInWindowSats = 0, pastRecipients = emptySet(), balanceSats = 0)

    override suspend fun spendingStats(windowBlocks: Int) = stats

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

    /** Vault spends reuse [plans]; seeds are recorded as "phone/recovery" first bytes. */
    val vaultSends = mutableListOf<Pair<Byte, Byte>>()

    override suspend fun planVaultSpend(request: SpendRequest): SpendPlan = planSpend(request)

    override suspend fun sendFromVault(
        plan: SpendPlan,
        phoneSeed: ByteArray,
        recoverySeed: ByteArray,
    ): String {
        sendError?.let { throw it }
        vaultSends += phoneSeed.first() to recoverySeed.first()
        return "vault-txid-${vaultSends.size}"
    }

    var heartbeat =
        io.github.brunogutierre.bitpocket.core.vault.Heartbeat(
            io.github.brunogutierre.bitpocket.core.vault.HeartbeatStatus.OK,
            null,
        )

    override suspend fun heartbeat() = heartbeat

    override fun close() {
        closed = true
    }
}

/** Opens a [FakePocketWallet] per unlock (or fails when [failure] is set). */
class FakeWalletOpener(
    private val root: java.io.File = java.io.File("build/fake-wallets"),
) : WalletOpener {
    val opened = mutableListOf<FakePocketWallet>()

    /** The payloads it was given (the same objects, so tests can check they were wiped). */
    val payloads = mutableListOf<SlotPayload>()
    var failure: Exception? = null

    override fun dirOf(payload: SlotPayload) = java.io.File(root, payload.network.name)

    val vaults = mutableListOf<FakePocketWallet>()

    override fun openVault(
        payload: SlotPayload,
        config: io.github.brunogutierre.bitpocket.core.vault.VaultConfig,
    ): FakePocketWallet = FakePocketWallet(payload.network).also { vaults += it }

    override fun open(payload: SlotPayload): FakePocketWallet {
        failure?.let { throw it }
        payloads += payload
        return FakePocketWallet(payload.network).also { opened += it }
    }
}
