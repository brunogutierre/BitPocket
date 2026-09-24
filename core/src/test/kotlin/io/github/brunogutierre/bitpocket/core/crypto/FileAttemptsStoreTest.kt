package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileAttemptsStoreTest {
    @TempDir
    lateinit var dir: File
    private val keyWrapper = FakeKeyWrapper()
    private val file by lazy { File(dir, "attempts.bin") }
    private val store by lazy { FileAttemptsStore(file, keyWrapper) }
    private val state = AttemptsState(failedAttempts = 5, lockoutRemainingMs = 30_000, bootCount = 2, elapsedAtMs = 99)

    @Test
    fun `round-trips an encrypted fixed-size record, rewritten with new bytes each time`() {
        assertEquals(AttemptsState.NONE, store.read())

        store.write(state)
        val first = file.readBytes()
        store.write(AttemptsState.NONE)
        store.write(state)

        assertEquals(state, store.read())
        assertEquals(AttemptsState.SIZE + AesGcmEnvelope.OVERHEAD, file.length().toInt())
        assertFalse(first.contentEquals(file.readBytes()), "fresh nonce on every write")
        assertEquals(listOf("attempts.bin"), dir.list()!!.toList())
    }

    @Test
    fun `an unreadable record reads as no failures`() {
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(AttemptsState.NONE, store.read())

        file.writeBytes(keyWrapper.wrap(ByteArray(AttemptsState.SIZE)))
        assertEquals(AttemptsState.NONE, store.read())
    }
}
