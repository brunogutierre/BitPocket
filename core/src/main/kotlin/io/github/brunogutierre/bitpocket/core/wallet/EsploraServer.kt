package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import java.net.URI

enum class ServerCheck {
    OK,

    /** Not an https URL: Esplora traffic reveals the wallet's addresses, so it must be encrypted. */
    NOT_HTTPS,

    /** The server answered, but with another network's genesis block (e.g. mainnet). */
    WRONG_NETWORK,

    UNREACHABLE,
}

/** Custom Esplora servers (ADR 0010). */
object EsploraServer {
    /** Trims spaces and a trailing slash: `https://host/api/` -> `https://host/api`. */
    fun normalize(url: String): String = url.trim().trimEnd('/')

    /** The URL must be https with a host; the server must serve [network], checked by its genesis block. */
    fun check(
        url: String,
        network: SupportedNetwork,
        clientFor: (String) -> ChainClient,
    ): ServerCheck {
        val normalized = normalize(url)
        val uri = runCatching { URI(normalized) }.getOrNull()
        if (uri?.scheme != "https" || uri.host.isNullOrEmpty()) return ServerCheck.NOT_HTTPS
        return try {
            clientFor(normalized).use { client ->
                if (client.blockHash(0) == network.genesisHash) ServerCheck.OK else ServerCheck.WRONG_NETWORK
            }
        } catch (e: Exception) {
            ServerCheck.UNREACHABLE
        }
    }
}
