package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.send.SpendingStats
import io.github.brunogutierre.bitpocket.core.testing.ChainFixtures
import io.github.brunogutierre.bitpocket.core.testing.Funding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.EsploraException
import org.bitcoindevkit.Wallet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SendTest {
    private val network = SupportedNetwork.SIGNET
    private val descriptors = SpendingDescriptors.fromEntropy(SEED, network).use { it.toPublic() }
    private val external = Descriptor(descriptors.external, network.toBdkNetworkKind())
    private val internal = Descriptor(descriptors.internal, network.toBdkNetworkKind())
    private val chain = FakeChainClient()
    private val persister =
        ChainFixtures.persister(
            external,
            internal,
            tipHeight = 105u,
            fundings = listOf(Funding(external.deriveAddress(0u, network.toBdkNetwork()).scriptPubkey().toBytes(), 50_000, 100u)),
        )
    private val wallet = BdkWallet(Wallet.load(external, internal, persister), persister, network, chain, Dispatchers.Unconfined)

    private suspend fun plan() = wallet.planSpend(SpendRequest(RECIPIENT, SendAmount.Exact(10_000), feeRateSatPerVb = 2))

    @Test
    fun `signs, broadcasts and records the spend as pending`() =
        runTest {
            val plan = plan()

            val txid = wallet.send(plan, SEED.copyOf())

            assertEquals(listOf(txid), chain.broadcasts)
            val sent = wallet.history().first()
            assertEquals(txid, sent.txid)
            assertTrue(sent.isPending)
            assertEquals(-plan.totalSats, sent.netSats)
            assertEquals(50_000 - plan.totalSats, wallet.balance().totalSats)
        }

    @Test
    fun `spending stats count recent sends and remember their recipients`() =
        runTest {
            val before = wallet.spendingStats(144)
            assertEquals(SpendingStats(105, 0, emptySet(), 50_000, before.ownAddresses), before)
            assertTrue(FIRST_ADDRESS in before.ownAddresses)

            val plan = plan()
            wallet.send(plan, SEED.copyOf())

            val stats = wallet.spendingStats(144)
            assertEquals(plan.totalSats, stats.spentInWindowSats)
            assertEquals(setOf(RECIPIENT), stats.pastRecipients)
            assertEquals(50_000 - plan.totalSats, stats.balanceSats)
        }

    @Test
    fun `another seed cannot sign and nothing is broadcast`() =
        runTest {
            val error = assertThrows<SpendException> { wallet.send(plan(), ByteArray(16) { 9 }) }

            assertEquals(SpendError.SIGNING_FAILED, error.error)
            assertTrue(chain.broadcasts.isEmpty())
        }

    @Test
    fun `a plan that does not match its PSBT is never broadcast`() =
        runTest {
            val tampered = plan().let { it.copy(amountSats = it.amountSats - 1_000, changeSats = it.changeSats + 1_000) }

            val error = assertThrows<SpendException> { wallet.send(tampered, SEED.copyOf()) }

            assertEquals(SpendError.PLAN_MISMATCH, error.error)
            assertTrue(chain.broadcasts.isEmpty())
        }

    @Test
    fun `a refused broadcast is reported apart from an unreachable backend, and records nothing`() =
        runTest {
            val plan = plan()
            chain.broadcastError = EsploraException.HttpResponse(400u, "bad-txns-inputs-missingorspent")
            assertEquals(SpendError.REJECTED, assertThrows<SpendException> { wallet.send(plan, SEED.copyOf()) }.error)

            chain.broadcastError = EsploraException.Minreq("offline")
            assertThrows<ChainUnavailableException> { wallet.send(plan, SEED.copyOf()) }

            assertTrue(wallet.history().none { it.isPending })
            assertEquals(50_000, wallet.balance().totalSats)
        }

    private companion object {
        val SEED = ByteArray(16)
        const val RECIPIENT = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"

        // m/86'/1'/0'/0/0 of the all-zero seed: funded by the fixture, so it is revealed.
        const val FIRST_ADDRESS = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"
    }
}
