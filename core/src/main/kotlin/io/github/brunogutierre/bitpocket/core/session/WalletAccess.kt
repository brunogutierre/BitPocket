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

/** Creates the wallet and unlocks it into the [Session]. Blocking and slow: call off the main thread. */
interface WalletAccess {
    fun create(
        pin: CharArray,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    )

    fun unlock(pin: CharArray): UnlockOutcome
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
        session.open(UnlockedWallet(SlotId.A, payload, opener.open(payload)))
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
            session.open(UnlockedWallet(result.slot, payload, opener.open(payload)))
            UnlockOutcome.Unlocked
        } catch (e: Exception) {
            // The wallet database could not be opened (I/O error): not a PIN problem.
            payload.seedEntropy.wipe()
            UnlockOutcome.Failed
        }
    }
}
