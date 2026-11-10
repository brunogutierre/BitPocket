package io.github.brunogutierre.bitpocket.core.wallet

/**
 * Parses Esplora's `GET /fee-estimates` body: a flat JSON object of confirmation target (blocks)
 * to sat/vB, e.g. `{"1":12.1,"6":4,"144":1.02}`. The shape is fixed and tiny, so a strict regex
 * reader is enough (no JSON library in `:core`).
 */
object FeeEstimatesJson {
    private val entry = Regex(""""(\d{1,4})"\s*:\s*(\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)""")
    private val shape = Regex("""\{\s*(${entry.pattern}\s*(,\s*${entry.pattern}\s*)*)?}""")

    /** @throws IllegalArgumentException when [json] is not such an object. */
    fun parse(json: String): Map<Int, Double> {
        val text = json.trim()
        require(shape.matches(text)) { "Not a fee estimates object" }
        return entry.findAll(text).associate { it.groupValues[1].toInt() to it.groupValues[2].toDouble() }
    }
}
