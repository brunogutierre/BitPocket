package io.github.brunogutierre.bitpocket.core.wallet

import org.bitcoindevkit.EsploraClient
import org.bitcoindevkit.FullScanRequest
import org.bitcoindevkit.SyncRequest
import org.bitcoindevkit.Update

/** BDK-typed access to a chain backend, so wallets can be tested with a fake one. */
interface ChainClient : AutoCloseable {
    fun fullScan(request: FullScanRequest): Update

    fun sync(request: SyncRequest): Update
}

/**
 * Thin [ChainClient] over BDK's blocking Esplora client. It only forwards calls, so it is left
 * out of the coverage gate and exercised by the opt-in live test (`./gradlew :core:test -Plive`).
 */
class EsploraChainClient(
    baseUrl: String,
) : ChainClient {
    private val client = EsploraClient(baseUrl)

    override fun fullScan(request: FullScanRequest): Update = client.fullScan(request, STOP_GAP, PARALLEL_REQUESTS)

    override fun sync(request: SyncRequest): Update = client.sync(request, PARALLEL_REQUESTS)

    override fun close() = client.close()

    private companion object {
        /** Unused addresses in a row before a full scan stops (BIP44 gap limit). */
        const val STOP_GAP = 20uL
        const val PARALLEL_REQUESTS = 4uL
    }
}
