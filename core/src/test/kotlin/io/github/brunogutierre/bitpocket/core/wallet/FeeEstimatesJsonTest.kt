package io.github.brunogutierre.bitpocket.core.wallet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FeeEstimatesJsonTest {
    @Test
    fun `parses an Esplora fee estimates body`() {
        val body = """{"1":1,"2":1,"3":0.5,"144":0.1,"1008":1.5e0}"""

        assertEquals(mapOf(1 to 1.0, 2 to 1.0, 3 to 0.5, 144 to 0.1, 1008 to 1.5), FeeEstimatesJson.parse(body))
    }

    @Test
    fun `accepts whitespace and an empty object`() {
        assertEquals(mapOf(6 to 2.0), FeeEstimatesJson.parse(" { \"6\" : 2 }\n"))
        assertEquals(emptyMap<Int, Double>(), FeeEstimatesJson.parse("{}"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "[]", """{"a":1}""", """{"1":-2}""", """{"1":1,}""", """{"1":"1"}""", "<html>"])
    fun `rejects anything else`(body: String) {
        assertThrows<IllegalArgumentException> { FeeEstimatesJson.parse(body) }
    }
}
