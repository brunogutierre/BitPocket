package io.github.brunogutierre.bitpocket.core.network

import org.bitcoindevkit.Network
import org.bitcoindevkit.NetworkKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class SupportedNetworkTest {
    @ParameterizedTest
    @EnumSource(SupportedNetwork::class)
    fun `never maps to mainnet`(network: SupportedNetwork) {
        assertNotEquals(Network.BITCOIN, network.toBdkNetwork())
        assertEquals(NetworkKind.TEST, network.toBdkNetworkKind())
    }

    @Test
    fun `maps each entry to the matching BDK network`() {
        assertEquals(Network.SIGNET, SupportedNetwork.SIGNET.toBdkNetwork())
        assertEquals(Network.TESTNET4, SupportedNetwork.TESTNET4.toBdkNetwork())
    }

    @Test
    fun `uses mempool space Esplora endpoints`() {
        assertEquals("https://mempool.space/signet/api", SupportedNetwork.SIGNET.esploraBaseUrl)
        assertEquals("https://mempool.space/testnet4/api", SupportedNetwork.TESTNET4.esploraBaseUrl)
    }

    @Test
    fun `defaults to signet`() {
        assertEquals(SupportedNetwork.SIGNET, SupportedNetwork.DEFAULT)
        assertEquals("Signet", SupportedNetwork.DEFAULT.displayName)
    }
}
