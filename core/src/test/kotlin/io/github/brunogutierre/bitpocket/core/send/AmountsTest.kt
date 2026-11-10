package io.github.brunogutierre.bitpocket.core.send

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AmountsTest {
    @ParameterizedTest
    @CsvSource(
        "'10 000', 10000",
        "'1 250 000', 1250000",
        "1_000, 1000",
        "546, 546",
    )
    fun `parses whole satoshis with grouping characters`(
        text: String,
        sats: Long,
    ) {
        assertEquals(AmountResult.Valid(sats), AmountParser.parseSats(text))
    }

    @ParameterizedTest
    @CsvSource(
        "'', EMPTY",
        "'  ', EMPTY",
        "0.5, NOT_A_NUMBER",
        "-1000, NOT_A_NUMBER",
        "12345678901234567, NOT_A_NUMBER",
        "545, BELOW_DUST",
        "0, BELOW_DUST",
    )
    fun `rejects empty, non-integer and dust amounts`(
        text: String,
        error: AmountError,
    ) {
        assertEquals(AmountResult.Invalid(error), AmountParser.parseSats(text))
    }

    @Test
    fun `picks the nearest target at or above each one and rounds up`() {
        val estimates = mapOf(1 to 12.2, 3 to 8.0, 6 to 4.5, 144 to 1.1, 1008 to 1.0)

        assertEquals(FeeOptions(fast = 13, normal = 5, slow = 2), FeePolicy.fromEstimates(estimates))
    }

    @Test
    fun `uses the slowest estimate when a target is missing, and stays monotonic`() {
        assertEquals(FeeOptions(fast = 3, normal = 3, slow = 3), FeePolicy.fromEstimates(mapOf(2 to 1.0, 25 to 3.0)))
    }

    @Test
    fun `clamps to the floor and the cap`() {
        assertEquals(FeeOptions(1, 1, 1), FeePolicy.fromEstimates(emptyMap()))
        assertEquals(FeeOptions(1, 1, 1), FeePolicy.fromEstimates(mapOf(1 to 0.1, 6 to 0.1, 144 to 0.1)))
        assertEquals(FeeOptions(500, 500, 500), FeePolicy.fromEstimates(mapOf(1 to 9_999.0, 6 to 900.0, 144 to 600.0)))
    }
}
