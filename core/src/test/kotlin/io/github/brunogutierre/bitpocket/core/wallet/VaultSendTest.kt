package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.Bip39
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.testing.ChainFixtures
import io.github.brunogutierre.bitpocket.core.testing.Funding
import io.github.brunogutierre.bitpocket.core.vault.Heartbeat
import io.github.brunogutierre.bitpocket.core.vault.HeartbeatStatus
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.vault.VaultGuard
import io.github.brunogutierre.bitpocket.core.vault.VaultKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Wallet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VaultSendTest {
    private val network = SupportedNetwork.SIGNET
    private val phone =
        Bip39.toEntropy(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
        )
    private val recovery = Bip39.toEntropy("legal winner thank year wave sausage worth useful legal winner thank yellow".split(" "))
    private val heir = Bip39.toEntropy("letter advice cage absurd amount doctor acoustic avoid letter advice cage above".split(" "))
    private val config =
        VaultConfig(
            VaultKeys.publicKey(phone, network),
            VaultKeys.publicKey(recovery, network),
            VaultKeys.publicKey(heir, network),
            HeirTimelock.YEAR,
        )
    private val chain = FakeChainClient()

    /** A watch-only Vault with [confirmed] sats confirmed at 100 (tip 105) and [pending] unconfirmed. */
    private fun vault(
        confirmed: Long,
        pending: Long? = null,
    ): BdkWallet {
        val descriptors = config.publicDescriptors(network)
        val external = Descriptor(descriptors.external, network.toBdkNetworkKind())
        val internal = Descriptor(descriptors.internal, network.toBdkNetworkKind())
        val address = { i: UInt -> external.deriveAddress(i, network.toBdkNetwork()).scriptPubkey().toBytes() }
        val fundings = listOfNotNull(Funding(address(0u), confirmed, 100u), pending?.let { Funding(address(1u), it, null) })
        val persister = ChainFixtures.persister(external, internal, tipHeight = 105u, fundings = fundings)
        return BdkWallet(Wallet.load(external, internal, persister), persister, network, chain, Dispatchers.Unconfined, config)
    }

    private fun request(amount: SendAmount = SendAmount.Exact(30_000)) = SpendRequest(RECIPIENT, amount, feeRateSatPerVb = 2)

    @Test
    fun `phone and recovery keys sign the 2-of-2 branch, which is checked and broadcast`() =
        runTest {
            val wallet = vault(confirmed = 100_000)
            val plan = wallet.planVaultSpend(request())

            val txid = wallet.sendFromVault(plan, phone, recovery)

            assertEquals(listOf(txid), chain.broadcasts)
            assertTrue(wallet.history().first().isPending)
            assertEquals(100_000 - plan.totalSats, wallet.balance().totalSats)
        }

    @Test
    fun `without the right recovery key nothing is broadcast`() =
        runTest {
            val wallet = vault(confirmed = 100_000)
            val plan = wallet.planVaultSpend(request())

            val error = assertThrows<SpendException> { wallet.sendFromVault(plan, phone, heir) }

            assertEquals(SpendError.SIGNING_FAILED, error.error)
            assertTrue(chain.broadcasts.isEmpty())
        }

    @Test
    fun `unconfirmed Vault coins are never spent (the BDK finalize panic is avoided)`() =
        runTest {
            val wallet = vault(confirmed = 20_000, pending = 80_000)

            val error = assertThrows<SpendException> { wallet.planVaultSpend(request()) }
            val max = wallet.planVaultSpend(request(SendAmount.Max))

            assertEquals(SpendError.INSUFFICIENT_FUNDS, error.error)
            assertEquals(1, max.inputCount)
            assertEquals(20_000, max.totalSats)
            wallet.sendFromVault(max, phone, recovery)
        }

    @Test
    fun `the heartbeat counts from the oldest confirmed coin at the synced tip`() =
        runTest {
            // Confirmed at 100, tip 105: 6 confirmations of a one-year timelock.
            assertEquals(Heartbeat(HeartbeatStatus.OK, HeirTimelock.YEAR.blocks - 6), vault(confirmed = 1_000, pending = 500).heartbeat())
        }

    @Test
    fun `the vault pocket refuses single-key spends and the spending one refuses vault spends`() =
        runTest {
            val wallet = vault(confirmed = 100_000)

            assertThrows<IllegalStateException> { wallet.planSpend(request()) }
            assertThrows<IllegalStateException> { wallet.send(wallet.planVaultSpend(request()), phone) }
        }

    @Test
    fun `the guard reads two signatures on the 2-of-2 branch`() =
        runTest {
            val wallet = vault(confirmed = 100_000)
            val plan = wallet.planVaultSpend(request())
            wallet.sendFromVault(plan, phone, recovery)

            // An unsigned transaction has no signatures, and a heir-like sequence is refused.
            Psbt(plan.psbtBase64).use { psbt ->
                psbt.extractTxUncheckedFeeRate().use { tx ->
                    assertEquals(SpendError.WRONG_BRANCH, assertThrows<SpendException> { VaultGuard.checkTwoOfTwo(tx, 52_560) }.error)
                }
            }
            assertEquals(2, VaultGuard.signatureCount(listOf(ByteArray(0), sig(), sig(), ByteArray(100))))
        }

    private fun sig() = ByteArray(71).also { it[0] = 0x30 }

    private companion object {
        const val RECIPIENT = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
    }
}
