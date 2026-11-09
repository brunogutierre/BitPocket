package io.github.brunogutierre.bitpocket.core.fingerprint

import io.github.brunogutierre.bitpocket.core.seed.Bip39
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddressFingerprintTest {
    private val address = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"

    @Test
    fun `is deterministic and uses three BIP39 words`() {
        val first = AddressFingerprint.of(address)
        val again = AddressFingerprint.of(address)

        assertEquals(first.words, again.words)
        assertEquals(first.cells, again.cells)
        assertEquals(first.hue, again.hue)
        assertEquals(3, first.words.size)
        assertTrue(first.words.all { it in Bip39.words })
        assertTrue(first.hue in 0..359)
    }

    @Test
    fun `the scheme is frozen`() {
        // Changing the bit layout would change every fingerprint users may have written down.
        val fingerprint = AddressFingerprint.of(address)

        assertEquals(EXPECTED_WORDS, fingerprint.words)
        assertEquals(EXPECTED_HUE, fingerprint.hue)
    }

    @Test
    fun `the identicon is mirrored left to right`() {
        val fingerprint = AddressFingerprint.of(address)

        for (row in 0 until AddressFingerprint.SIZE) {
            assertEquals(fingerprint[row, 0], fingerprint[row, 4])
            assertEquals(fingerprint[row, 1], fingerprint[row, 3])
        }
    }

    @Test
    fun `a one-character change gives a different fingerprint`() {
        val lookalike = address.dropLast(1) + "x"

        assertNotEquals(AddressFingerprint.of(address).words, AddressFingerprint.of(lookalike).words)
    }

    private companion object {
        // Computed independently: SHA-256 of the address in Python, bits 0-32 as 3 BIP39 indexes,
        // bits 48-56 mod 360 as the hue.
        val EXPECTED_WORDS = listOf("deal", "decorate", "blue")
        const val EXPECTED_HUE = 227
    }
}
