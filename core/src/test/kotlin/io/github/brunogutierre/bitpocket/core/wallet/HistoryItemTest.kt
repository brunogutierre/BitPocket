package io.github.brunogutierre.bitpocket.core.wallet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryItemTest {
    private fun item(
        txid: String,
        confirmations: Int,
        time: Long?,
    ) = HistoryItem(txid, netSats = 1, feeSats = null, feeRateSatPerVb = null, confirmations = confirmations, timestampSec = time)

    @Test
    fun `pending first (newest seen on top), then shallowest confirmations`() {
        val items = listOf(item("old", 50, 10), item("pendingOld", 0, 100), item("recent", 2, 20), item("pendingNew", 0, 200))

        assertEquals(listOf("pendingNew", "pendingOld", "recent", "old"), items.sortedNewestFirst().map { it.txid })
    }
}
