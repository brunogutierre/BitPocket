package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.bitcoindevkit.Mnemonic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class SpendingDescriptorsTest {
    private fun descriptors(
        network: SupportedNetwork,
        passphrase: String? = null,
    ) = SpendingDescriptors.fromMnemonic(Mnemonic.fromString(TEST_MNEMONIC), network, passphrase)

    @ParameterizedTest
    @EnumSource(SupportedNetwork::class)
    fun `first receive address matches the BIP86 testnet vector`(network: SupportedNetwork) {
        val address = descriptors(network).external.deriveAddress(0u, network.toBdkNetwork())

        assertEquals(BIP86_TESTNET_FIRST_ADDRESS, address.toString())
    }

    @Test
    fun `uses BIP86 test-coin paths with distinct receive and change keychains`() {
        val descriptors = descriptors(SupportedNetwork.DEFAULT)
        val external = descriptors.external.toString()
        val internal = descriptors.internal.toString()

        assertTrue(external.startsWith("tr([$TEST_FINGERPRINT/86'/1'/0']"), external)
        assertTrue(external.contains("/0/*)"), external)
        assertTrue(internal.contains("/1/*)"), internal)
        assertNotEquals(external, internal)
    }

    @Test
    fun `toString redacts private keys`() {
        val descriptors = descriptors(SupportedNetwork.DEFAULT)

        assertFalse(descriptors.external.toString().contains("prv"))
        assertFalse(descriptors.internal.toString().contains("prv"))
    }

    @ParameterizedTest
    @EnumSource(SupportedNetwork::class)
    fun `never produces mainnet keys or addresses`(network: SupportedNetwork) {
        val descriptors = descriptors(network)
        val secrets = descriptors.external.toStringWithSecret() + descriptors.internal.toStringWithSecret()
        val addresses =
            (0u..4u).flatMap { index ->
                listOf(descriptors.external, descriptors.internal).map {
                    it.deriveAddress(index, network.toBdkNetwork()).toString()
                }
            }

        assertTrue(secrets.contains("tprv"))
        assertFalse(secrets.contains("xprv"))
        assertTrue(addresses.all { it.startsWith("tb1p") }, addresses.toString())
        assertFalse(addresses.any { it.startsWith("bc1") })
    }

    @Test
    fun `passphrase changes the derived wallet`() {
        val withoutPassphrase = descriptors(SupportedNetwork.DEFAULT).external.toString()
        val withPassphrase = descriptors(SupportedNetwork.DEFAULT, passphrase = "TREZOR").external.toString()

        assertNotEquals(withoutPassphrase, withPassphrase)
    }

    private companion object {
        const val TEST_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // Master key fingerprint of TEST_MNEMONIC (no passphrase).
        const val TEST_FINGERPRINT = "73c5da0a"

        // m/86'/1'/0'/0/0 for TEST_MNEMONIC, independent of BDK: bip_utils BIP86 test vectors,
        // "Bitcoin test net" default address (seed 5eb00bbd... = TEST_MNEMONIC without passphrase).
        // https://github.com/ebellocchia/bip_utils/blob/master/tests/bip/bip86/test_bip86.py
        const val BIP86_TESTNET_FIRST_ADDRESS = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"
    }
}
