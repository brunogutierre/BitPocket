package io.github.brunogutierre.bitpocket.core.spike

import org.bitcoindevkit.Address
import org.bitcoindevkit.Anchor
import org.bitcoindevkit.BlockHash
import org.bitcoindevkit.BlockId
import org.bitcoindevkit.ChainChange
import org.bitcoindevkit.ChangeSet
import org.bitcoindevkit.ConfirmationBlockTime
import org.bitcoindevkit.DerivationPath
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.IndexerChangeSet
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.LocalChainChangeSet
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.Network
import org.bitcoindevkit.NetworkKind
import org.bitcoindevkit.Persistence
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Policy
import org.bitcoindevkit.SatisfiableItem
import org.bitcoindevkit.SignOptions
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxGraphChangeSet
import org.bitcoindevkit.Wallet
import java.io.ByteArrayOutputStream

/**
 * Keys, descriptors and offline funding for the Vault spike.
 *
 * Vault policy: `wsh(or_d(multi(2,PHONE,RECOVERY),and_v(v:pkh(HEIR),older(N))))`
 * - 2-of-2 PHONE + RECOVERY can spend at any time;
 * - HEIR alone can spend once the coin is N blocks old (relative timelock, BIP68/112).
 */
object VaultFixtures {
    val NETWORK = Network.SIGNET

    /** ~1 year of blocks; the relative timelock maximum is 65535 blocks. */
    const val HEIR_TIMELOCK_BLOCKS = 52_560u

    private const val SIGNET_GENESIS = "00000008819873e925422c1ff0f99f7cc9bbb232af63a077a480a3633bee1ef6"

    // BIP39 test-vector mnemonics: public, test-only keys.
    val PHONE = VaultKey("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
    val RECOVERY = VaultKey("legal winner thank year wave sausage worth useful legal winner thank yellow")
    val HEIR = VaultKey("letter advice cage absurd amount doctor acoustic avoid letter advice cage above")

    /**
     * Vault descriptor for one keychain [branch] (`0` receive, `1` change, or BIP389
     * multipath `<0;1>` for both), with private keys only for the given [signers].
     */
    fun vaultDescriptor(
        branch: String,
        signers: Set<VaultKey> = emptySet(),
    ): Descriptor {
        fun key(k: VaultKey) = (if (k in signers) k.xprv else k.xpub) + "/$branch/*"
        val miniscript =
            "wsh(or_d(multi(2,${key(PHONE)},${key(RECOVERY)})," +
                "and_v(v:pkh(${key(HEIR)}),older($HEIR_TIMELOCK_BLOCKS))))"
        return Descriptor(miniscript, NetworkKind.TEST)
    }

    /** Watch-only wallet from a single BIP389 multipath descriptor (receive + change). */
    fun watchOnlyWallet(): Wallet = Wallet.createFromTwoPathDescriptor(vaultDescriptor("<0;1>"), NETWORK, Persister.newInMemory())

    /** Wallet holding the private keys of [signers]; needs one descriptor per keychain. */
    fun signingWallet(vararg signers: VaultKey): Wallet =
        Wallet(
            vaultDescriptor("0", signers.toSet()),
            vaultDescriptor("1", signers.toSet()),
            NETWORK,
            Persister.newInMemory(),
        )

    /** First receive address of the vault (identical for every cosigner's wallet). */
    fun firstVaultAddress(): Address = vaultDescriptor("0").deriveAddress(0u, NETWORK)

    /**
     * Offline funding with a CONFIRMED coin: a fake transaction paying [sats] to the first vault
     * address, anchored in a fake block at [confirmedAt]. `Update` has no public constructor, so
     * the wallet is loaded (`Wallet.load`) from a hand-built `ChangeSet` through a custom
     * `Persister`. The input is fictitious: the tx is only good for PSBT building and signing.
     *
     * Unconfirmed funding (`Wallet.applyUnconfirmedTxs`) is NOT usable with this policy:
     * see [signOptions].
     */
    fun fundedWallet(
        vararg signers: VaultKey,
        sats: Long,
        confirmedAt: UInt,
    ): Wallet =
        Wallet.load(
            vaultDescriptor("0", signers.toSet()),
            vaultDescriptor("1", signers.toSet()),
            fundedPersister(sats, confirmedAt),
        )

    /** Same as [fundedWallet], but watch-only from the multipath descriptor. */
    fun fundedWatchOnlyWallet(
        sats: Long,
        confirmedAt: UInt,
    ): Wallet = Wallet.loadFromTwoPathDescriptor(vaultDescriptor("<0;1>"), fundedPersister(sats, confirmedAt))

    private fun fundedPersister(
        sats: Long,
        confirmedAt: UInt,
    ): Persister {
        val funding = Transaction(fakeFundingTxBytes(firstVaultAddress().scriptPubkey().toBytes(), sats))
        val tip = BlockId(confirmedAt, BlockHash.fromString("%064x".format(confirmedAt.toLong())))
        val changeSet =
            ChangeSet.fromAggregate(
                descriptor = vaultDescriptor("0").asPublic(),
                changeDescriptor = vaultDescriptor("1").asPublic(),
                network = NETWORK,
                localChain =
                    LocalChainChangeSet(
                        listOf(ChainChange(0u, BlockHash.fromString(SIGNET_GENESIS)), ChainChange(tip.height, tip.hash)),
                    ),
                txGraph =
                    TxGraphChangeSet(
                        txs = listOf(funding),
                        txouts = emptyMap(),
                        anchors = listOf(Anchor(ConfirmationBlockTime(tip, confirmationTime = 1u), funding.computeTxid())),
                        lastSeen = emptyMap(),
                        firstSeen = emptyMap(),
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

    /**
     * BDK defaults, plus [assumeHeight] (the chain tip used to evaluate `older()`).
     *
     * Finalizing a spend of an UNCONFIRMED coin with a descriptor containing `older()` panics in
     * bdk_wallet 3.1.0 (`Older::check_older`: unconfirmed height u32::MAX + N -> "Overflowing
     * addition"), and the panic aborts the whole JVM process instead of throwing.
     */
    fun signOptions(assumeHeight: UInt? = null) =
        SignOptions(
            trustWitnessUtxo = false,
            assumeHeight = assumeHeight,
            allowAllSighashes = false,
            tryFinalize = true,
            signWithTapInternalKey = true,
            allowGrinding = true,
        )

    /** The first-level policy node: `or_d` becomes a 1-of-2 threshold of [multisig, heir]. */
    fun rootPolicy(
        wallet: Wallet,
        keychain: KeychainKind = KeychainKind.EXTERNAL,
    ): Policy = requireNotNull(wallet.policies(keychain))

    fun branchesOf(policy: Policy): List<Policy> = (policy.item() as SatisfiableItem.Thresh).items

    /** Legacy (non-witness) serialization: 1 fake input, 1 output, version 2, locktime 0. */
    private fun fakeFundingTxBytes(
        scriptPubkey: ByteArray,
        sats: Long,
    ): ByteArray =
        ByteArrayOutputStream()
            .apply {
                writeLe(2, 4) // version
                write(1) // input count
                write(ByteArray(32) { 0x11 }) // previous txid (fictitious)
                writeLe(0, 4) // previous vout
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

/** A cosigner key at the BIP48 P2WSH account path m/48'/1'/0'/2' (test coin type). */
class VaultKey(
    mnemonic: String,
) {
    val xprv: String
    val xpub: String

    init {
        val account =
            DescriptorSecretKey(NetworkKind.TEST, Mnemonic.fromString(mnemonic), null)
                .derive(DerivationPath("m/48'/1'/0'/2'"))
        xprv = account.toString()
        xpub = account.asPublic().toString()
    }
}
