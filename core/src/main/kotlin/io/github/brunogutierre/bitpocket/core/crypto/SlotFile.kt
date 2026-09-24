package io.github.brunogutierre.bitpocket.core.crypto

import java.nio.ByteBuffer

/** Storage slots: A is the main wallet, B is reserved for the decoy wallet (M5). */
enum class SlotId(
    val dirName: String,
) {
    A("a"),
    B("b"),
}

/** Plaintext slot header; authenticated as AAD of both envelopes. */
class SlotHeader(
    val params: KdfParams,
    val salt: ByteArray,
) {
    init {
        require(salt.size == KdfParams.SALT_BYTES) { "salt must be ${KdfParams.SALT_BYTES} bytes" }
    }

    fun encode(): ByteArray =
        ByteBuffer
            .allocate(SIZE)
            .put(VERSION)
            .putInt(params.memoryKib)
            .putInt(params.iterations)
            .put(params.parallelism.toByte())
            .put(salt)
            .array()

    companion object {
        const val VERSION: Byte = 1
        const val SIZE = 1 + 4 + 4 + 1 + KdfParams.SALT_BYTES

        fun decode(buffer: ByteBuffer): SlotHeader {
            require(buffer.get() == VERSION) { "Unsupported slot version" }
            val params = KdfParams(buffer.int, buffer.int, buffer.get().toInt() and 0xFF)
            return SlotHeader(params, ByteArray(KdfParams.SALT_BYTES).also { buffer.get(it) })
        }
    }
}

/**
 * One slot file: `header | wrappedDek length (2) | wrappedDek | payload envelope`.
 *
 * - `wrappedDek` = KeyWrapper(AES-GCM(key = Argon2id(PIN), DEK)), so opening needs the device
 *   AND the PIN.
 * - The payload envelope is AES-GCM(key = DEK) over a fixed-size padded plaintext, so every
 *   slot file has the same size whatever it holds.
 */
class SlotFile(
    val header: SlotHeader,
    val wrappedDek: ByteArray,
    val payload: ByteArray,
) {
    fun encode(): ByteArray =
        ByteBuffer
            .allocate(SlotHeader.SIZE + 2 + wrappedDek.size + payload.size)
            .put(header.encode())
            .putShort(wrappedDek.size.toShort())
            .put(wrappedDek)
            .put(payload)
            .array()

    companion object {
        /** Plaintext payload capacity, including the 4-byte length prefix. */
        const val PAYLOAD_CAPACITY = 4096
        const val MAX_PAYLOAD_BYTES = PAYLOAD_CAPACITY - 4
        val PAYLOAD_ENVELOPE_SIZE = AesGcmEnvelope.sealedSize(PAYLOAD_CAPACITY)

        fun decode(bytes: ByteArray): SlotFile {
            val buffer = ByteBuffer.wrap(bytes)
            val header = SlotHeader.decode(buffer)
            val wrappedDek = ByteArray(buffer.short.toInt() and 0xFFFF).also { buffer.get(it) }
            val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }
            require(payload.size == PAYLOAD_ENVELOPE_SIZE) { "Corrupted slot payload" }
            return SlotFile(header, wrappedDek, payload)
        }

        fun pad(payload: ByteArray): ByteArray {
            require(payload.size <= MAX_PAYLOAD_BYTES) { "payload exceeds $MAX_PAYLOAD_BYTES bytes" }
            return ByteBuffer
                .allocate(PAYLOAD_CAPACITY)
                .putInt(payload.size)
                .put(payload)
                .array()
        }

        fun unpad(padded: ByteArray): ByteArray {
            val length = ByteBuffer.wrap(padded).int
            require(length in 0..MAX_PAYLOAD_BYTES) { "Corrupted slot payload length" }
            return padded.copyOfRange(4, 4 + length)
        }
    }
}
