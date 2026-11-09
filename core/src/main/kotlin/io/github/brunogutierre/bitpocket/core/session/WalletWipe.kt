package io.github.brunogutierre.bitpocket.core.session

import java.io.File

/**
 * Deletes every file BitPocket stores under [root]: both slots, the attempts record and all wallet
 * databases. The Keystore keys are deleted by the app. Afterwards the app starts at onboarding.
 */
object WalletWipe {
    private val ENTRIES = listOf("slots", "attempts.bin", "wallets")

    /** @return true when nothing is left behind. */
    fun wipe(root: File): Boolean =
        ENTRIES.map { File(root, it) }.all { entry ->
            !entry.exists() || entry.deleteRecursively()
        }
}
