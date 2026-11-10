package io.github.brunogutierre.bitpocket.core.vault

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeartbeatTest {
    private val year = HeirTimelock.YEAR.blocks

    @Test
    fun `no confirmed coins means nothing to refresh`() {
        assertEquals(Heartbeat(HeartbeatStatus.OK, null), Heartbeat.of(1_000, emptyList(), year))
        assertEquals(Heartbeat(HeartbeatStatus.OK, null), Heartbeat.of(1_000, listOf(null), year))
    }

    @Test
    fun `counts from the oldest coin and warns in the last tenth`() {
        // Confirmed at 1 000: 1 confirmation at the tip 1 000.
        assertEquals(Heartbeat(HeartbeatStatus.OK, year - 1), Heartbeat.of(1_000, listOf(1_000, null), year))
        val warnAt = 1_000 + year - year / 10 - 1
        assertEquals(Heartbeat(HeartbeatStatus.DUE_SOON, year / 10), Heartbeat.of(warnAt, listOf(5_000, 1_000), year))
        assertEquals(Heartbeat(HeartbeatStatus.EXPIRED, 0), Heartbeat.of(1_000 + year - 1, listOf(1_000), year))
        assertEquals(Heartbeat(HeartbeatStatus.EXPIRED, 0), Heartbeat.of(1_000 + 2 * year, listOf(1_000), year))
    }

    @Test
    fun `short timelocks warn for half of the period at most`() {
        // 6 blocks: the warning window is 3 blocks, not a whole day.
        assertEquals(HeartbeatStatus.OK, Heartbeat.of(101, listOf(100), 6).status)
        assertEquals(HeartbeatStatus.DUE_SOON, Heartbeat.of(102, listOf(100), 6).status)
        assertEquals(HeartbeatStatus.EXPIRED, Heartbeat.of(105, listOf(100), 6).status)
    }
}
