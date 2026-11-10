package io.github.brunogutierre.bitpocket.core.send

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LookalikeTest {
    private val real = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"

    // Same "tb1p8wpt" start and "t9zj" end, different middle: what a poisoning address looks like.
    private val poisoned = "tb1p8wptqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqt9zj"

    @Test
    fun `flags a different address with the same visible ends`() {
        assertTrue(Lookalike.looksAlike(real, poisoned))
        assertEquals(real, Lookalike.find(poisoned, setOf("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx", real)))
    }

    @Test
    fun `the same address or unrelated ones are fine`() {
        assertNull(Lookalike.find(real, setOf(real)))
        assertFalse(Lookalike.looksAlike(real, "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"))
        assertFalse(Lookalike.looksAlike(real, real.dropLast(4) + "aaaa"))
        assertFalse(Lookalike.looksAlike(real, "tb1p9wpt" + real.drop(8)))
    }

    @Test
    fun `the shared tb1p prefix alone is not enough`() {
        val other = "tb1pxxxx" + real.drop(8)

        assertFalse(Lookalike.looksAlike(real, other))
    }
}
