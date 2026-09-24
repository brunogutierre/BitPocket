package io.github.brunogutierre.bitpocket.core.crypto

import java.time.Clock
import java.time.Instant

sealed interface UnlockResult {
    /** [payload] is the decrypted slot content; the caller must wipe it after use. */
    class Unlocked(
        val slot: SlotId,
        val payload: ByteArray,
    ) : UnlockResult

    /** Wrong PIN; [lockedUntil] is set when this failure started a lockout. */
    data class Wrong(
        val lockedUntil: Instant?,
    ) : UnlockResult

    data class LockedOut(
        val until: Instant,
    ) : UnlockResult
}

/**
 * Unlocks whichever slot the PIN opens, with the same work for every outcome: the KDF and the
 * key unwrap always run for BOTH slots, so timing does not reveal which slot matched.
 *
 * The failure counter is incremented BEFORE the KDF runs and reset only on success, so killing
 * the app mid-attempt cannot be used to get free guesses. Blocking and slow (~1 s): call it
 * from a background dispatcher.
 */
class UnlockService(
    private val slots: SlotStore,
    private val attempts: AttemptsStore,
    private val backoff: BackoffPolicy = BackoffPolicy(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun unlock(pin: CharArray): UnlockResult {
        val now = clock.instant()
        val state = attempts.read()
        backoff.lockedUntil(state)?.takeIf { now.isBefore(it) }?.let { until ->
            attempts.write(state)
            return UnlockResult.LockedOut(until)
        }

        val pessimistic = AttemptsState(state.failedAttempts + 1, lastFailureAt = now)
        attempts.write(pessimistic)

        val files = SlotId.entries.associateWith { slots.read(it) }
        val deks = SlotId.entries.associateWith { slots.openDek(it, files[it], pin) }
        try {
            val (slot, dek) =
                deks.entries.firstOrNull { it.value != null }
                    ?: return UnlockResult.Wrong(backoff.lockedUntil(pessimistic))
            attempts.write(AttemptsState.NONE)
            return UnlockResult.Unlocked(slot, slots.openPayload(slot, files.getValue(slot)!!, dek!!))
        } finally {
            deks.values.forEach { it?.wipe() }
        }
    }
}
