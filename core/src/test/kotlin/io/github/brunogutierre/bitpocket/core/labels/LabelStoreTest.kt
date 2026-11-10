package io.github.brunogutierre.bitpocket.core.labels

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LabelStoreTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "wallets/ab/labels.jsonl")

    @Test
    fun `flat JSON round-trips strings with escapes, booleans and null`() {
        val values = mapOf("type" to "tx", "label" to "Café \"quoted\" \\ back\nline\ttab\u0001", "spendable" to false, "origin" to null)

        val line = FlatJson.write(values)

        assertEquals(values, FlatJson.parse(line))
        assertEquals(mapOf("a" to "é/"), FlatJson.parse(""" { "a" : "é\/" } """))
        assertEquals(emptyMap<String, Any?>(), FlatJson.parse("{}"))
        listOf("", "[]", """{"a":1}""", """{"a":{"b":"c"}}""", """{"a":"b",}""", """{"a":"b"} x""", """{"a":"\x"}""").forEach { bad ->
            assertThrows<IllegalArgumentException>(bad) { FlatJson.parse(bad) }
        }
    }

    @Test
    fun `labels are saved as BIP329 JSON lines and survive reopening`() {
        val store = LabelStore(file)
        store.setLabel(LabelStore.TX, TXID, "Rent")
        store.setLabel(LabelStore.ADDR, ADDRESS, "From Alice")

        val reopened = LabelStore(file)

        assertEquals("Rent", reopened.label(LabelStore.TX, TXID))
        assertEquals(mapOf(TXID to "Rent"), reopened.transactionLabels())
        assertEquals(
            """{"type":"tx","ref":"$TXID","label":"Rent"}""" + "\n" + """{"type":"addr","ref":"$ADDRESS","label":"From Alice"}""" + "\n",
            file.readText(),
        )
        reopened.setLabel(LabelStore.TX, TXID, "  ")
        assertNull(LabelStore(file).label(LabelStore.TX, TXID))
    }

    @Test
    fun `import merges valid records, skips other networks and returns unspendable outputs`() {
        val export =
            listOf(
                """{"type":"tx","ref":"$TXID","label":"Imported","origin":"wpkh([d34db33f/84'/0'/0'])"}""",
                """{"type":"addr","ref":"$ADDRESS","label":"Test address"}""",
                """{"type":"addr","ref":"bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4","label":"Mainnet"}""",
                """{"type":"output","ref":"$TXID:1","label":"Dust","spendable":false}""",
                """{"type":"xpub","ref":"tpubFoo","label":"Kept as is"}""",
                "not json",
                "",
            ).joinToString("\n")
        val store = LabelStore(file)

        val result = store.import(export, SupportedNetwork.SIGNET)

        assertEquals(LabelImport(added = 4, skipped = 2, frozen = listOf("$TXID:1")), result)
        assertEquals("Imported", store.label(LabelStore.TX, TXID))
        assertTrue(store.export().contains("\"origin\":\"wpkh([d34db33f/84'/0'/0'])\""), "unknown fields are kept")
        assertTrue(store.export().contains("tpubFoo"))
        assertFalse(store.export().contains("Mainnet"))
    }

    private companion object {
        const val TXID = "f91d0a8a78462bc59398f2c5d7a84fcff491c26ba54c4833478b202796c8aafd"
        const val ADDRESS = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
    }
}
