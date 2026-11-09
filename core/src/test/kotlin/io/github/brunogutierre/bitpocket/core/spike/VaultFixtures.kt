package io.github.brunogutierre.bitpocket.core.spike

import io.github.brunogutierre.bitpocket.core.testing.ChainFixtures
import io.github.brunogutierre.bitpocket.core.testing.Funding
import org.bitcoindevkit.Address
import org.bitcoindevkit.DerivationPath
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.NetworkKind
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Policy
import org.bitcoindevkit.SatisfiableItem
import org.bitcoindevkit.SignOptions
import org.bitcoindevkit.Wallet

/**
 * Keys, descriptors and offline funding for the Vault spike.
 *
 * Vault policy: `wsh(or_d(multi(2,PHONE,RECOVERY),and_v(v:pkh(HEIR),older(N))))`
 * - 2-of-2 PHONE + RECOVERY can spend at any time;
 * - HEIR alone can spend once the coin is N blocks old (relative timelock, BIP68/112).
 */
object VaultFixtures {
    val NETWORK = ChainFixtures.NETWORK

    /** ~1 year of blocks; the relative timelock maximum is 65535 blocks. */
    const val HEIR_TIMELOCK_BLOCKS = 52_560u

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
     * address, anchored in a fake block at [confirmedAt] (see [ChainFixtures]).
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
    ): Persister =
        ChainFixtures.persister(
            vaultDescriptor("0"),
            vaultDescriptor("1"),
            tipHeight = confirmedAt,
            fundings = listOf(Funding(firstVaultAddress().scriptPubkey().toBytes(), sats, confirmedAt)),
        )

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
