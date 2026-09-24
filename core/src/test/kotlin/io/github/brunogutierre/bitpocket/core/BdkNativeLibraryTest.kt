package io.github.brunogutierre.bitpocket.core

import org.bitcoindevkit.Mnemonic
import org.bitcoindevkit.WordCount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Smoke test: the bdk-jvm native library loads on the build machine (JNA + UniFFI checksums). */
class BdkNativeLibraryTest {
    @Test
    fun `generates a 12-word mnemonic through the native library`() {
        val words = Mnemonic(WordCount.WORDS12).toString().split(" ")

        assertEquals(12, words.size)
    }
}
