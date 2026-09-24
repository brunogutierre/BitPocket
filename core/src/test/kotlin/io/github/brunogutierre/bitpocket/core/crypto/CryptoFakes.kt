package io.github.brunogutierre.bitpocket.core.crypto

/** Fast Argon2id parameters for tests only. */
val TEST_KDF_PARAMS = KdfParams(memoryKib = 64, iterations = 2, parallelism = 1)

/** Stand-in for the Android Keystore: AES-GCM under a key that never leaves the "device". */
class FakeKeyWrapper : KeyWrapper {
    private val deviceKey = ByteArray(32) { 42 }
    var unwrapCalls = 0
        private set

    override fun wrap(plaintext: ByteArray) = AesGcmEnvelope.seal(deviceKey, plaintext)

    override fun unwrap(wrapped: ByteArray): ByteArray {
        unwrapCalls++
        return AesGcmEnvelope.open(deviceKey, wrapped)
    }
}

/** Real Argon2id that counts calls, to check constant work. */
class CountingKdf : Kdf {
    var calls = 0
        private set

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: KdfParams,
    ): ByteArray {
        calls++
        return Argon2idKdf.deriveKey(password, salt, params)
    }
}

class InMemorySlotStorage : SlotStorage {
    val files = mutableMapOf<SlotId, ByteArray>()
    val writes = mutableListOf<SlotId>()

    override fun read(slot: SlotId) = files[slot]?.copyOf()

    override fun write(
        slot: SlotId,
        bytes: ByteArray,
    ) {
        writes += slot
        files[slot] = bytes.copyOf()
    }
}
