package io.github.brunogutierre.bitpocket.core.labels

/**
 * Reads and writes the flat JSON objects of BIP329 lines: string, boolean and null values only
 * (no nesting, no numbers). Small and strict on purpose: `:core` has no JSON library.
 */
internal object FlatJson {
    /** @throws IllegalArgumentException for anything that is not a flat object of those values. */
    fun parse(line: String): Map<String, Any?> {
        val reader = Reader(line.trim())
        reader.expect('{')
        val values = linkedMapOf<String, Any?>()
        reader.skipSpaces()
        if (reader.peek() == '}') {
            reader.next()
        } else {
            while (true) {
                reader.skipSpaces()
                val key = reader.string()
                reader.skipSpaces()
                reader.expect(':')
                reader.skipSpaces()
                values[key] = reader.value()
                reader.skipSpaces()
                if (reader.peek() == ',') reader.next() else break
            }
            reader.expect('}')
        }
        require(reader.atEnd()) { "Trailing characters" }
        return values
    }

    fun write(values: Map<String, Any?>): String =
        values.entries.joinToString(",", "{", "}") { (key, value) ->
            quote(key) + ":" +
                when (value) {
                    null -> "null"
                    is Boolean -> value.toString()
                    is String -> quote(value)
                    else -> throw IllegalArgumentException("Unsupported value $value")
                }
        }

    private fun quote(text: String): String =
        buildString {
            append('"')
            text.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }

    private class Reader(
        private val text: String,
    ) {
        private var i = 0

        fun atEnd() = i == text.length

        fun peek(): Char = text.getOrElse(i) { throw IllegalArgumentException("Unexpected end") }

        fun next(): Char = peek().also { i++ }

        fun expect(c: Char) = require(next() == c) { "Expected '$c' at $i" }

        fun skipSpaces() {
            while (!atEnd() && text[i].isWhitespace()) i++
        }

        fun value(): Any? =
            when (peek()) {
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> throw IllegalArgumentException("Unsupported value at $i")
            }

        private fun literal(
            word: String,
            value: Any?,
        ): Any? {
            require(text.startsWith(word, i)) { "Expected $word at $i" }
            i += word.length
            return value
        }

        fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                when (val c = next()) {
                    '"' -> return out.toString()
                    '\\' -> out.append(escape())
                    else -> out.append(c)
                }
            }
        }

        private fun escape(): Char =
            when (val c = next()) {
                '"', '\\', '/' -> {
                    c
                }

                'n' -> {
                    '\n'
                }

                'r' -> {
                    '\r'
                }

                't' -> {
                    '\t'
                }

                'b' -> {
                    '\b'
                }

                'f' -> {
                    '\u000c'
                }

                'u' -> {
                    require(i + 4 <= text.length) { "Bad \\u escape" }
                    text
                        .substring(i, i + 4)
                        .toInt(16)
                        .toChar()
                        .also { i += 4 }
                }

                else -> {
                    throw IllegalArgumentException("Bad escape \\$c")
                }
            }
    }
}
