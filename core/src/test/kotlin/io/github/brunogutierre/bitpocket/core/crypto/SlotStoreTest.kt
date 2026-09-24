package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SlotStoreTest {
    private val storage = InMemorySlotStorage()
    private val store = SlotStore(storage, Argon2idKdf, FakeKeyWrapper())
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
        store.save(SlotId.A, "1234".toCharArray(), payload, TEST_KDF_PARAMS)

        assertArrayEquals(payload, open(SlotId.A, "1234"))
        assertNull(open(SlotId.A, "0000"))
        assertNull(open(SlotId.B, "1234"), "decoy slot must not open")
    }

    @Test
    fun `always writes both slots with identical sizes`() {
        store.save(SlotId.A, "1234".toCharArray(), payload, TEST_KDF_PARAMS)

        assertEquals(listOf(SlotId.A, SlotId.B), storage.writes)
        assertEquals(storage.files.getValue(SlotId.A).size, storage.files.getValue(SlotId.B).size)
        assertEquals(TEST_KDF_PARAMS, store.read(SlotId.B)!!.header.params, "decoy header looks real")
    }

    @Test
    fun `saving one slot keeps the other slot's content`() {
        store.save(SlotId.B, "9999".toCharArray(), "decoy wallet".toByteArray(), TEST_KDF_PARAMS)
        val bBefore = storage.files.getValue(SlotId.B)

        store.save(SlotId.A, "1234".toCharArray(), payload, TEST_KDF_PARAMS)

        assertArrayEquals(bBefore, storage.files.getValue(SlotId.B))
        assertArrayEquals("decoy wallet".toByteArray(), open(SlotId.B, "9999"))
        assertEquals(storage.files.getValue(SlotId.A).size, storage.files.getValue(SlotId.B).size)
    }

    @Test
    fun `file size does not depend on the payload size`() {
        store.save(SlotId.A, "1234".toCharArray(), ByteArray(0), TEST_KDF_PARAMS)
        val small = storage.files.getValue(SlotId.A).size
        store.save(SlotId.A, "1234".toCharArray(), ByteArray(SlotFile.MAX_PAYLOAD_BYTES), TEST_KDF_PARAMS)

        assertEquals(small, storage.files.getValue(SlotId.A).size)
        assertThrows<IllegalArgumentException> {
            store.save(SlotId.A, "1234".toCharArray(), ByteArray(SlotFile.MAX_PAYLOAD_BYTES + 1), TEST_KDF_PARAMS)
        }
    }

    @Test
    fun `stores the KDF parameters in the header`() {
        val params = KdfParams(memoryKib = 128, iterations = 3, parallelism = 1)

        store.save(SlotId.A, "1234".toCharArray(), payload, params)

        assertEquals(params, store.read(SlotId.A)!!.header.params)
        assertArrayEquals(payload, open(SlotId.A, "1234"))
    }

    @Test
    fun `a slot file copied to the other slot does not open`() {
        store.save(SlotId.A, "1234".toCharArray(), payload, TEST_KDF_PARAMS)
        storage.files[SlotId.B] = storage.files.getValue(SlotId.A)

        assertNull(open(SlotId.B, "1234"))
    }

    @Test
    fun `missing or corrupted files read as null`() {
        assertNull(store.read(SlotId.A))
        assertNull(store.openDek(SlotId.A, null, "1234".toCharArray()))

        storage.files[SlotId.A] = byteArrayOf(9, 9, 9)
        assertNull(store.read(SlotId.A))
    }

    @Test
    fun `file storage replaces slot files under slots-a and slots-b`(
        @TempDir root: File,
    ) {
        val files = FileSlotStorage(root)
        assertNull(files.read(SlotId.A))

        files.write(SlotId.A, byteArrayOf(1))
        files.write(SlotId.A, byteArrayOf(2))
        files.write(SlotId.B, byteArrayOf(3))

        assertArrayEquals(byteArrayOf(2), File(root, "slots/a/slot.bin").readBytes())
        assertArrayEquals(byteArrayOf(3), files.read(SlotId.B))
        assertNotNull(File(root, "slots/b").listFiles()?.singleOrNull { it.name == "slot.bin" })
    }
}
