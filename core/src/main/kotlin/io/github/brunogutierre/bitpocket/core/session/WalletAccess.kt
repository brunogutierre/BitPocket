package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.UnlockResult
import io.github.brunogutierre.bitpocket.core.crypto.UnlockService
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.wallet.WalletOpener
import java.time.Duration

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
        // Biometric sealing is deferred to the first send, so no biometric copy yet.
        val payload = SlotPayload(seedEntropy.copyOf(), biometricSeed = null, network = network)
        val encoded = payload.encode()
        try {
            slots.save(SlotId.A, pin, encoded)
        } finally {
            encoded.wipe()
        }
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

    override fun seedForSigning(pin: CharArray): SeedOutcome {
        val slot = session.wallet?.slot ?: return SeedOutcome.Failed
        return try {
            when (val result = unlockService.unlock(pin, expected = slot)) {
                is UnlockResult.Unlocked -> seedOf(result)
                is UnlockResult.Wrong -> SeedOutcome.WrongPin(result.lockout)
                is UnlockResult.LockedOut -> SeedOutcome.LockedOut(result.remaining)
            }
        } catch (e: KeyUnavailableException) {
            SeedOutcome.Failed
        } catch (e: AuthenticationFailedException) {
            SeedOutcome.Failed
        }
    }

    private fun seedOf(result: UnlockResult.Unlocked): SeedOutcome =
        try {
            val payload = SlotPayload.decode(result.payload)
            payload.biometricSeed?.wipe()
            SeedOutcome.Seed(payload.seedEntropy)
        } catch (e: IllegalArgumentException) {
            SeedOutcome.Failed
        } finally {
            result.payload.wipe()
        }

    /** Opens the pockets, then drops the seed: the session keeps public data only. */
    private fun openSession(
        slot: SlotId,
        payload: SlotPayload,
    ) {
        try {
            val spending = opener.open(payload)
            session.open(UnlockedWallet(slot, payload.network, payload.biometricSeed, spending))
        } finally {
            payload.seedEntropy.wipe()
        }
    }
}
