package io.github.brunogutierre.bitpocket.core.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when authenticated decryption fails: wrong key, wrong AAD or tampered data. */
class AuthenticationFailedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * AES-256-GCM envelope: `version (1) | nonce (12) | ciphertext | tag (16)`.
 * The nonce is random per seal; [aad] is authenticated but not stored.
 */
object AesGcmEnvelope {
    const val VERSION: Byte = 1
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    const val OVERHEAD = 1 + NONCE_BYTES + TAG_BYTES

    fun sealedSize(plaintextBytes: Int) = plaintextBytes + OVERHEAD

    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0),
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return byteArrayOf(VERSION) + nonce + AesGcm.encrypt(key, nonce, plaintext, aad)
    }

    fun open(
        key: ByteArray,
        envelope: ByteArray,
        aad: ByteArray = ByteArray(0),
    ): ByteArray {
        if (envelope.size < OVERHEAD) throw AuthenticationFailedException("Envelope too short")
        if (envelope[0] != VERSION) throw AuthenticationFailedException("Unsupported envelope version")
        val nonce = envelope.copyOfRange(1, 1 + NONCE_BYTES)
        return AesGcm.decrypt(key, nonce, envelope.copyOfRange(1 + NONCE_BYTES, envelope.size), aad)
    }
}

/** Raw AES-GCM (JCA) with a caller-provided nonce; output is `ciphertext | tag`. */
internal object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray = cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

    fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertextAndTag: ByteArray,
        aad: ByteArray,
    ): ByteArray =
        try {
            cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(ciphertextAndTag)
        } catch (e: GeneralSecurityException) {
            throw AuthenticationFailedException("Authenticated decryption failed", e)
        }

    private fun cipher(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
    ): Cipher {
        require(key.size == 32) { "AES-256 key required" }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(AesGcmEnvelope.TAG_BYTES * 8, nonce))
            updateAAD(aad)
        }
    }
}
