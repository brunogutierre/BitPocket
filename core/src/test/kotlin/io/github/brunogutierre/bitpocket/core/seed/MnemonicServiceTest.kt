package io.github.brunogutierre.bitpocket.core.seed

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MnemonicServiceTest {
    private val service = MnemonicService()
    private val legal = "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" ")

    @Test
    fun `creates 12 words that encode the returned entropy`() {
        val created = service.create()

        assertEquals(12, created.words.size)
        assertEquals(16, created.entropy.size)
        assertArrayEquals(created.entropy, Bip39.toEntropy(created.words))
    }

    @Test
    fun `restores 12 or 24 words and maps every failure to a typed result`() {
        val valid = service.restore(legal.map { " ${it.uppercase()} " })
        val twentyFour = service.restore(Bip39.toWords(ByteArray(32)))

        assertArrayEquals(Bip39.toEntropy(legal), (valid as RestoreResult.Valid).entropy)
        assertInstanceOf(RestoreResult.Valid::class.java, twentyFour)
        assertEquals(RestoreResult.InvalidWordCount(15), service.restore(Bip39.toWords(ByteArray(20))))
        assertEquals(RestoreResult.UnknownWords(listOf(1, 12)), service.restore(listOf("nope") + legal.subList(1, 11) + "nah"))
        assertEquals(RestoreResult.InvalidChecksum, service.restore(legal.dropLast(1) + "zoo"))
    }

    @Test
    fun `builds deterministic challenges at distinct positions with distinct options`() {
        val first = service.challenges(legal, Random(42))
        val second = service.challenges(legal, Random(42))

        assertEquals(first, second)
        assertEquals(3, first.map { it.position }.distinct().size)
        assertEquals(first.map { it.position }.sorted(), first.map { it.position })
        first.forEach { challenge ->
            assertEquals(legal[challenge.position - 1], challenge.answer)
            assertEquals(4, challenge.options.distinct().size)
            assertTrue(challenge.answer in challenge.options)
        }
        assertEquals(listOf("zoo"), service.suggestions("zoo"))
    }
}
