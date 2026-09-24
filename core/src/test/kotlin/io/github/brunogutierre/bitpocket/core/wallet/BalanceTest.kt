package io.github.brunogutierre.bitpocket.core.wallet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BalanceTest {
    @Test
    fun `total is confirmed plus pending`() {
        assertEquals(1_500L, Balance(confirmedSats = 1_000L, pendingSats = 500L).totalSats)
    }
}
