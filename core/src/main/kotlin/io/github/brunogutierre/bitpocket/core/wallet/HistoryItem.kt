package io.github.brunogutierre.bitpocket.core.wallet

/** One wallet transaction, from the wallet's point of view. */
data class HistoryItem(
    val txid: String,
    /** What the wallet gained (positive) or lost (negative, fee included), in satoshis. */
    val netSats: Long,
    /** Known when BDK has every input of the transaction (always for our own sends). */
    val feeSats: Long?,
    val feeRateSatPerVb: Long?,
    /** 0 while unconfirmed; 1 when in the tip block. */
    val confirmations: Int,
    /** Block time when confirmed, otherwise when first seen in the mempool (Unix seconds). */
    val timestampSec: Long?,
) {
    val isPending: Boolean get() = confirmations == 0
}

/** Newest first: pending ones on top, then by confirmation depth. */
fun List<HistoryItem>.sortedNewestFirst(): List<HistoryItem> =
    sortedWith(compareBy<HistoryItem> { !it.isPending }.thenBy { it.confirmations }.thenByDescending { it.timestampSec ?: 0 })
