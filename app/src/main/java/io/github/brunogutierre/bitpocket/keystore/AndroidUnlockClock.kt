package io.github.brunogutierre.bitpocket.keystore

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import io.github.brunogutierre.bitpocket.core.crypto.ClockReading
import io.github.brunogutierre.bitpocket.core.crypto.UnlockClock

/** Monotonic clock for lockouts: elapsed realtime since boot plus the device boot counter. */
class AndroidUnlockClock(
    private val contentResolver: ContentResolver,
) : UnlockClock {
    override fun now() =
        ClockReading(
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0),
        )
}
