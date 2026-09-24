package io.github.brunogutierre.bitpocket.core.crypto

/**
 * Device-bound encryption of slot files (Android Keystore in production). A wrapped file is
 * useless off the device, so it cannot be brute-forced offline. Wrapping must be randomized
 * (fresh nonce), and for a given input size the output size must be constant.
 * [wrap] throws [KeyUnavailableException] when the key cannot be used.
 */
interface KeyWrapper {
    fun wrap(plaintext: ByteArray): ByteArray

    /**
     * @throws AuthenticationFailedException if [wrapped] was not produced by this wrapper.
     * @throws KeyUnavailableException if the key cannot be used right now (e.g. device locked).
     */
    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * The device key exists but cannot be used right now (device locked, Keystore busy...).
 * Distinct from [AuthenticationFailedException] so callers never mistake a transient failure
 * for bad data (e.g. [SlotStore] would otherwise replace a real slot with a decoy).
 */
class KeyUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
