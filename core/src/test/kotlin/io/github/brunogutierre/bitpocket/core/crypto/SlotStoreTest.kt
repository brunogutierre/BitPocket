package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.ByteBuffer

class SlotStoreTest {
    private val storage = InMemorySlotStorage()
    private val keyWrapper = FakeKeyWrapper()
    private val kdf = CountingKdf()
    private val random = RecordingRandom()
    private val store = SlotStore(storage, kdf, keyWrapper, TEST_KDF_PARAMS, random)
    private val payload = "entropy + metadata".toByteArray()

    private fun open(
        slot: SlotId,
        pin: String,
    ): ByteArray? {
        val file = store.read(slot)
        val dek = store.openDek(slot, file, pin.toCharArray()) ?: return null
        return store.openPayload(slot, file!!, dek)
    }

    @Test
    fun `round-trips a payload with the right PIN only`() {
        store.save(SlotId.A, "1234".toCharArray(), payload)

        assertArrayEquals(payload, open(SlotId.A, "1234"))
        assertNull(open(SlotId.A, "0000"))
        assertNull(open(SlotId.B, "1234"), "decoy slot must not open")
    }

    @ParameterizedTest
    @EnumSource(SlotId::class)
    fun `every save writes A then B with identical sizes and all-new bytes`(slot: SlotId) {
        store.save(SlotId.A, "1111".toCharArray(), payload)
        store.save(SlotId.B, "2222".toCharArray(), payload)
        val before = storage.files.toMap()
        storage.writes.clear()

        store.save(slot, "1111".toCharArray(), payload)

        assertEquals(listOf(SlotId.A, SlotId.B), storage.writes)
        assertEquals(SlotFile.SIZE + AesGcmEnvelope.OVERHEAD, storage.files.getValue(SlotId.A).size)
        assertEquals(storage.files.getValue(SlotId.A).size, storage.files.getValue(SlotId.B).size)
        SlotId.entries.forEach { assertFalse(before.getValue(it).contentEquals(storage.files.getValue(it))) }
    }

    @Test
    fun `saving one slot keeps the other slot's content`() {
        store.save(SlotId.B, "9999".toCharArray(), "decoy wallet".toByteArray())

        store.save(SlotId.A, "1234".toCharArray(), payload)

        assertArrayEquals("decoy wallet".toByteArray(), open(SlotId.B, "9999"))
        assertArrayEquals(payload, open(SlotId.A, "1234"))
    }

    @Test
    fun `stores the configured KDF parameters and rejects oversized payloads`() {
        val params = KdfParams(memoryKib = 128, iterations = 3, parallelism = 1)
        val tuned = SlotStore(storage, kdf, keyWrapper, params)

        tuned.save(SlotId.A, "1234".toCharArray(), ByteArray(SlotFile.MAX_PAYLOAD_BYTES))

        assertEquals(params, tuned.read(SlotId.A)!!.header.params)
        assertThrows<IllegalArgumentException> {
            tuned.save(SlotId.A, "1234".toCharArray(), ByteArray(SlotFile.MAX_PAYLOAD_BYTES + 1))
        }
    }

    @Test
    fun `wipes the PIN key and the DEK after saving`() {
        store.save(SlotId.A, "1234".toCharArray(), payload)

        assertTrue(kdf.derivedKeys.single().all { it == 0.toByte() }, "PIN key")
        val dek = random.filled.single { it.size == SlotFile.DEK_BYTES }
        assertTrue(dek.all { it == 0.toByte() }, "DEK")
    }

    @Test
    fun `a slot file copied to the other slot does not open`() {
        store.save(SlotId.A, "1234".toCharArray(), payload)
        storage.files[SlotId.B] = storage.files.getValue(SlotId.A)

        assertNull(open(SlotId.B, "1234"))
    }

    @Test
    fun `a tampered header with huge KDF params is rejected before any derivation`() {
        store.save(SlotId.A, "1234".toCharArray(), payload)
        val plain = keyWrapper.unwrap(storage.files.getValue(SlotId.A))
        ByteBuffer.wrap(plain).putInt(1, Int.MAX_VALUE) // memoryKib
        storage.files[SlotId.A] = keyWrapper.wrap(plain)
        val file = store.read(SlotId.A)

        assertNull(file)
        assertNull(store.openDek(SlotId.A, file, "1234".toCharArray()))
        assertEquals(TEST_KDF_PARAMS, kdf.params.last(), "missing file falls back to configured params")
    }

    @ParameterizedTest
    @ValueSource(strings = ["truncated", "version", "memory", "iterations", "parallelism"])
    fun `decoding rejects malformed slot files`(corruption: String) {
        val valid =
            SlotFile(
                SlotHeader(TEST_KDF_PARAMS, ByteArray(16)),
                ByteArray(SlotFile.DEK_ENVELOPE_SIZE),
                ByteArray(SlotFile.PAYLOAD_ENVELOPE_SIZE),
            ).encode()
        val corrupted =
            when (corruption) {
                "truncated" -> valid.copyOf(valid.size - 1)
                "version" -> valid.copyOf().also { it[0] = 9 }
                "memory" -> valid.copyOf().also { ByteBuffer.wrap(it).putInt(1, KdfParams.MAX_MEMORY_KIB + 1) }
                "iterations" -> valid.copyOf().also { ByteBuffer.wrap(it).putInt(5, 0) }
                else -> valid.copyOf().also { it[9] = (KdfParams.MAX_PARALLELISM + 1).toByte() }
            }

        assertEquals(TEST_KDF_PARAMS, SlotFile.decode(valid).header.params)
        assertThrows<IllegalArgumentException> { SlotFile.decode(corrupted) }
    }

    @Test
    fun `missing, foreign or garbage files read as null`() {
        assertNull(store.read(SlotId.A))

        storage.files[SlotId.A] = byteArrayOf(9, 9, 9)
        assertNull(store.read(SlotId.A))

        storage.files[SlotId.A] = keyWrapper.wrap(byteArrayOf(1, 2, 3))
        assertNull(store.read(SlotId.A))
    }

    @Test
    fun `file storage atomically replaces exactly slots-a-slot-bin and slots-b-slot-bin`(
        @TempDir root: File,
    ) {
        val files = FileSlotStorage(root)
        assertNull(files.read(SlotId.A))

        files.write(SlotId.A, byteArrayOf(1))
        files.write(SlotId.A, byteArrayOf(2))
        files.write(SlotId.B, byteArrayOf(3))

        val written =
            root
                .walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(root).path }
                .sorted()
                .toList()
        assertEquals(listOf("slots/a/slot.bin", "slots/b/slot.bin"), written)
        assertArrayEquals(byteArrayOf(2), files.read(SlotId.A))
        assertArrayEquals(byteArrayOf(3), files.read(SlotId.B))
    }
}
