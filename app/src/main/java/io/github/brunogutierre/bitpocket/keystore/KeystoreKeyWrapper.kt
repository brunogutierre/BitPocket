package io.github.brunogutierre.bitpocket.keystore

import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.crypto.KeyWrapper
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * [KeyWrapper] backed by a no-auth AndroidKeyStore AES-256-GCM key (usable only while the device
 * is unlocked). Output: `version (1) | iv (12, chosen by Keystore) | ciphertext | tag (16)`.
 */
class KeystoreKeyWrapper(
    private val alias: String,
) : KeyWrapper {
    override fun wrap(plaintext: ByteArray): ByteArray =
        keystoreCall {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, KeystoreKeys.getOrCreate(alias)) }
            check(cipher.iv.size == IV_BYTES) { "Unexpected IV size" }
            byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(plaintext)
        }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (wrapped.size < OVERHEAD || wrapped[0] != VERSION) throw AuthenticationFailedException("Not a wrapped value")
        return keystoreCall {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, KeystoreKeys.getOrCreate(alias), GCMParameterSpec(TAG_BITS, wrapped, 1, IV_BYTES))
            try {
                cipher.doFinal(wrapped, 1 + IV_BYTES, wrapped.size - 1 - IV_BYTES)
            } catch (e: AEADBadTagException) {
                throw AuthenticationFailedException("Wrapped value failed authentication", e)
            }
        }
    }

    private inline fun <T> keystoreCall(block: () -> T): T =
        try {
            block()
        } catch (e: GeneralSecurityException) {
            throw KeyUnavailableException("Keystore key $alias unavailable", e)
        } catch (e: ProviderException) {
            throw KeyUnavailableException("Keystore key $alias unavailable", e)
        }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION: Byte = 1
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        const val OVERHEAD = 1 + IV_BYTES + TAG_BITS / 8
    }
}
