package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendPort
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.send.SpendingStats
import io.github.brunogutierre.bitpocket.core.vault.Heartbeat

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

    /** Every wallet transaction, newest first. */
    suspend fun history(): List<HistoryItem>

    /** One transaction by id, or null if the wallet does not know it. */
    suspend fun transaction(txid: String): HistoryItem?

    /** Recent spending for the guardrails, over the last [windowBlocks] blocks of the synced tip. */
    suspend fun spendingStats(windowBlocks: Int): SpendingStats

    companion object {
        const val MAX_UNUSED_ADDRESSES = 10
    }
}

/** The chain backend of a wallet (Esplora). Calls throw [ChainUnavailableException] when it cannot be reached. */
interface ChainPort {
    /** Fetches new chain data. */
    suspend fun sync()

    /** Current fee rates for fast, normal and slow confirmation. */
    suspend fun feeRates(): FeeOptions
}

/** An open pocket: what the UI reads, refreshes and spends from. Closed when the session locks. */
interface PocketWallet :
    WalletPort,
    ChainPort,
    SpendPort,
    AutoCloseable

/** Spending from the Vault: the 2-of-2 branch, signed by PHONE and RECOVERY (ADR 0002, 0007). */
interface VaultPort {
    /** Throws `SpendException`, like `SpendPort.planSpend`. */
    suspend fun planVaultSpend(request: SpendRequest): SpendPlan

    /** The callers wipe both seeds. Throws `SpendException` or [ChainUnavailableException]. */
    suspend fun sendFromVault(
        plan: SpendPlan,
        phoneSeed: ByteArray,
        recoverySeed: ByteArray,
    ): String

    /** How close the oldest Vault coin is to the heir timelock, at the synced tip (ADR 0008). */
    suspend fun heartbeat(): Heartbeat

    /** Every Vault coin, with how many blocks remain before the heir can spend it. */
    suspend fun heirCoins(): List<HeirCoin>

    /**
     * Sweeps every coin the heir can already spend (at least N confirmations) to [address], on the
     * heir branch. Throws `SpendException` (INSUFFICIENT_FUNDS when nothing has matured).
     */
    suspend fun planHeirClaim(
        address: String,
        feeRateSatPerVb: Long,
    ): SpendPlan

    /** Signs with the heir key only, checks nSequence = N and one signature per input, broadcasts. */
    suspend fun sendHeirClaim(
        plan: SpendPlan,
        heirSeed: ByteArray,
    ): String
}

/** A Vault coin seen by the heir: spendable once [blocksLeft] is 0. */
data class HeirCoin(
    val sats: Long,
    val confirmations: Int,
    val blocksLeft: Int,
) {
    val matured: Boolean get() = blocksLeft == 0
}

/** The Vault pocket (the BDK wallet implements both; only Vault pockets accept Vault spends). */
interface VaultPocket :
    PocketWallet,
    VaultPort

/** The chain backend failed (offline, timeout, HTTP error); the user can retry. */
class ChainUnavailableException(
    cause: Throwable,
) : Exception("Chain backend unavailable", cause)
