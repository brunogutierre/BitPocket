package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.settings.AutoLock

/**
 * Locks the [session] once the app has stayed in the background for the wallet's auto-lock time.
 * Times come from a monotonic clock (Android: `SystemClock.elapsedRealtime`), so changing the
 * wall clock cannot keep a wallet unlocked.
 */
class AutoLocker(
    private val session: Session,
) {
    private var backgroundedAtMs: Long? = null

    fun onBackground(nowMs: Long) {
        val wallet = session.wallet ?: return
        if (wallet.settings.autoLock == AutoLock.IMMEDIATELY) {
            session.lock()
        } else {
            backgroundedAtMs = nowMs
        }
    }

    fun onForeground(nowMs: Long) {
        val since = backgroundedAtMs ?: return
        backgroundedAtMs = null
        val timeout = session.wallet?.settings?.autoLock ?: return
        if (nowMs - since >= timeout.seconds * 1_000) session.lock()
    }
}
