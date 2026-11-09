package io.github.brunogutierre.bitpocket.core.testing

import org.bitcoindevkit.Anchor
import org.bitcoindevkit.BlockHash
import org.bitcoindevkit.BlockId
import org.bitcoindevkit.ChainChange
import org.bitcoindevkit.ChangeSet
import org.bitcoindevkit.ConfirmationBlockTime
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.IndexerChangeSet
import org.bitcoindevkit.LocalChainChangeSet
import org.bitcoindevkit.Network
import org.bitcoindevkit.Persistence
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxGraphChangeSet
import java.io.ByteArrayOutputStream

/** A fake payment of [sats] to [scriptPubkey], confirmed at [confirmedAt], or pending when null. */
class Funding(
    val scriptPubkey: ByteArray,
    val sats: Long,
    val confirmedAt: UInt?,
)

/**
 * Offline chain data for wallet tests (Signet).
 *
 * BDK's `Update` has no public constructor, so funded wallets are loaded (`Wallet.load`) from a
 * hand-built [ChangeSet] through a custom [Persister]. The funding transactions spend fictitious
 * outpoints: they are good for balances, history and PSBT building/signing, never for broadcast.
 */
object ChainFixtures {
    val NETWORK = Network.SIGNET
    private const val SIGNET_GENESIS = "00000008819873e925422c1ff0f99f7cc9bbb232af63a077a480a3633bee1ef6"

    /** Pending fundings are "seen" in the mempool at this Unix time. */
    const val SEEN_AT = 1_700_000_000uL

    /** A persister whose stored wallet holds [fundings], with the local chain tip at [tipHeight]. */
    fun persister(
        descriptor: Descriptor,
        changeDescriptor: Descriptor,
        tipHeight: UInt,
        fundings: List<Funding>,
    ): Persister {
        val txs = fundings.mapIndexed { i, funding -> Transaction(fakeTxBytes(i, funding.scriptPubkey, funding.sats)) }
        val heights = (fundings.mapNotNull { it.confirmedAt } + tipHeight).toSortedSet()
        val anchors =
            fundings.zip(txs).mapNotNull { (funding, tx) ->
                funding.confirmedAt?.let { Anchor(ConfirmationBlockTime(blockAt(it), confirmationTime = it.toULong()), tx.computeTxid()) }
            }
        val pending =
            fundings.zip(txs).filter { (funding, _) -> funding.confirmedAt == null }.associate { (_, tx) ->
                tx.computeTxid() to
                    SEEN_AT
            }
        val changeSet =
            ChangeSet.fromAggregate(
                descriptor = descriptor.asPublic(),
                changeDescriptor = changeDescriptor.asPublic(),
                network = NETWORK,
                localChain =
                    LocalChainChangeSet(
                        listOf(ChainChange(0u, BlockHash.fromString(SIGNET_GENESIS))) +
                            heights.map { blockAt(it) }.map { ChainChange(it.height, it.hash) },
                    ),
                txGraph =
                    TxGraphChangeSet(
                        txs = txs,
                        txouts = emptyMap(),
                        anchors = anchors,
                        lastSeen = pending,
                        firstSeen = pending,
                        lastEvicted = emptyMap(),
                    ),
                indexer = IndexerChangeSet(emptyMap()),
            )
        return Persister.custom(
            object : Persistence {
                override fun initialize(): ChangeSet = changeSet

                override fun persist(changeset: ChangeSet) = Unit
            },
        )
    }

    private fun blockAt(height: UInt) = BlockId(height, BlockHash.fromString("%064x".format(height.toLong())))

    /** Legacy serialization: 1 fictitious input (vout [index] keeps txids distinct), 1 output, version 2. */
    private fun fakeTxBytes(
        index: Int,
        scriptPubkey: ByteArray,
        sats: Long,
    ): ByteArray =
        ByteArrayOutputStream()
            .apply {
                writeLe(2, 4) // version
                write(1) // input count
                write(ByteArray(32) { 0x11 }) // previous txid (fictitious)
                writeLe(index.toLong(), 4) // previous vout
                write(0) // empty scriptSig
                writeLe(0xFFFFFFFFL, 4) // sequence
                write(1) // output count
                writeLe(sats, 8) // value
                write(scriptPubkey.size) // script length (< 0xFD, fits a 1-byte varint)
                write(scriptPubkey)
                writeLe(0, 4) // locktime
            }.toByteArray()

    private fun ByteArrayOutputStream.writeLe(
        value: Long,
        bytes: Int,
    ) = repeat(bytes) { write(((value shr (8 * it)) and 0xFF).toInt()) }
}
