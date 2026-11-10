package io.github.brunogutierre.bitpocket.core.vault

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.Bip39
import io.github.brunogutierre.bitpocket.core.spike.VaultFixtures
import org.bitcoindevkit.Descriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VaultConfigTest {
    private val network = SupportedNetwork.SIGNET

    private fun entropy(words: String) = Bip39.toEntropy(words.split(" "))

    private val phone = entropy("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
    private val recovery = entropy("legal winner thank year wave sausage worth useful legal winner thank yellow")
    private val heir = entropy("letter advice cage absurd amount doctor acoustic avoid letter advice cage above")
    private val config =
        VaultConfig(
            VaultKeys.publicKey(phone, network),
            VaultKeys.publicKey(recovery, network),
            VaultKeys.publicKey(heir, network),
            HeirTimelock.YEAR,
        )

    @Test
    fun `keys are BIP48 test account keys with their origin`() {
        assertTrue(config.phoneKey.startsWith("[73c5da0a/48'/1'/0'/2']tpub"), config.phoneKey)
        assertEquals(VaultFixtures.PHONE.xpub, config.phoneKey)
        assertTrue(VaultKeys.secretKey(phone, network).contains("tprv"))
    }

    @Test
    fun `builds the same vault as the M0 spike`() {
        val ours = Descriptor(config.descriptor("0"), network.toBdkNetworkKind())

        assertEquals(VaultFixtures.firstVaultAddress().toString(), ours.deriveAddress(0u, network.toBdkNetwork()).toString())
        assertTrue(Descriptor(config.descriptor("<0;1>"), network.toBdkNetworkKind()).isMultipath())
    }

    @Test
    fun `signer descriptors carry private keys only for the given roles and a single branch`() {
        val secret = VaultKeys.secretKey(phone, network)
        val signer = config.descriptor("1", mapOf(VaultRole.PHONE to secret))

        assertTrue(signer.contains("tprv") && signer.contains(config.recoveryKey))
        assertThrows<IllegalArgumentException> { config.descriptor("<0;1>", mapOf(VaultRole.PHONE to secret)) }
    }

    @Test
    fun `round-trips its record and validates keys`() {
        assertEquals(config, VaultConfig.decode(config.encode()))
        assertEquals(listOf(6, 144, 4_320, 52_560), HeirTimelock.entries.map { it.blocks })
        assertThrows<IllegalArgumentException> { config.copy(heirKey = config.phoneKey) }
        assertThrows<IllegalArgumentException> { config.copy(heirKey = VaultKeys.secretKey(heir, network)) }
        assertThrows<IllegalArgumentException> { VaultConfig.decode(config.encode().copyOf(10)) }
    }
}
