package io.github.brunogutierre.bitpocket.core.crypto

import java.time.Duration

sealed interface UnlockResult {
    /** [payload] is the decrypted slot content; the caller must wipe it after use. */
    class Unlocked(
        val slot: SlotId,
        val payload: ByteArray,
    ) : UnlockResult

    /** Wrong PIN; [lockout] is non-zero when this failure started a lockout. */
    data class Wrong(
        val lockout: Duration,
    ) : UnlockResult

    data class LockedOut(
        val remaining: Duration,
    ) : UnlockResult
}

/**
 * Unlocks whichever slot the PIN opens, with the same work for every outcome: the KDF and the
 * key unwrap always run for BOTH slots, so timing does not reveal which slot matched.
 *
 * The failure counter is incremented BEFORE the KDF runs and reset only once the payload has
 * been decrypted, so killing the app mid-attempt cannot be used to get free guesses. Blocking
 * and slow (~1 s): call it from a background dispatcher.
 */
class UnlockService(
    private val slots: SlotStore,
    private val attempts: AttemptsStore,
    private val clock: UnlockClock,
    private val backoff: BackoffPolicy = BackoffPolicy(),
) {
    /** @throws AuthenticationFailedException if the PIN is right but the payload was tampered with. */
    fun unlock(pin: CharArray): UnlockResult {
        val now = clock.now()
        val state = attempts.read()
        val remaining = backoff.remaining(state, now)
        if (!remaining.isZero) {
            attempts.write(backoff.anchoredAt(state, now))
            return UnlockResult.LockedOut(remaining)
        }

        val pessimistic = backoff.afterFailure(state, now)
        attempts.write(pessimistic)

        val files = SlotId.entries.associateWith { slots.read(it) }
        val deks = SlotId.entries.associateWith { slots.openDek(it, files[it], pin) }
        try {
            val (slot, dek) =
                deks.entries.firstOrNull { it.value != null }
                    ?: return UnlockResult.Wrong(Duration.ofMillis(pessimistic.lockoutRemainingMs))
            val payload = slots.openPayload(slot, files.getValue(slot)!!, dek!!)
            attempts.write(AttemptsState.NONE)
            return UnlockResult.Unlocked(slot, payload)
        } finally {
            deks.values.forEach { it?.wipe() }
        }
    }
}
