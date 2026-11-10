package io.github.brunogutierre.bitpocket.biometric

import androidx.fragment.app.FragmentActivity
import io.github.brunogutierre.bitpocket.core.seed.BiometricSeedVault

/** The prompt was dismissed: biometric signing stays off. */
class BiometricCancelledException : Exception("Biometric prompt cancelled")

/** [BiometricSeedVault] that seals the seed after a BiometricPrompt on [activity]. */
class PromptBiometricVault(
    private val activity: FragmentActivity,
    private val seedCipher: SeedCipher,
    private val title: String,
    private val negativeButton: String,
) : BiometricSeedVault {
    override fun resetKey() = seedCipher.resetKey()

    override suspend fun seal(seed: ByteArray): ByteArray {
        val authenticated = activity.authenticate(seedCipher.encryptCipher(), title, negativeButton) ?: throw BiometricCancelledException()
        return seedCipher.seal(authenticated, seed)
    }
}
