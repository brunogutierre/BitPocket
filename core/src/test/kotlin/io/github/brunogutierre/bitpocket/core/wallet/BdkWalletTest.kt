package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.testing.ChainFixtures
import io.github.brunogutierre.bitpocket.core.testing.Funding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.EsploraException
import org.bitcoindevkit.FullScanRequest
import org.bitcoindevkit.SyncRequest
import org.bitcoindevkit.Update
import org.bitcoindevkit.Wallet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BdkWalletTest {
    @TempDir
    lateinit var dir: File

    private val network = SupportedNetwork.SIGNET
    private val chain = FakeChainClient()
    private val descriptors = SpendingDescriptors.fromEntropy(ByteArray(16), network).use { it.toPublic() }

    @Test
    fun `a new wallet is empty and starts receiving at index 0`() =
        runTest {
            BdkWallet.open(dir, descriptors, network, chain).use { wallet ->
                assertEquals(Balance(0, 0), wallet.balance())
                val address = wallet.nextReceiveAddress()
                assertEquals(0, address.index)
                assertEquals(FIRST_ADDRESS, address.address)
            }
        }

    @Test
    fun `revealed addresses survive reopening the database`() =
        runTest {
            BdkWallet.open(dir, descriptors, network, chain).use { it.nextReceiveAddress() }

            BdkWallet.open(dir, descriptors, network, chain).use { wallet ->
                // Index 0 is revealed but still unused, so it is offered again instead of index 1.
                assertEquals(0, wallet.nextReceiveAddress().index)
            }
            assertTrue(File(dir, "spending.sqlite").isFile)
        }

    @Test
    fun `fresh addresses stop while too many revealed ones are unused`() =
        runTest {
            BdkWallet.open(dir, descriptors, network, chain).use { wallet ->
                val fresh = List(WalletPort.MAX_UNUSED_ADDRESSES) { wallet.freshReceiveAddress() }

                assertEquals((0 until WalletPort.MAX_UNUSED_ADDRESSES).toList(), fresh.map { it?.index })
                assertNull(wallet.freshReceiveAddress())
                assertEquals(0, wallet.nextReceiveAddress().index)
            }
        }

    @Test
    fun `an unreadable database is replaced by a new wallet`() =
        runTest {
            File(dir, "spending.sqlite").writeText("not a database")

            BdkWallet.open(dir, descriptors, network, chain).use { wallet ->
                assertEquals(Balance(0, 0), wallet.balance())
            }
        }

    @Test
    fun `balance splits confirmed and pending coins`() =
        runTest {
            fundedWallet(confirmedAt = 100u, pendingSats = 20_000).use { wallet ->
                assertEquals(Balance(confirmedSats = 50_000, pendingSats = 20_000), wallet.balance())
                // Both funded addresses are used: the next unused one is index 2.
                assertEquals(2, wallet.nextReceiveAddress().index)
            }
        }

    @Test
    fun `the first sync is a full scan and later ones only check revealed scripts`() =
        runTest {
            BdkWallet.open(dir, descriptors, network, chain).use { wallet ->
                assertThrows<ChainUnavailableException> { wallet.sync() }
            }
            fundedWallet(confirmedAt = 100u).use { wallet ->
                assertThrows<ChainUnavailableException> { wallet.sync() }
            }
            assertEquals(listOf("fullScan", "sync"), chain.calls)
        }

    @Test
    fun `closing the wallet closes its chain client and stops later calls`() =
        runTest {
            val wallet = BdkWallet.open(dir, descriptors, network, chain, Dispatchers.Unconfined)

            wallet.close()

            assertTrue(chain.closed)
            assertThrows<WalletClosedException> { wallet.balance() }
        }

    @Test
    fun `the opener derives the wallet directory from public data`() =
        runTest {
            val opener = BdkWalletOpener(dir) { chain }
            val payload = SlotPayload(ByteArray(16), biometricSeed = null, network = network)

            opener.open(payload).use { wallet ->
                assertEquals(FIRST_ADDRESS, wallet.nextReceiveAddress().address)
            }
            val id = WalletFiles.id(network, descriptors)
            assertTrue(File(dir, "wallets/$id/spending.sqlite").isFile)
        }

    @Test
    fun `public descriptors hold no private keys and wallet ids depend on the network`() {
        assertFalse(descriptors.external.contains("prv"))
        assertTrue(descriptors.external.startsWith("tr([") && descriptors.external.contains("tpub"))
        assertNotEquals(WalletFiles.id(SupportedNetwork.SIGNET, descriptors), WalletFiles.id(SupportedNetwork.TESTNET4, descriptors))
        assertEquals(16, WalletFiles.id(network, descriptors).length)
        assertEquals(File(dir, "wallets/abc"), WalletFiles.dir(dir, "abc"))
    }

    /** 50 000 sats confirmed at [confirmedAt] to index 0 (tip 5 blocks later), plus [pendingSats] to index 1. */
    private fun fundedWallet(
        confirmedAt: UInt,
        pendingSats: Long? = null,
    ): BdkWallet {
        val external = Descriptor(descriptors.external, network.toBdkNetworkKind())
        val internal = Descriptor(descriptors.internal, network.toBdkNetworkKind())
        val receive = { index: UInt -> external.deriveAddress(index, network.toBdkNetwork()).scriptPubkey().toBytes() }
        val fundings = listOfNotNull(Funding(receive(0u), 50_000, confirmedAt), pendingSats?.let { Funding(receive(1u), it, null) })
        val persister = ChainFixtures.persister(external, internal, tipHeight = confirmedAt + 5u, fundings = fundings)
        return BdkWallet(Wallet.load(external, internal, persister), persister, network, chain, Dispatchers.Unconfined)
    }

    private companion object {
        // m/86'/1'/0'/0/0 of the all-zero 128-bit entropy ("abandon ... about").
        const val FIRST_ADDRESS = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"
    }
}

/** Records which request was made, then fails like an offline Esplora server. */
class FakeChainClient : ChainClient {
    val calls = mutableListOf<String>()
    var closed = false

    override fun fullScan(request: FullScanRequest): Update {
        calls += "fullScan"
        throw EsploraException.Minreq("offline")
    }

    override fun sync(request: SyncRequest): Update {
        calls += "sync"
        throw EsploraException.Minreq("offline")
    }

    override fun close() {
        closed = true
    }
}
