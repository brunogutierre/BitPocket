package io.github.brunogutierre.bitpocket.core.spike

import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.HEIR
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.HEIR_TIMELOCK_BLOCKS
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.PHONE
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.RECOVERY
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.branchesOf
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.firstVaultAddress
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.fundedWallet
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.fundedWatchOnlyWallet
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.rootPolicy
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.signOptions
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.signingWallet
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.vaultDescriptor
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures.watchOnlyWallet
import org.bitcoindevkit.Amount
import org.bitcoindevkit.CreateTxException
import org.bitcoindevkit.DescriptorException
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.SatisfiableItem
import org.bitcoindevkit.SignersContainer
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Wallet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * M0 gate spike: can BDK 3.1.0 (Kotlin bindings) hold and spend the Vault policy?
 * See docs/adr/0002-vault-policy-and-signing.md for the conclusions.
 */
class VaultSpikeTest {
    @Test
    fun `parses the vault miniscript as a watch-only BIP389 multipath descriptor`() {
        val descriptor = vaultDescriptor("<0;1>")
        val watchOnly = watchOnlyWallet()
        val phoneWallet = signingWallet(PHONE)

        assertTrue(descriptor.isMultipath())
        val first = watchOnly.revealNextAddress(KeychainKind.EXTERNAL).address.toString()
        // P2WSH on test networks; same script as the per-keychain wallet holding private keys.
        assertTrue(first.startsWith("tb1q") && first.length == 62, first)
        assertEquals(first, phoneWallet.revealNextAddress(KeychainKind.EXTERNAL).address.toString())
    }

    @Test
    fun `rejects multipath descriptors that contain private keys`() {
        // rust-miniscript: "Can't make an extended private key with multiple paths into a public key".
        assertThrows<DescriptorException> { vaultDescriptor("<0;1>", signers = setOf(PHONE)) }
    }

    @Test
    fun `exposes the or_d as a 1-of-2 policy that requires choosing a path`() {
        val root = rootPolicy(watchOnlyWallet())
        val item = root.item() as SatisfiableItem.Thresh
        val (multisig, heir) = branchesOf(root)

        assertEquals(1uL, item.threshold)
        assertTrue(root.requiresPath())
        assertEquals(2uL, (multisig.item() as SatisfiableItem.Multisig).threshold)
        assertTrue(branchesOf(heir).any { it.item() == SatisfiableItem.RelativeTimelock(HEIR_TIMELOCK_BLOCKS) })
    }

    @Test
    fun `2-of-2 path is final only after phone and recovery both sign`() {
        // Two separate wallets, each holding one private key (as on two devices).
        val phoneWallet = fundedWallet(PHONE, sats = FUNDING_SATS, confirmedAt = FUNDING_HEIGHT)
        val recoveryWallet = fundedWallet(RECOVERY, sats = FUNDING_SATS, confirmedAt = FUNDING_HEIGHT)
        assertEquals(FUNDING_SATS.toULong(), phoneWallet.balance().total.toSat())

        val psbt = buildSpend(phoneWallet, branch = MULTISIG_BRANCH)

        assertFalse(phoneWallet.sign(psbt, signOptions()), "one signature must not finalize a 2-of-2")
        assertTrue(recoveryWallet.sign(psbt, signOptions()), "the second signature finalizes")
        val tx = psbt.extractTx()
        assertEquals(1, tx.input().size)
        assertTrue(tx.input().single().sequence > HEIR_TIMELOCK_BLOCKS, "no relative timelock on this path")
    }

    @Test
    fun `watch-only multipath wallet can be signed by external signers`() {
        // Keeps private keys out of the persisted wallet: signers live only during signing.
        val watchOnly = fundedWatchOnlyWallet(sats = FUNDING_SATS, confirmedAt = FUNDING_HEIGHT)
        val psbt = buildSpend(watchOnly, branch = MULTISIG_BRANCH)

        assertFalse(watchOnly.sign(psbt, signOptions()), "watch-only wallet has no keys")
        val signers =
            listOf(PHONE, RECOVERY).map { key ->
                SignersContainer.fromDescriptor(vaultDescriptor("0", signers = setOf(key)))
            }
        assertTrue(watchOnly.signWithSigners(psbt, signers, signOptions()))
        assertEquals(1, psbt.extractTx().input().size)
    }

    @Test
    fun `heir cannot build a spend without choosing the policy path`() {
        val heirWallet = fundedWallet(HEIR, sats = FUNDING_SATS, confirmedAt = FUNDING_HEIR_HEIGHT)
        val destination = firstVaultAddress().scriptPubkey()

        assertThrows<CreateTxException.SpendingPolicyRequired> {
            TxBuilder()
                .addRecipient(destination, Amount.fromSat(SEND_SATS))
                .feeRate(FeeRate.fromSatPerVb(2u))
                .finish(heirWallet)
        }
    }

    @Test
    fun `heir path finalizes with nSequence N but BDK does not check maturity`() {
        val heirWallet = fundedWallet(HEIR, sats = FUNDING_SATS, confirmedAt = FUNDING_HEIR_HEIGHT)

        val psbt = buildSpend(heirWallet, branch = HEIR_BRANCH)

        // Only one confirmation: the timelock has NOT matured, yet BDK finalizes. Its finalizer
        // accepts older(N) when either the PSBT nSequence >= N or tip >= confirmation + N, so the
        // maturity gate is left to consensus (the network rejects the tx until it is BIP68-final).
        assertTrue(heirWallet.sign(psbt, signOptions(assumeHeight = FUNDING_HEIR_HEIGHT)))
        val input = psbt.extractTx().input().single()
        assertEquals(HEIR_TIMELOCK_BLOCKS, input.sequence, "TxBuilder sets nSequence = N for the heir branch")
        // Dissatisfied multi (3 empty pushes) + heir signature + heir pubkey + witness script.
        assertEquals(6, input.witness.size)
    }

    @Test
    fun `heir choosing the 2-of-2 branch gets a tx that consensus would reject once mature`() {
        val heirWallet = fundedWallet(HEIR, sats = FUNDING_SATS, confirmedAt = FUNDING_HEIR_HEIGHT)
        val matured = FUNDING_HEIR_HEIGHT + HEIR_TIMELOCK_BLOCKS

        val psbt = buildSpend(heirWallet, branch = MULTISIG_BRANCH)

        assertFalse(heirWallet.sign(psbt, signOptions(assumeHeight = matured - 1u)))
        // Pitfall: past maturity the finalizer satisfies older(N) from the chain tip alone and
        // uses the heir branch, but nSequence was not set to N, so OP_CHECKSEQUENCEVERIFY fails.
        assertTrue(heirWallet.finalizePsbt(psbt, signOptions(assumeHeight = matured)))
        assertTrue(
            psbt
                .extractTx()
                .input()
                .single()
                .sequence > HEIR_TIMELOCK_BLOCKS,
        )
    }

    private fun buildSpend(
        wallet: Wallet,
        branch: ULong,
    ): Psbt {
        val destination = signingWallet(PHONE).revealNextAddress(KeychainKind.EXTERNAL).address

        // The branch must be chosen for BOTH keychains, even if only external coins are spent:
        // otherwise BDK fails with CreateTxException.SpendingPolicyRequired(kind = INTERNAL).
        fun pathFor(keychain: KeychainKind) = mapOf(rootPolicy(wallet, keychain).id() to listOf(branch))
        return TxBuilder()
            .addRecipient(destination.scriptPubkey(), Amount.fromSat(SEND_SATS))
            .feeRate(FeeRate.fromSatPerVb(2u))
            .policyPath(pathFor(KeychainKind.EXTERNAL), KeychainKind.EXTERNAL)
            .policyPath(pathFor(KeychainKind.INTERNAL), KeychainKind.INTERNAL)
            .finish(wallet)
    }

    private companion object {
        const val FUNDING_SATS = 100_000L
        const val SEND_SATS = 40_000uL
        const val FUNDING_HEIGHT = 200_000u
        const val FUNDING_HEIR_HEIGHT = 10_000u
        const val MULTISIG_BRANCH = 0uL
        const val HEIR_BRANCH = 1uL
    }
}
