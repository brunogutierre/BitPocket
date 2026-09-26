package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload

/** The wallet unlocked in this process. */
class UnlockedWallet(
    val slot: SlotId,
    val payload: SlotPayload,
)

/** Holds the unlocked wallet in memory only; [lock] wipes the seed copy. */
class Session {
    @Volatile
    var wallet: UnlockedWallet? = null
        private set

    val isUnlocked: Boolean get() = wallet != null

    fun open(wallet: UnlockedWallet) {
        lock()
        this.wallet = wallet
    }

    fun lock() {
        wallet?.payload?.seedEntropy?.wipe()
        wallet = null
    }
}
