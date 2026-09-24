package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class Argon2idKdfTest {
    private val fast = KdfParams(memoryKib = 64, iterations = 2, parallelism = 1)
    private val salt = ByteArray(KdfParams.SALT_BYTES) { it.toByte() }

    @Test
    fun `matches the RFC 9106 Argon2id test vector`() {
        // RFC 9106, section 5.3: https://www.rfc-editor.org/rfc/rfc9106#section-5.3
        val tag =
            Argon2idKdf.derive(
                password = ByteArray(32) { 0x01 },
                salt = ByteArray(16) { 0x02 },
                params = KdfParams(memoryKib = 32, iterations = 3, parallelism = 4),
                outputBytes = 32,
                secret = ByteArray(8) { 0x03 },
                associatedData = ByteArray(12) { 0x04 },
            )

        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.hex())
    }

    @Test
    fun `derives a deterministic 32-byte key from the UTF-8 password`() {
        val key = Argon2idKdf.deriveKey("1234".toCharArray(), salt, fast)

        assertEquals(KdfParams.KEY_BYTES, key.size)
        assertArrayEquals(key, Argon2idKdf.derive("1234".toByteArray(), salt, fast, KdfParams.KEY_BYTES))
    }

    @Test
    fun `different salt or password gives a different key`() {
        val key = Argon2idKdf.deriveKey("1234".toCharArray(), salt, fast)

        assertFalse(key.contentEquals(Argon2idKdf.deriveKey("1235".toCharArray(), salt, fast)))
        assertFalse(key.contentEquals(Argon2idKdf.deriveKey("1234".toCharArray(), salt.reversedArray(), fast)))
    }

    @Test
    fun `leaves the caller's password untouched`() {
        val password = "1234".toCharArray()

        Argon2idKdf.deriveKey(password, salt, fast)

        assertEquals("1234", String(password))
    }

    @Test
    fun `rejects invalid parameters`() {
        assertThrows<IllegalArgumentException> { KdfParams(memoryKib = 4, iterations = 2, parallelism = 1) }
        assertThrows<IllegalArgumentException> { KdfParams(memoryKib = 64, iterations = 0, parallelism = 1) }
        assertThrows<IllegalArgumentException> { KdfParams(memoryKib = 64, iterations = 2, parallelism = 0) }
    }
}
