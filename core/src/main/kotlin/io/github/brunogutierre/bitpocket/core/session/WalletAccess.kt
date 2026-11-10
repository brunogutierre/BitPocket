package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.UnlockResult
import io.github.brunogutierre.bitpocket.core.crypto.UnlockService
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.BiometricRewrap
import io.github.brunogutierre.bitpocket.core.seed.BiometricSeedVault
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.wallet.WalletOpener
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

sealed interface UnlockOutcome {
    data object Unlocked : UnlockOutcome

    /** [lockout] is non-zero when this failure started a lockout. */
    data class WrongPin(
        val lockout: Duration,
    ) : UnlockOutcome

    data class LockedOut(
        val remaining: Duration,
    ) : UnlockOutcome

    /** The device key is unavailable or the slot is unreadable (not a PIN problem). */
    data object Failed : UnlockOutcome
}

/** The seed for one signing operation. */
sealed interface SeedOutcome {
    /** The caller wipes [entropy] as soon as it has signed. */
    class Seed(
        val entropy: ByteArray,
    ) : SeedOutcome

    data class WrongPin(
        val lockout: Duration,
    ) : SeedOutcome

    data class LockedOut(
        val remaining: Duration,
    ) : SeedOutcome

    data object Failed : SeedOutcome
}

/** Result of a settings change that re-enters the PIN. */
sealed interface SettingsOutcome {
    data object Done : SettingsOutcome

    data class WrongPin(
        val lockout: Duration,
    ) : SettingsOutcome

    data class LockedOut(
        val remaining: Duration,
    ) : SettingsOutcome

    /** Keystore/storage error, or the biometric sealing was cancelled. */
    data object Failed : SettingsOutcome
}

/** Creates the wallet and unlocks it into the [Session]. Blocking and slow: call off the main thread. */
interface WalletAccess {
    fun create(
        pin: CharArray,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    )

    fun unlock(pin: CharArray): UnlockOutcome

    /**
     * Re-enters the PIN of the open session to get its seed for signing. It goes through the same
     * backoff as unlocking, and a PIN of another slot is just a wrong PIN.
     */
    fun seedForSigning(pin: CharArray): SeedOutcome

    /** Re-encrypts the slot under [newPin] after checking [currentPin]. */
    fun changePin(
        currentPin: CharArray,
        newPin: CharArray,
    ): SettingsOutcome

    /**
     * Turns biometric signing on (a new biometric key seals the seed; [vault] shows the prompt) or
     * off ([vault] null). Either way the slot is saved again under [pin].
     */
    suspend fun setBiometricSigning(
        pin: CharArray,
        vault: BiometricSeedVault?,
    ): SettingsOutcome

    /** Checks the session PIN (e.g. before wiping) without decrypting anything for the caller. */
    fun verifyPin(pin: CharArray): SettingsOutcome

    /** Saves new security settings in the slot after checking [pin] (ADR 0005). */
    fun updateSettings(
        pin: CharArray,
        settings: WalletSettings,
    ): SettingsOutcome
}

/** [WalletAccess] over the slot store. M1 uses slot A only; slot B stays a decoy. */
class SlotWalletAccess(
    private val slots: SlotStore,
    private val unlockService: UnlockService,
    private val session: Session,
    private val opener: WalletOpener,
) : WalletAccess {
    override fun create(
        pin: CharArray,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    ) {
        // Biometric signing is turned on later, in Settings, so no biometric copy yet.
        val payload = SlotPayload(seedEntropy.copyOf(), biometricSeed = null, network = network)
        save(SlotId.A, pin, payload)
        openSession(SlotId.A, payload)
    }

    override fun unlock(pin: CharArray): UnlockOutcome =
        try {
            when (val result = unlockService.unlock(pin)) {
                is UnlockResult.Unlocked -> open(result)
                is UnlockResult.Wrong -> UnlockOutcome.WrongPin(result.lockout)
                is UnlockResult.LockedOut -> UnlockOutcome.LockedOut(result.remaining)
            }
        } catch (e: KeyUnavailableException) {
            UnlockOutcome.Failed
        } catch (e: AuthenticationFailedException) {
            UnlockOutcome.Failed
        }

    private fun open(result: UnlockResult.Unlocked): UnlockOutcome {
        val payload =
            try {
                SlotPayload.decode(result.payload)
            } catch (e: IllegalArgumentException) {
                return UnlockOutcome.Failed
            } finally {
                result.payload.wipe()
            }
        return try {
            openSession(result.slot, payload)
            UnlockOutcome.Unlocked
        } catch (e: Exception) {
            // The wallet database could not be opened (I/O error): not a PIN problem.
            UnlockOutcome.Failed
        }
    }

    override fun seedForSigning(pin: CharArray): SeedOutcome =
        when (val result = reauthenticate(pin)) {
            is Reauth.Ok -> {
                result.payload.biometricSeed?.wipe()
                SeedOutcome.Seed(result.payload.seedEntropy)
            }

            is Reauth.Denied -> {
                when (val outcome = result.outcome) {
                    is SettingsOutcome.WrongPin -> SeedOutcome.WrongPin(outcome.lockout)
                    is SettingsOutcome.LockedOut -> SeedOutcome.LockedOut(outcome.remaining)
                    else -> SeedOutcome.Failed
                }
            }
        }

    override fun changePin(
        currentPin: CharArray,
        newPin: CharArray,
    ): SettingsOutcome =
        withPayload(currentPin) { slot, payload ->
            save(slot, newPin, payload)
            SettingsOutcome.Done
        }

    override suspend fun setBiometricSigning(
        pin: CharArray,
        vault: BiometricSeedVault?,
    ): SettingsOutcome =
        when (val result = reauthenticate(pin)) {
            is Reauth.Denied -> {
                result.outcome
            }

            is Reauth.Ok -> {
                try {
                    val saved =
                        if (vault == null) {
                            result.payload.withBiometricSeed(null).also { save(result.slot, pin, it) }
                        } else {
                            BiometricRewrap(slots, vault).rewrap(result.slot, pin, result.payload)
                        }
                    session.updateBiometricSeed(saved.biometricSeed?.copyOf())
                    SettingsOutcome.Done
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sealing failed or was cancelled: the slot was saved without a biometric copy.
                    session.updateBiometricSeed(null)
                    SettingsOutcome.Failed
                } finally {
                    result.payload.seedEntropy.wipe()
                }
            }
        }

    override fun verifyPin(pin: CharArray): SettingsOutcome = withPayload(pin) { _, _ -> SettingsOutcome.Done }

    override fun updateSettings(
        pin: CharArray,
        settings: WalletSettings,
    ): SettingsOutcome =
        withPayload(pin) { slot, payload ->
            save(slot, pin, payload.withSettings(settings))
            session.updateSettings(settings)
            SettingsOutcome.Done
        }

    private sealed interface Reauth {
        class Ok(
            val slot: SlotId,
            val payload: SlotPayload,
        ) : Reauth

        class Denied(
            val outcome: SettingsOutcome,
        ) : Reauth
    }

    /** The session PIN, checked like an unlock (same backoff); another slot's PIN is wrong here. */
    private fun reauthenticate(pin: CharArray): Reauth {
        val slot = session.wallet?.slot ?: return Reauth.Denied(SettingsOutcome.Failed)
        val result =
            try {
                unlockService.unlock(pin, expected = slot)
            } catch (e: KeyUnavailableException) {
                return Reauth.Denied(SettingsOutcome.Failed)
            } catch (e: AuthenticationFailedException) {
                return Reauth.Denied(SettingsOutcome.Failed)
            }
        return when (result) {
            is UnlockResult.Wrong -> {
                Reauth.Denied(SettingsOutcome.WrongPin(result.lockout))
            }

            is UnlockResult.LockedOut -> {
                Reauth.Denied(SettingsOutcome.LockedOut(result.remaining))
            }

            is UnlockResult.Unlocked -> {
                try {
                    Reauth.Ok(result.slot, SlotPayload.decode(result.payload))
                } catch (e: IllegalArgumentException) {
                    Reauth.Denied(SettingsOutcome.Failed)
                } finally {
                    result.payload.wipe()
                }
            }
        }
    }

    /** Runs [block] with the re-authenticated payload, then wipes its seed. */
    private fun withPayload(
        pin: CharArray,
        block: (SlotId, SlotPayload) -> SettingsOutcome,
    ): SettingsOutcome =
        when (val result = reauthenticate(pin)) {
            is Reauth.Denied -> {
                result.outcome
            }

            is Reauth.Ok -> {
                try {
                    block(result.slot, result.payload)
                } catch (e: KeyUnavailableException) {
                    SettingsOutcome.Failed
                } finally {
                    result.payload.seedEntropy.wipe()
                }
            }
        }

    private fun save(
        slot: SlotId,
        pin: CharArray,
        payload: SlotPayload,
    ) {
        val encoded = payload.encode()
        try {
            slots.save(slot, pin, encoded)
        } finally {
            encoded.wipe()
        }
    }

    /** Opens the pockets, then drops the seed: the session keeps public data only. */
    private fun openSession(
        slot: SlotId,
        payload: SlotPayload,
    ) {
        try {
            val spending = opener.open(payload)
            session.open(UnlockedWallet(slot, payload.network, payload.biometricSeed, spending, payload.settings))
        } finally {
            payload.seedEntropy.wipe()
        }
    }
}
