package io.github.brunogutierre.bitpocket.biometric

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.seed.BiometricKeyInvalidatedException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/** Why the biometric seed could not be opened; every case falls back to the PIN. */
enum class BiometricFailure {
    /** Cancelled, too many attempts, or no usable sensor right now. */
    CANCELLED,

    /** A new biometric was enrolled: the key is gone and biometric signing must be set up again. */
    INVALIDATED,
}

sealed interface BiometricSeedResult {
    /** The caller wipes [entropy] after signing. */
    class Opened(
        val entropy: ByteArray,
    ) : BiometricSeedResult

    data class Failed(
        val reason: BiometricFailure,
    ) : BiometricSeedResult
}

/** Opens the biometric-sealed seed: BiometricPrompt (Class 3 only) authorizes the Keystore cipher. */
class BiometricSeedOpener(
    private val seedCipher: SeedCipher,
) {
    suspend fun open(
        activity: FragmentActivity,
        blob: ByteArray,
        title: String,
        negativeButton: String,
    ): BiometricSeedResult {
        val cipher =
            try {
                seedCipher.decryptCipher(blob)
            } catch (e: BiometricKeyInvalidatedException) {
                return BiometricSeedResult.Failed(BiometricFailure.INVALIDATED)
            } catch (e: KeyUnavailableException) {
                return BiometricSeedResult.Failed(BiometricFailure.INVALIDATED)
            } catch (e: AuthenticationFailedException) {
                return BiometricSeedResult.Failed(BiometricFailure.INVALIDATED)
            }
        val authenticated =
            activity.authenticate(cipher, title, negativeButton) ?: return BiometricSeedResult.Failed(BiometricFailure.CANCELLED)
        return try {
            BiometricSeedResult.Opened(seedCipher.open(authenticated, blob))
        } catch (e: AuthenticationFailedException) {
            BiometricSeedResult.Failed(BiometricFailure.INVALIDATED)
        }
    }
}

/** Shows BiometricPrompt for [cipher]; returns the authenticated cipher, or null on error/cancel. */
suspend fun FragmentActivity.authenticate(
    cipher: Cipher,
    title: String,
    negativeButton: String,
): Cipher? =
    suspendCancellableCoroutine { continuation ->
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(result.cryptoObject?.cipher)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (continuation.isActive) continuation.resume(null)
                }
                // onAuthenticationFailed (one non-matching finger) keeps the prompt open: nothing to do.
            }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setNegativeButtonText(negativeButton)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
