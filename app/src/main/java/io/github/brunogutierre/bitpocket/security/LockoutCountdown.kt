package io.github.brunogutierre.bitpocket.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Ticks the whole seconds left of a PIN lockout, then 0. Display only: UnlockService enforces
 * the lockout itself (its record survives restarts), so screens just disable their keys.
 */
class LockoutCountdown(
    private val scope: CoroutineScope,
    private val onSeconds: (Long) -> Unit,
) {
    private var job: Job? = null

    fun start(duration: Duration) {
        if (duration.isZero) return
        job?.cancel()
        job =
            scope.launch {
                var seconds = (duration.toMillis() + 999) / 1000
                while (seconds > 0) {
                    onSeconds(seconds)
                    delay(1000)
                    seconds--
                }
                onSeconds(0)
            }
    }
}
