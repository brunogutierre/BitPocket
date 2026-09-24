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

    /** Reveals (and persists) the next unused receive address. */
    suspend fun nextReceiveAddress(): ReceiveAddress
}

/** Synchronizes wallet state with the chain backend (Esplora). */
interface SyncPort {
    /** Throws on network/backend failure; callers map it to UI error state. */
    suspend fun sync()
}
