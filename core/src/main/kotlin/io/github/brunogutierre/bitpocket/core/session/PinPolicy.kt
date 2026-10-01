package io.github.brunogutierre.bitpocket.core.session

/**
 * Rejects PINs that are too easy to guess: all digits equal, straight sequences (ascending or
 * descending by 1, no wrap) and a short list of very common PINs.
 */
object PinPolicy {
    private val common =
        setOf(
            "123123",
            "121212",
            "112233",
            "111222",
            "696969",
            "159753",
            "147258",
            "102030",
            "101010",
            "131313",
            "202020",
            "212121",
            "520520",
            "110110",
            "100100",
            "200000",
            "999000",
            "123321",
            "654456",
            "147852",
            "123654",
            "000123",
            "007007",
            "789456",
        ).map { it.toCharArray() }

    fun isTooWeak(pin: CharArray): Boolean {
        if (pin.isEmpty()) return true
        return pin.all { it == pin[0] } || isStraight(pin, 1) || isStraight(pin, -1) || common.any { it.contentEquals(pin) }
    }

    private fun isStraight(
        pin: CharArray,
        step: Int,
    ) = (1 until pin.size).all { pin[it] - pin[it - 1] == step }
}
