package io.github.brunogutierre.bitpocket.core.crypto

import java.security.SecureRandom

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

/** Real Argon2id that records calls and keeps every derived key, to check work and wiping. */
class CountingKdf : Kdf {
    val params = mutableListOf<KdfParams>()
    val derivedKeys = mutableListOf<ByteArray>()
    val calls get() = params.size

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: KdfParams,
    ): ByteArray {
        this.params += params
        return Argon2idKdf.deriveKey(password, salt, params).also { derivedKeys += it }
    }
}

/** SecureRandom that keeps the arrays it fills, to check that secrets are wiped. */
class RecordingRandom : SecureRandom() {
    val filled = mutableListOf<ByteArray>()

    override fun nextBytes(bytes: ByteArray) {
        super.nextBytes(bytes)
        filled += bytes
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
