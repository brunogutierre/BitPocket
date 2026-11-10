package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EsploraServerTest {
    private fun clientAnswering(hash: String?) =
        { _: String ->
            object : ChainClient by FakeChainClient() {
                override fun blockHash(height: Int): String = hash ?: throw java.io.IOException("offline")
            }
        }

    @Test
    fun `accepts an https server of the wallet network`() {
        val check =
            EsploraServer.check(
                " https://example.org/signet/api/ ",
                SupportedNetwork.SIGNET,
                clientAnswering(SupportedNetwork.SIGNET.genesisHash),
            )

        assertEquals(ServerCheck.OK, check)
        assertEquals("https://example.org/signet/api", EsploraServer.normalize(" https://example.org/signet/api/ "))
    }

    @Test
    fun `rejects plain http, another network and unreachable servers`() {
        val signet = clientAnswering(SupportedNetwork.SIGNET.genesisHash)

        assertEquals(ServerCheck.NOT_HTTPS, EsploraServer.check("http://example.org/api", SupportedNetwork.SIGNET, signet))
        assertEquals(ServerCheck.NOT_HTTPS, EsploraServer.check("not a url", SupportedNetwork.SIGNET, signet))
        assertEquals(ServerCheck.WRONG_NETWORK, EsploraServer.check("https://example.org/api", SupportedNetwork.TESTNET4, signet))
        assertEquals(
            ServerCheck.UNREACHABLE,
            EsploraServer.check("https://example.org/api", SupportedNetwork.SIGNET, clientAnswering(null)),
        )
    }

    @Test
    fun `the server URL is a settings record`() {
        val settings = WalletSettings(esploraUrl = "https://example.org/api")

        assertEquals(settings, WalletSettings.decode(settings.encode()))
        assertEquals(null, WalletSettings.decode(WalletSettings().encode()).esploraUrl)
    }
}
