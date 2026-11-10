package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet

/** The wallet unlocked in this process, with its open [spending] pocket. */
class UnlockedWallet(
    val slot: SlotId,
    val payload: SlotPayload,
    val spending: PocketWallet,
)

/** Holds the unlocked wallet in memory only; [lock] wipes the seed copy and closes the pockets. */
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
        val current = wallet ?: return
        wallet = null
        current.payload.seedEntropy.wipe()
        current.spending.close()
    }
}
