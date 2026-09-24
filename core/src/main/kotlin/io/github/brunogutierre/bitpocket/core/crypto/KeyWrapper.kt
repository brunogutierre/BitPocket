package io.github.brunogutierre.bitpocket.core.crypto

/**
 * Device-bound wrapping of small secrets (Android Keystore in production).
 * A wrapped value is useless off the device, so a copied slot file cannot be brute-forced
 * offline. For a given input size, the output size must be constant.
 */
interface KeyWrapper {
    fun wrap(plaintext: ByteArray): ByteArray

    /** @throws AuthenticationFailedException if [wrapped] was not produced by this wrapper. */
    fun unwrap(wrapped: ByteArray): ByteArray
}
