package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork

/** Balance of a pocket, in satoshis. */
data class Balance(
    val confirmedSats: Long,
    val pendingSats: Long,
) {
    val totalSats: Long get() = confirmedSats + pendingSats
}

/** A receive address and its derivation index on the external keychain. */
data class ReceiveAddress(
    val index: Int,
    val address: String,
)

/** Read/receive operations the UI needs from a wallet. Free of BDK types on purpose. */
interface WalletPort {
    val network: SupportedNetwork

    suspend fun balance(): Balance

    /** The first revealed address that is still unused (revealed and persisted when needed). */
    suspend fun nextReceiveAddress(): ReceiveAddress

    /**
     * Reveals a brand-new address, or returns null while [MAX_UNUSED_ADDRESSES] revealed ones are
     * still waiting for funds: a restore scans until 20 unused addresses in a row (the stop gap),
     * so staying well below it means a restored wallet always finds every payment.
     */
    suspend fun freshReceiveAddress(): ReceiveAddress?

    companion object {
        const val MAX_UNUSED_ADDRESSES = 10
    }
}

/** The chain backend of a wallet (Esplora). Fee rates and broadcast arrive with sending. */
interface ChainPort {
    /** Fetches new chain data. Throws [ChainUnavailableException] when the backend cannot be reached. */
    suspend fun sync()
}

/** An open pocket: what the UI reads and refreshes. Closed when the session locks. */
interface PocketWallet :
    WalletPort,
    ChainPort,
    AutoCloseable

/** The chain backend failed (offline, timeout, HTTP error); the user can retry. */
class ChainUnavailableException(
    cause: Throwable,
) : Exception("Chain backend unavailable", cause)
