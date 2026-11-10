package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.Bip39
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.testing.ChainFixtures
import io.github.brunogutierre.bitpocket.core.testing.Funding
import io.github.brunogutierre.bitpocket.core.vault.HeirClaim
import io.github.brunogutierre.bitpocket.core.vault.HeirClaimOpen
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.core.vault.VaultBackup
import io.github.brunogutierre.bitpocket.core.vault.VaultBackupContents
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.vault.VaultKeys
import io.github.brunogutierre.bitpocket.core.vault.VaultRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.Wallet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HeirClaimTest {
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
            HeirTimelock.HOUR,
        )
    private val chain = FakeChainClient()

    /** 60 000 sats confirmed at 100 and 40 000 at 108; the tip is 110 and N = 6. */
    private fun vault(): BdkWallet {
        val descriptors = config.publicDescriptors(network)
        val external = Descriptor(descriptors.external, network.toBdkNetworkKind())
        val internal = Descriptor(descriptors.internal, network.toBdkNetworkKind())
        val address = { i: UInt -> external.deriveAddress(i, network.toBdkNetwork()).scriptPubkey().toBytes() }
        val fundings = listOf(Funding(address(0u), 60_000, 100u), Funding(address(1u), 40_000, 108u))
        val persister = ChainFixtures.persister(external, internal, tipHeight = 110u, fundings = fundings)
        return BdkWallet(Wallet.load(external, internal, persister), persister, network, chain, Dispatchers.Unconfined, config)
    }

    @Test
    fun `only matured coins are swept, on the heir branch, with the heir key alone`() =
        runTest {
            val wallet = vault()
            assertEquals(listOf(true, false), wallet.heirCoins().sortedByDescending { it.sats }.map { it.matured })
            assertEquals(listOf(0, 3), wallet.heirCoins().sortedByDescending { it.sats }.map { it.blocksLeft })

            val plan = wallet.planHeirClaim(DESTINATION, feeRateSatPerVb = 2)
            assertEquals(1, plan.inputCount)
            assertEquals(60_000 - plan.feeSats, plan.amountSats)

            val txid = wallet.sendHeirClaim(plan, heir)

            assertEquals(listOf(txid), chain.broadcasts)
        }

    @Test
    fun `nothing matured, or another key, means no claim`() =
        runTest {
            val wallet = vault()
            val plan = wallet.planHeirClaim(DESTINATION, feeRateSatPerVb = 2)

            assertEquals(SpendError.SIGNING_FAILED, assertThrows<SpendException> { wallet.sendHeirClaim(plan, recovery) }.error)
            assertTrue(chain.broadcasts.isEmpty())

            val young = config.copy(timelock = HeirTimelock.DAY)
            val youngWallet = vault().let { BdkWallet.inMemory(young.publicDescriptors(network), network, chain, young) }
            assertEquals(SpendError.INSUFFICIENT_FUNDS, assertThrows<SpendException> { youngWallet.planHeirClaim(DESTINATION, 2) }.error)
        }

    @Test
    fun `the heir opens the backup with their words and others cannot claim`() {
        val backup = VaultBackup.seal(VaultBackupContents(network, config), mapOf(VaultRole.PHONE to phone, VaultRole.HEIR to heir))

        val opened = HeirClaim.open(backup, heir) { chain }
        assertTrue(opened is HeirClaimOpen.Opened)
        (opened as HeirClaimOpen.Opened).vault.close()
        assertEquals(config, opened.config)

        assertEquals(HeirClaimOpen.NotTheHeir, HeirClaim.open(backup, phone) { chain })
        assertEquals(HeirClaimOpen.NotTheHeir, HeirClaim.open(backup, recovery) { chain })
        assertEquals(HeirClaimOpen.InvalidBackup, HeirClaim.open("junk".toByteArray(), heir) { chain })
    }

    private companion object {
        const val DESTINATION = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
    }
}
