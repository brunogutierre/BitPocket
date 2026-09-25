package io.github.brunogutierre.bitpocket.core.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BitcoinFormatTest {
    @ParameterizedTest
    @CsvSource(
        "0, 0 sats",
        "999, 999 sats",
        "1000, 1_000 sats",
        "1250000, 1_250_000 sats",
        "-40000, -40_000 sats",
        "2100000000000000, 2_100_000_000_000_000 sats",
        "-9223372036854775808, -9_223_372_036_854_775_808 sats",
    )
    fun `groups sats with thin spaces`(
        amount: Long,
        expected: String,
    ) {
        assertEquals(expected.replace('_', BitcoinFormat.THIN_SPACE), BitcoinFormat.sats(amount))
    }

    @ParameterizedTest
    @CsvSource(
        "0, 0.00000000 BTC",
        "1, 0.00000001 BTC",
        "1250000, 0.01250000 BTC",
        "100000000, 1.00000000 BTC",
        "-40000, -0.00040000 BTC",
    )
    fun `formats BTC with 8 decimals`(
        amount: Long,
        expected: String,
    ) {
        assertEquals(expected, BitcoinFormat.btc(amount))
    }

    @ParameterizedTest
    @CsvSource(
        "tb1p8wpt9v4f, tb1p|8wpt|9v4f",
        "tb1qab, tb1q|ab",
        "'', ''",
    )
    fun `chunks addresses by 4 characters`(
        address: String,
        expected: String,
    ) {
        assertEquals(expected.split("|").filter { it.isNotEmpty() }, BitcoinFormat.addressChunks(address))
    }
}
