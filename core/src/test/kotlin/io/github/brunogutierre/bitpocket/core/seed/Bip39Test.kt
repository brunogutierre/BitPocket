package io.github.brunogutierre.bitpocket.core.seed

import org.bitcoindevkit.Mnemonic
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.security.MessageDigest
import kotlin.random.Random

class Bip39Test {
    @Test
    fun `ships the canonical English wordlist`() {
        val bytes = Bip39.words.joinToString("\n", postfix = "\n").toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        assertEquals("2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda", sha)
    }

    // Official BIP39 test vectors (Trezor): https://github.com/trezor/python-mnemonic/blob/master/vectors.json
    @ParameterizedTest
    @CsvSource(
        "00000000000000000000000000000000, abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f, legal winner thank year wave sausage worth useful legal winner thank yellow",
        "80808080808080808080808080808080, letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
        "ffffffffffffffffffffffffffffffff, zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
        "0000000000000000000000000000000000000000000000000000000000000000, " +
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art",
    )
    fun `matches the BIP39 test vectors both ways`(
        entropyHex: String,
        mnemonic: String,
    ) {
        val entropy = entropyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        assertEquals(mnemonic.split(" "), Bip39.toWords(entropy))
        assertArrayEquals(entropy, Bip39.toEntropy(mnemonic.split(" ")))
    }

    @Test
    fun `agrees with BDK for random entropy of every size`() {
        val random = Random(7)
        for (size in listOf(16, 20, 24, 28, 32)) {
            val entropy = random.nextBytes(size)
            assertEquals(Mnemonic.fromEntropy(entropy).toString(), Bip39.toWords(entropy).joinToString(" "))
        }
    }

    @Test
    fun `rejects invalid mnemonics with typed errors`() {
        val valid = "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" ")

        assertThrows<MnemonicException.InvalidWordCount> { Bip39.toEntropy(valid.take(11)) }
        val unknown = assertThrows<MnemonicException.UnknownWords> { Bip39.toEntropy(valid.toMutableList().apply { this[2] = "bitcoinz" }) }
        assertEquals(listOf(3), unknown.positions)
        assertThrows<MnemonicException.InvalidChecksum> { Bip39.toEntropy(valid.dropLast(1) + "zoo") }
        assertThrows<IllegalArgumentException> { Bip39.toWords(ByteArray(15)) }
    }

    @Test
    fun `suggests words by prefix, ignoring case and blanks`() {
        assertEquals(listOf("abandon", "ability", "able", "about"), Bip39.suggestions(" AB"))
        assertEquals(listOf("zone", "zoo"), Bip39.suggestions("zo"))
        assertEquals(emptyList<String>(), Bip39.suggestions(""))
        assertEquals(emptyList<String>(), Bip39.suggestions("xyz"))
    }
}
