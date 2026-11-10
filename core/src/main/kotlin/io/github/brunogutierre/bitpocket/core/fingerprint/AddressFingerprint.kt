package io.github.brunogutierre.bitpocket.core.fingerprint

import io.github.brunogutierre.bitpocket.core.seed.Bip39
import java.math.BigInteger
import java.security.MessageDigest

/**
 * A memorable fingerprint of an address, for comparing it across devices or by voice:
 * three BIP39 words plus a symmetric 5x5 identicon with its own hue.
 *
 * Bits of SHA-256(address): 0-32 the words (3 x 11 bits), 33-47 the identicon cells (3 columns
 * x 5 rows, mirrored), 48-56 the hue. That is about 57 bits: an attacker would need on the order
 * of 10^17 generated addresses for a full match. It helps humans notice a swapped address; it does
 * not replace checking the address itself.
 */
class AddressFingerprint private constructor(
    val words: List<String>,
    /** 5x5 cells, row by row (`true` = filled); column 4 mirrors 0 and 3 mirrors 1. */
    val cells: List<Boolean>,
    /** 0..359. */
    val hue: Int,
) {
    operator fun get(
        row: Int,
        column: Int,
    ): Boolean = cells[row * SIZE + column]

    companion object {
        const val SIZE = 5
        const val WORDS = 3
        private const val WORD_BITS = 11
        private const val HALF_COLUMNS = 3

        /** [address] as BDK prints it (bech32 lowercase), so the same address always gives the same fingerprint. */
        fun of(address: String): AddressFingerprint {
            val bits = BigInteger(1, MessageDigest.getInstance("SHA-256").digest(address.toByteArray(Charsets.US_ASCII)))
            var offset = 0

            fun take(count: Int): Int {
                // Read from the most significant end of the 256-bit hash.
                val value = bits.shiftRight(256 - offset - count).toInt() and ((1 shl count) - 1)
                offset += count
                return value
            }
            val words = List(WORDS) { Bip39.words[take(WORD_BITS)] }
            val half = List(SIZE * HALF_COLUMNS) { take(1) == 1 }
            val cells =
                List(SIZE * SIZE) { index ->
                    val row = index / SIZE
                    val column = index % SIZE
                    val source = if (column < HALF_COLUMNS) column else SIZE - 1 - column
                    half[row * HALF_COLUMNS + source]
                }
            val hue = take(9) % 360
            return AddressFingerprint(words, cells, hue)
        }
    }
}
