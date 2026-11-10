package io.github.brunogutierre.bitpocket.core.send

enum class AmountError { EMPTY, NOT_A_NUMBER, BELOW_DUST }

sealed interface AmountResult {
    data class Valid(
        val sats: Long,
    ) : AmountResult

    data class Invalid(
        val error: AmountError,
    ) : AmountResult
}

/** Amounts are typed in whole satoshis: no decimal separator to get wrong. */
object AmountParser {
    /**
     * Smallest amount accepted: 546 sats is the dust limit of the largest standard output type
     * (P2PKH), so it is relayed whatever the recipient address type.
     */
    const val MIN_SATS = 546L
    private const val MAX_DIGITS = 16

    fun parseSats(text: String): AmountResult {
        // Grouping characters a user may type or paste: spaces, thin spaces, underscores.
        val digits = text.filterNot { it.isWhitespace() || it == '_' || it == ' ' }
        return when {
            digits.isEmpty() -> AmountResult.Invalid(AmountError.EMPTY)
            !digits.all(Char::isDigit) || digits.length > MAX_DIGITS -> AmountResult.Invalid(AmountError.NOT_A_NUMBER)
            digits.toLong() < MIN_SATS -> AmountResult.Invalid(AmountError.BELOW_DUST)
            else -> AmountResult.Valid(digits.toLong())
        }
    }
}

/** Fee rates offered to the user, in sat/vB, for about 1, 6 and 144 blocks. */
data class FeeOptions(
    val fast: Long,
    val normal: Long,
    val slow: Long,
)

object FeePolicy {
    const val MIN_SAT_PER_VB = 1L

    /** A cap against absurd fees, e.g. a misbehaving backend; test networks rarely exceed a few sat/vB. */
    const val MAX_SAT_PER_VB = 500L
    const val FAST_TARGET = 1
    const val NORMAL_TARGET = 6
    const val SLOW_TARGET = 144

    /**
     * From Esplora estimates (confirmation target in blocks -> sat/vB): for each target, the
     * estimate of the nearest target at or above it (else the slowest known), rounded up,
     * clamped to [MIN_SAT_PER_VB]..[MAX_SAT_PER_VB], and made monotonic (fast >= normal >= slow).
     */
    fun fromEstimates(estimates: Map<Int, Double>): FeeOptions {
        fun rateFor(target: Int): Long {
            val key = estimates.keys.filter { it >= target }.minOrNull() ?: estimates.keys.maxOrNull() ?: return MIN_SAT_PER_VB
            return kotlin.math
                .ceil(estimates.getValue(key))
                .toLong()
                .coerceIn(MIN_SAT_PER_VB, MAX_SAT_PER_VB)
        }
        val slow = rateFor(SLOW_TARGET)
        val normal = maxOf(rateFor(NORMAL_TARGET), slow)
        val fast = maxOf(rateFor(FAST_TARGET), normal)
        return FeeOptions(fast = fast, normal = normal, slow = slow)
    }
}
