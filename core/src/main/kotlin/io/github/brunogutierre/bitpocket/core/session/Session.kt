package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet

/**
 * The wallet unlocked in this process. It keeps NO seed: only what is needed while unlocked (the
 * open pockets, the network and the biometric-sealed seed). Signing asks for the seed again,
 * through biometrics or the PIN.
 */
class UnlockedWallet(
    val slot: SlotId,
    val network: SupportedNetwork,
    /** The seed sealed by the biometric Keystore key, or null when biometric signing is off. */
    val biometricSeed: ByteArray?,
    val spending: PocketWallet,
)

/** Holds the unlocked wallet in memory only; [lock] closes its pockets. */
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
        current.biometricSeed?.wipe()
        current.spending.close()
    }
}
