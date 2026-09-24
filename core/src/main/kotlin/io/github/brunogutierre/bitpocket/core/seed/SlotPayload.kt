package io.github.brunogutierre.bitpocket.core.seed

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import java.nio.ByteBuffer

/**
 * Plaintext content of a slot (encrypted under the slot DEK by `SlotStore`).
 *
 * - [seedEntropy]: BIP39 entropy, the PIN-fallback copy of the seed.
 * - [biometricSeed]: the same entropy encrypted with the biometric-bound Keystore key, or null
 *   if biometrics are not set up. Signing normally uses this copy.
 * - [network]: the first wallet setting; the versioned format leaves room for more.
 *
 * Format: `version (1) | network id (1) | entropy length (1) | entropy | blob length (2) | blob`.
 */
class SlotPayload(
    val seedEntropy: ByteArray,
    val biometricSeed: ByteArray?,
    val network: SupportedNetwork,
) {
    init {
        require(seedEntropy.size in ENTROPY_SIZES) { "BIP39 entropy must be 16..32 bytes in steps of 4" }
        require((biometricSeed?.size ?: 0) <= MAX_BLOB_BYTES) { "biometric blob too large" }
    }

    fun withBiometricSeed(blob: ByteArray?) = SlotPayload(seedEntropy, blob, network)

    fun encode(): ByteArray {
        val blob = biometricSeed ?: ByteArray(0)
        return ByteBuffer
            .allocate(1 + 1 + 1 + seedEntropy.size + 2 + blob.size)
            .put(VERSION)
            .put(networkIds.getValue(network))
            .put(seedEntropy.size.toByte())
            .put(seedEntropy)
            .putShort(blob.size.toShort())
            .put(blob)
            .array()
    }

    companion object {
        private const val VERSION: Byte = 1
        private const val MAX_BLOB_BYTES = 1024
        private val ENTROPY_SIZES = setOf(16, 20, 24, 28, 32)

        // Stable ids: never reuse or renumber (enum ordinals could change).
        private val networkIds = mapOf(SupportedNetwork.SIGNET to 1.toByte(), SupportedNetwork.TESTNET4 to 2.toByte())

        /** @throws IllegalArgumentException for a malformed payload. */
        fun decode(bytes: ByteArray): SlotPayload {
            try {
                val buffer = ByteBuffer.wrap(bytes)
                require(buffer.get() == VERSION) { "Unsupported payload version" }
                val networkId = buffer.get()
                val network = networkIds.entries.singleOrNull { it.value == networkId }?.key
                requireNotNull(network) { "Unknown network id $networkId" }
                val entropy = ByteArray(buffer.get().toInt() and 0xFF).also { buffer.get(it) }
                val blob = ByteArray(buffer.short.toInt() and 0xFFFF).also { buffer.get(it) }
                require(!buffer.hasRemaining()) { "Trailing bytes in payload" }
                return SlotPayload(entropy, blob.takeIf { it.isNotEmpty() }, network)
            } catch (e: java.nio.BufferUnderflowException) {
                throw IllegalArgumentException("Truncated payload", e)
            }
        }
    }
}

/** The biometric-bound key was invalidated (e.g. a new fingerprint was enrolled). */
class BiometricKeyInvalidatedException(
    cause: Throwable? = null,
) : Exception("Biometric key permanently invalidated", cause)
