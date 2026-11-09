package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.FeePolicy
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Address
import org.bitcoindevkit.AddressParseException
import org.bitcoindevkit.Amount
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.CreateTxException
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.EsploraException
import org.bitcoindevkit.ExtractTxException
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.LoadWithPersistException
import org.bitcoindevkit.PersistenceException
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Script
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Txid
import org.bitcoindevkit.UnconfirmedTx
import org.bitcoindevkit.Wallet
import java.io.File
import java.io.IOException

/**
 * A watch-only BDK wallet: public descriptors only, persisted in SQLite. Private keys never reach
 * it; spending signs with short-lived signers (ADR 0004).
 *
 * BDK objects are not meant for concurrent use, so every call runs on [dispatcher], one at a
 * time (by default a per-wallet view of `Dispatchers.IO` with parallelism 1). [close] is queued
 * on the same dispatcher, so native objects are never freed in the middle of a call (a sync can
 * still be running when the user locks the app).
 */
class BdkWallet internal constructor(
    private val wallet: Wallet,
    private val persister: Persister,
    override val network: SupportedNetwork,
    private val chain: ChainClient,
    private val dispatcher: CoroutineDispatcher = serialIoDispatcher(),
) : PocketWallet {
    override suspend fun balance(): Balance =
        onWallet {
            val balance = wallet.balance()
            Balance(
                confirmedSats = balance.confirmed.toSat().toLong(),
                pendingSats = (balance.trustedPending.toSat() + balance.untrustedPending.toSat()).toLong(),
            )
        }

    override suspend fun nextReceiveAddress(): ReceiveAddress =
        onWallet {
            val info = wallet.nextUnusedAddress(KeychainKind.EXTERNAL)
            wallet.persist(persister)
            ReceiveAddress(index = info.index.toInt(), address = info.address.toString())
        }

    override suspend fun freshReceiveAddress(): ReceiveAddress? =
        onWallet {
            if (wallet.listUnusedAddresses(KeychainKind.EXTERNAL).size >= WalletPort.MAX_UNUSED_ADDRESSES) return@onWallet null
            val info = wallet.revealNextAddress(KeychainKind.EXTERNAL)
            wallet.persist(persister)
            ReceiveAddress(index = info.index.toInt(), address = info.address.toString())
        }

    override suspend fun history(): List<HistoryItem> =
        onWallet {
            val tip = wallet.latestCheckpoint().height
            wallet.transactions().mapNotNull { tx -> tx.transaction.computeTxid().use { historyItem(it, tip) } }.sortedNewestFirst()
        }

    override suspend fun transaction(txid: String): HistoryItem? =
        onWallet {
            val id = runCatching { Txid.fromString(txid) }.getOrNull() ?: return@onWallet null
            id.use { historyItem(it, wallet.latestCheckpoint().height) }
        }

    private fun historyItem(
        txid: Txid,
        tip: UInt,
    ): HistoryItem? {
        val details = wallet.txDetails(txid) ?: return null
        val (confirmations, timestamp) =
            when (val position = details.chainPosition) {
                is ChainPosition.Confirmed -> {
                    val block = position.confirmationBlockTime
                    (tip.toInt() - block.blockId.height.toInt() + 1).coerceAtLeast(1) to block.confirmationTime.toLong()
                }

                is ChainPosition.Unconfirmed -> {
                    0 to position.timestamp?.toLong()
                }
            }
        return HistoryItem(
            txid = txid.toString(),
            netSats = details.balanceDelta,
            feeSats = details.fee?.toSat()?.toLong(),
            feeRateSatPerVb = details.feeRate?.toSatPerVbCeil()?.toLong(),
            confirmations = confirmations,
            timestampSec = timestamp,
        )
    }

    /** A full scan the first time (local chain still at genesis), then only revealed scripts. */
    override suspend fun sync() =
        onWallet {
            val update =
                try {
                    if (wallet.latestCheckpoint().height == 0u) {
                        wallet.startFullScan().build().use { chain.fullScan(it) }
                    } else {
                        wallet.startSyncWithRevealedSpks().build().use { chain.sync(it) }
                    }
                } catch (e: EsploraException) {
                    throw ChainUnavailableException(e)
                }
            update.use { wallet.applyUpdate(it) }
            wallet.persist(persister)
            Unit
        }

    @Volatile
    private var closed = false

    override suspend fun feeRates(): FeeOptions =
        onWallet {
            try {
                FeePolicy.fromEstimates(chain.feeEstimates())
            } catch (e: IOException) {
                throw ChainUnavailableException(e)
            }
        }

    override suspend fun planSpend(request: SpendRequest): SpendPlan =
        onWallet {
            val recipient =
                try {
                    Address(request.address, network.toBdkNetwork())
                } catch (e: AddressParseException) {
                    throw SpendException(SpendError.INVALID_ADDRESS, e)
                }
            recipient.use { address ->
                address.scriptPubkey().use { script ->
                    val psbt = buildPsbt(script, request)
                    psbt.use {
                        SpendGuard.check(it, wallet)
                        wallet.persist(persister) // building reveals a change address
                        planOf(it, request, address.toString(), script.toBytes())
                    }
                }
            }
        }

    override suspend fun send(
        plan: SpendPlan,
        seedEntropy: ByteArray,
    ): String =
        onWallet {
            Psbt(plan.psbtBase64).use { psbt ->
                SpendGuard.check(psbt, wallet)
                if (!SeedSigner.sign(wallet, psbt, seedEntropy, network)) throw SpendException(SpendError.SIGNING_FAILED)
                val tx =
                    try {
                        psbt.extractTx() // also refuses an absurd fee rate
                    } catch (e: ExtractTxException) {
                        throw SpendException(SpendError.OTHER, e)
                    }
                tx.use {
                    verifyAgainstPlan(it, psbt, plan)
                    broadcast(it)
                    // Record it now, so balance and history show it before the next sync.
                    wallet.applyUnconfirmedTxs(listOf(UnconfirmedTx(it, System.currentTimeMillis().toULong() / 1000u)))
                    wallet.persist(persister)
                    it.computeTxid().use { txid -> txid.toString() }
                }
            }
        }

    /** The signed transaction must be exactly what the user reviewed. */
    private fun verifyAgainstPlan(
        tx: Transaction,
        psbt: Psbt,
        plan: SpendPlan,
    ) {
        val recipientScript = Address(plan.recipient, network.toBdkNetwork()).use { it.scriptPubkey().use { s -> s.toBytes() } }
        val outputs = tx.output()
        val toRecipient = outputs.filter { it.scriptPubkey.toBytes().contentEquals(recipientScript) }.sumOf { it.value.toSat().toLong() }
        val change =
            outputs
                .filter {
                    wallet.derivationOfSpk(it.scriptPubkey)?.keychain == KeychainKind.INTERNAL
                }.sumOf { it.value.toSat().toLong() }
        val total = outputs.sumOf { it.value.toSat().toLong() }
        val matches =
            toRecipient == plan.amountSats &&
                change == plan.changeSats &&
                total == plan.amountSats + plan.changeSats &&
                psbt.fee().toLong() == plan.feeSats &&
                tx.input().size == plan.inputCount
        if (!matches) throw SpendException(SpendError.PLAN_MISMATCH)
    }

    private fun broadcast(tx: Transaction) {
        try {
            chain.broadcast(tx)
        } catch (e: EsploraException.HttpResponse) {
            // 4xx: the backend answered and refused the transaction; retrying will not help.
            if (e.status.toInt() in 400..499) throw SpendException(SpendError.REJECTED, e)
            throw ChainUnavailableException(e)
        } catch (e: EsploraException) {
            throw ChainUnavailableException(e)
        }
    }

    private fun buildPsbt(
        script: Script,
        request: SpendRequest,
    ): Psbt =
        FeeRate.fromSatPerVb(request.feeRateSatPerVb.toULong()).use { rate ->
            val base = SpendGuard.restrict(TxBuilder()).feeRate(rate)
            val builder =
                when (val amount = request.amount) {
                    is SendAmount.Exact -> Amount.fromSat(amount.sats.toULong()).use { base.addRecipient(script, it) }
                    SendAmount.Max -> base.drainWallet().drainTo(script)
                }
            try {
                builder.finish(wallet)
            } catch (e: CreateTxException) {
                throw SpendException(spendErrorOf(e), e)
            }
        }

    private fun planOf(
        psbt: Psbt,
        request: SpendRequest,
        recipient: String,
        recipientScript: ByteArray,
    ): SpendPlan =
        psbt.extractTxUncheckedFeeRate().use { tx ->
            val outputs = tx.output()
            val change = outputs.filter { wallet.derivationOfSpk(it.scriptPubkey)?.keychain == KeychainKind.INTERNAL }
            SpendPlan(
                psbtBase64 = psbt.serialize(),
                recipient = recipient,
                amountSats =
                    outputs
                        .filter {
                            it.scriptPubkey.toBytes().contentEquals(
                                recipientScript,
                            )
                        }.sumOf { it.value.toSat().toLong() },
                feeSats = psbt.fee().toLong(),
                feeRateSatPerVb = request.feeRateSatPerVb,
                changeSats = change.sumOf { it.value.toSat().toLong() },
                inputCount = tx.input().size,
            )
        }

    private fun spendErrorOf(e: CreateTxException): SpendError =
        when (e) {
            is CreateTxException.InsufficientFunds, is CreateTxException.CoinSelection, is CreateTxException.NoUtxosSelected -> {
                SpendError.INSUFFICIENT_FUNDS
            }

            is CreateTxException.OutputBelowDustLimit -> {
                SpendError.BELOW_DUST
            }

            else -> {
                SpendError.OTHER
            }
        }

    override fun close() {
        closed = true
        CoroutineScope(dispatcher).launch {
            chain.close()
            wallet.close()
            persister.close()
        }
    }

    private suspend fun <T> onWallet(block: () -> T): T =
        withContext(dispatcher) {
            if (closed) throw WalletClosedException()
            block()
        }

    companion object {
        private const val DB_FILE = "spending.sqlite"

        /**
         * Opens the wallet stored in [dir], creating it on first use. The database only caches
         * public chain data, so an unreadable one is replaced by a new wallet (the next sync
         * rescans the chain) instead of locking the user out.
         */
        fun open(
            dir: File,
            descriptors: PublicDescriptors,
            network: SupportedNetwork,
            chain: ChainClient,
            dispatcher: CoroutineDispatcher = serialIoDispatcher(),
        ): BdkWallet {
            check(dir.isDirectory || dir.mkdirs()) { "Cannot create $dir" }
            val db = File(dir, DB_FILE)
            val kind = network.toBdkNetworkKind()
            return Descriptor(descriptors.external, kind).use { external ->
                Descriptor(descriptors.internal, kind).use { internal ->
                    loadOrNull(db, external, internal) { wallet, persister -> BdkWallet(wallet, persister, network, chain, dispatcher) }
                        ?: Persister.newSqlite(db.path).let { persister ->
                            BdkWallet(Wallet(external, internal, network.toBdkNetwork(), persister), persister, network, chain, dispatcher)
                        }
                }
            }
        }

        private fun loadOrNull(
            db: File,
            external: Descriptor,
            internal: Descriptor,
            wrap: (Wallet, Persister) -> BdkWallet,
        ): BdkWallet? {
            if (!db.exists()) return null
            var persister: Persister? = null
            return try {
                persister = Persister.newSqlite(db.path)
                wrap(Wallet.load(external, internal, persister), persister)
            } catch (e: LoadWithPersistException) {
                discard(db, persister)
            } catch (e: PersistenceException) {
                discard(db, persister)
            }
        }

        private fun discard(
            db: File,
            persister: Persister?,
        ): BdkWallet? {
            persister?.close()
            check(db.delete()) { "Cannot replace the unreadable wallet database" }
            return null
        }

        /** A serial view of the IO pool: one BDK call at a time per wallet, no explicit locks. */
        fun serialIoDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}

/** A call reached a wallet after the session locked; it ends the caller's coroutine silently. */
class WalletClosedException : CancellationException("Wallet closed")
