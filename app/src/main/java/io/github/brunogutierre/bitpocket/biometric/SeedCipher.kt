package io.github.brunogutierre.bitpocket.biometric

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.seed.BiometricKeyInvalidatedException
import io.github.brunogutierre.bitpocket.keystore.KeyAliases
import io.github.brunogutierre.bitpocket.keystore.KeystoreKeys
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric-bound encryption of the seed. The Keystore key requires a BIOMETRIC_STRONG
 * authentication for every use (device credential is NOT accepted) and is invalidated when a
 * new biometric is enrolled.
 *
 * Usage: get a cipher from [encryptCipher] / [decryptCipher], authenticate it with
 * `BiometricPrompt.CryptoObject`, then call [seal] / [open] with the authenticated cipher.
 * Blob format: `version (1) | iv (12) | ciphertext | tag (16)`.
 */
class SeedCipher(
    private val alias: String = KeyAliases.SEED,
) {
    /** @throws BiometricKeyInvalidatedException after a new biometric enrollment. */
    fun encryptCipher(): Cipher = initCipher { init(Cipher.ENCRYPT_MODE, key()) }

    /** @throws BiometricKeyInvalidatedException after a new biometric enrollment. */
    fun decryptCipher(blob: ByteArray): Cipher {
        if (blob.size < OVERHEAD || blob[0] != VERSION) throw AuthenticationFailedException("Not a seed blob")
        return initCipher { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 1, IV_BYTES)) }
    }

    fun seal(
        authenticated: Cipher,
        seed: ByteArray,
    ): ByteArray = byteArrayOf(VERSION) + authenticated.iv + authenticated.doFinal(seed)

    fun open(
        authenticated: Cipher,
        blob: ByteArray,
    ): ByteArray =
        try {
            authenticated.doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
        } catch (e: AEADBadTagException) {
            throw AuthenticationFailedException("Seed blob failed authentication", e)
        }

    /** Deletes the (invalidated) key and creates a new one; old blobs become unreadable. */
    fun resetKey() {
        KeystoreKeys.delete(alias)
        key()
    }

    private fun key() =
        KeystoreKeys.getOrCreate(alias) {
            setUserAuthenticationRequired(true)
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            setInvalidatedByBiometricEnrollment(true)
        }

    private inline fun initCipher(block: Cipher.() -> Unit): Cipher =
        try {
            Cipher.getInstance(TRANSFORMATION).apply(block)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException(e)
        } catch (e: GeneralSecurityException) {
            throw KeyUnavailableException("Seed key unavailable", e)
        } catch (e: ProviderException) {
            throw KeyUnavailableException("Seed key unavailable", e)
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val OVERHEAD = 1 + IV_BYTES + TAG_BITS / 8
    }
}
