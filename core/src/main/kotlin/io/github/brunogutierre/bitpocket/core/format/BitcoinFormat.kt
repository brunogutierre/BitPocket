package io.github.brunogutierre.bitpocket.core.format

import java.math.BigDecimal

/** Display formatting for amounts and addresses (UI-independent, so it is unit tested here). */
object BitcoinFormat {
    const val SATS_PER_BTC = 100_000_000L

    /** Thin space (U+2009) groups digits without looking like a decimal separator. */
    const val THIN_SPACE = ' '

    /** `1250000` -> `"1 250 000 sats"` (thin spaces), with a leading `-` for negative amounts. */
    fun sats(amount: Long): String {
        val digits = amount.toBigInteger().abs().toString()
        val grouped =
            digits
                .reversed()
                .chunked(3)
                .joinToString(THIN_SPACE.toString())
                .reversed()
        return "${if (amount < 0) "-" else ""}$grouped sats"
    }

    /** `1250000` -> `"0.01250000 BTC"` (always 8 decimals). */
    fun btc(amount: Long): String = "${BigDecimal.valueOf(amount, 8).toPlainString()} BTC"

    /** Splits an address into 4-character chunks for easier visual comparison. */
    fun addressChunks(address: String): List<String> = address.chunked(4)
}
