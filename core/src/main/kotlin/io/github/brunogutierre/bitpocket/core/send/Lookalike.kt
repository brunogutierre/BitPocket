package io.github.brunogutierre.bitpocket.core.send

/**
 * Address poisoning: an attacker sends dust from an address that starts and ends like one the
 * user knows, hoping it gets copied from the history later. People compare the visible ends, so a
 * different address with the same ends is a strong warning sign.
 */
object Lookalike {
    /** Characters compared after the bech32 separator (witness version + 3 data characters). */
    private const val HEAD = 4

    /** Characters compared at the end of the address. */
    private const val TAIL = 4

    /** A known address that looks like [candidate] without being it, or null. */
    fun find(
        candidate: String,
        known: Collection<String>,
    ): String? = known.firstOrNull { it != candidate && looksAlike(it, candidate) }

    fun looksAlike(
        a: String,
        b: String,
    ): Boolean = a.length == b.length && head(a) == head(b) && a.takeLast(TAIL) == b.takeLast(TAIL)

    /** The first characters a person reads: after the human-readable part for bech32 (`tb1`). */
    private fun head(address: String): String {
        val separator = address.lastIndexOf('1')
        val start = if (separator in 1..address.length - HEAD) separator + 1 else 0
        return address.substring(start, start + HEAD)
    }
}
