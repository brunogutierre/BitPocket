package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.testing.ChainFixtures
import io.github.brunogutierre.bitpocket.core.testing.Funding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.FeeRate
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Wallet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SpendPlanTest {
    private val network = SupportedNetwork.SIGNET
    private val descriptors = SpendingDescriptors.fromEntropy(ByteArray(16), network).use { it.toPublic() }
    private val external = Descriptor(descriptors.external, network.toBdkNetworkKind())
    private val internal = Descriptor(descriptors.internal, network.toBdkNetworkKind())

    /** 50 000 sats confirmed at height 100 (receive index 0), 20 000 pending (index 1); tip 105. */
    private val persister =
        ChainFixtures.persister(
            external,
            internal,
            tipHeight = 105u,
            fundings = listOf(Funding(receiveScript(0u), 50_000, 100u), Funding(receiveScript(1u), 20_000, null)),
        )
    private val bdkWallet = Wallet.load(external, internal, persister)
    private val wallet = BdkWallet(bdkWallet, persister, network, FakeChainClient(), Dispatchers.Unconfined)

    private fun receiveScript(index: UInt) = external.deriveAddress(index, network.toBdkNetwork()).scriptPubkey().toBytes()

    private fun request(
        amount: SendAmount,
        address: String = RECIPIENT,
    ) = SpendRequest(address, amount, feeRateSatPerVb = 2)

    @Test
    fun `spends only the confirmed coin, with change back to the wallet`() =
        runTest {
            val plan = wallet.planSpend(request(SendAmount.Exact(10_000)))

            assertEquals(RECIPIENT, plan.recipient)
            assertEquals(10_000, plan.amountSats)
            assertEquals(1, plan.inputCount)
            assertTrue(plan.feeSats in 200..1_000, "fee ${plan.feeSats}")
            assertEquals(50_000 - 10_000 - plan.feeSats, plan.changeSats)
            assertEquals(10_000 + plan.feeSats, plan.totalSats)
            Psbt(plan.psbtBase64).use { assertEquals(plan.feeSats, it.fee().toLong()) }
        }

    @Test
    fun `pending coins never count as spendable`() =
        runTest {
            // The balance is 70 000, but only 50 000 are confirmed.
            val error = assertThrows<SpendException> { wallet.planSpend(request(SendAmount.Exact(60_000))) }

            assertEquals(SpendError.INSUFFICIENT_FUNDS, error.error)
        }

    @Test
    fun `max sends every confirmed sat minus the fee, without change`() =
        runTest {
            val plan = wallet.planSpend(request(SendAmount.Max))

            assertEquals(50_000 - plan.feeSats, plan.amountSats)
            assertEquals(0, plan.changeSats)
            assertEquals(50_000, plan.totalSats)
        }

    @Test
    fun `rejects dust, other networks and malformed addresses`() =
        runTest {
            assertEquals(SpendError.BELOW_DUST, assertThrows<SpendException> { wallet.planSpend(request(SendAmount.Exact(100))) }.error)
            assertEquals(
                SpendError.INVALID_ADDRESS,
                assertThrows<SpendException> { wallet.planSpend(request(SendAmount.Exact(1_000), MAINNET)) }.error,
            )
            assertEquals(
                SpendError.INVALID_ADDRESS,
                assertThrows<SpendException> { wallet.planSpend(request(SendAmount.Exact(1_000), "nope")) }.error,
            )
        }

    @Test
    fun `the guard rejects a PSBT that spends an unconfirmed coin`() {
        val pending = bdkWallet.listUnspent().single { it.chainPosition is org.bitcoindevkit.ChainPosition.Unconfirmed }
        val psbt =
            FeeRate.fromSatPerVb(2u).use { rate ->
                TxBuilder()
                    .addUtxo(pending.outpoint)
                    .manuallySelectedOnly()
                    .drainTo(bdkWallet.peekAddress(KeychainKind.INTERNAL, 0u).address.scriptPubkey())
                    .feeRate(rate)
                    .finish(bdkWallet)
            }

        val error = assertThrows<SpendException> { SpendGuard.check(psbt, bdkWallet) }

        assertEquals(SpendError.UNSAFE_INPUT, error.error)
    }

    private companion object {
        // BIP173 testnet P2WPKH vector: a test-network address outside this wallet.
        const val RECIPIENT = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"

        // BIP173 mainnet P2WPKH vector.
        const val MAINNET = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
    }
}
