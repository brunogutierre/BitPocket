package io.github.brunogutierre.bitpocket.core.seed

import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.wipe

/** Biometric-bound seed encryption (Keystore key + BiometricPrompt in :app). */
interface BiometricSeedVault {
    /** Deletes the (invalidated) biometric key and creates a new one. */
    fun resetKey()

    /** Encrypts [seed] with the biometric key, after a biometric authentication. */
    suspend fun seal(seed: ByteArray): ByteArray
}

/**
 * Recovers biometric signing after [BiometricKeyInvalidatedException] (a new biometric was
 * enrolled): the user unlocks with the PIN, then the seed is re-encrypted from the PIN-fallback
 * copy under a brand-new biometric key and the slot is saved.
 */
class BiometricRewrap(
    private val slots: SlotStore,
    private val vault: BiometricSeedVault,
) {
    /**
     * @param payload the slot content just unlocked with [pin].
     * @return the saved payload. If sealing fails (e.g. the prompt is cancelled), the slot is
     *   saved without a biometric copy, since the old one is unreadable, and the error is rethrown.
     */
    suspend fun rewrap(
        slot: SlotId,
        pin: CharArray,
        payload: SlotPayload,
    ): SlotPayload {
        vault.resetKey()
        val blob =
            try {
                vault.seal(payload.seedEntropy)
            } catch (e: Exception) {
                save(slot, pin, payload.withBiometricSeed(null))
                throw e
            }
        return save(slot, pin, payload.withBiometricSeed(blob))
    }

    private fun save(
        slot: SlotId,
        pin: CharArray,
        payload: SlotPayload,
    ): SlotPayload {
        val encoded = payload.encode()
        try {
            slots.save(slot, pin, encoded)
        } finally {
            encoded.wipe()
        }
        return payload
    }
}
