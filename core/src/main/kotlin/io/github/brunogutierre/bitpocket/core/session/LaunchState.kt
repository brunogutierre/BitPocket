package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStorage

/** Where the app starts: onboarding when no wallet exists yet, otherwise the lock screen. */
enum class LaunchState {
    ONBOARDING,
    LOCKED,
    ;

    companion object {
        /** Both slots are always written together, so any slot file means a wallet exists. */
        fun of(storage: SlotStorage): LaunchState = if (SlotId.entries.any { storage.read(it) != null }) LOCKED else ONBOARDING
    }
}
