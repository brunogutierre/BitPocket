package io.github.brunogutierre.bitpocket.core.wallet

import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.FullScanRequest
import org.bitcoindevkit.SyncRequest
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.Update
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** BDK-typed access to a chain backend, so wallets can be tested with a fake one. */
interface ChainClient : AutoCloseable {
    fun fullScan(request: FullScanRequest): Update

    fun sync(request: SyncRequest): Update

    fun broadcast(transaction: Transaction)

    /** Hash of the block at [height], as hex. Throws on failure (BDK EsploraException). */
    fun blockHash(height: Int): String

    /** Fee estimates: confirmation target in blocks -> sat/vB. Throws [java.io.IOException] on failure. */
    fun feeEstimates(): Map<Int, Double>
}

/**
 * Thin [ChainClient] over BDK's blocking Esplora client. It only forwards calls, so it is left
 * out of the coverage gate and exercised by the opt-in live test (`./gradlew :core:test -Plive`).
 */
class EsploraChainClient(
    private val baseUrl: String,
) : ChainClient {
    private val client = EsploraClient(baseUrl)

    override fun fullScan(request: FullScanRequest): Update = client.fullScan(request, STOP_GAP, PARALLEL_REQUESTS)

    override fun sync(request: SyncRequest): Update = client.sync(request, PARALLEL_REQUESTS)

    override fun broadcast(transaction: Transaction) = client.broadcast(transaction)

    override fun blockHash(height: Int): String = client.getBlockHash(height.toUInt()).use { it.toString() }

    /**
     * Fetched directly instead of through BDK: mempool.space answers this deprecated endpoint with
     * HTTP 203 (still a valid body), which BDK's client rejects. Any 2xx is accepted here.
     */
    override fun feeEstimates(): Map<Int, Double> {
        val connection = URI("$baseUrl/fee-estimates").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return try {
                FeeEstimatesJson.parse(body)
            } catch (e: IllegalArgumentException) {
                throw IOException("Malformed fee estimates", e)
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun close() = client.close()

    private companion object {
        /** Unused addresses in a row before a full scan stops (BIP44 gap limit). */
        const val STOP_GAP = 20uL
        const val PARALLEL_REQUESTS = 4uL
        const val TIMEOUT_MS = 15_000
    }
}
