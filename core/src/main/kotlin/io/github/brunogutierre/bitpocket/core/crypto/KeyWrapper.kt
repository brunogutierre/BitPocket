package io.github.brunogutierre.bitpocket.core.crypto

/**
 * Device-bound encryption of slot files (Android Keystore in production). A wrapped file is
 * useless off the device, so it cannot be brute-forced offline. Wrapping must be randomized
 * (fresh nonce), and for a given input size the output size must be constant.
 */
interface KeyWrapper {
    fun wrap(plaintext: ByteArray): ByteArray

    /** @throws AuthenticationFailedException if [wrapped] was not produced by this wrapper. */
    fun unwrap(wrapped: ByteArray): ByteArray
}
