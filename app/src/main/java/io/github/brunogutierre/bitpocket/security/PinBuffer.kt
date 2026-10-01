package io.github.brunogutierre.bitpocket.security

import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.ui.components.PIN_LENGTH

/** PIN digits kept in a CharArray (never a String) so they can be wiped. */
class PinBuffer {
    private val digits = CharArray(PIN_LENGTH)

    var size = 0
        private set

    val isFull: Boolean get() = size == PIN_LENGTH

    fun append(digit: Char) {
        require(digit in '0'..'9') { "PIN digits only" }
        if (!isFull) digits[size++] = digit
    }

    fun removeLast() {
        if (size > 0) digits[--size] = '\u0000'
    }

    /** A copy for the caller, who must wipe it. */
    fun toCharArray(): CharArray = digits.copyOf(size)

    fun contentEquals(other: PinBuffer) = size == other.size && (0 until size).all { digits[it] == other.digits[it] }

    fun clear() {
        digits.wipe()
        size = 0
    }
}
