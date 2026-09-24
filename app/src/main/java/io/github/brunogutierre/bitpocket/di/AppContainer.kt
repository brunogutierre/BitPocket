package io.github.brunogutierre.bitpocket.di

import android.content.Context
import io.github.brunogutierre.bitpocket.core.crypto.Argon2idKdf
import io.github.brunogutierre.bitpocket.core.crypto.FileAttemptsStore
import io.github.brunogutierre.bitpocket.core.crypto.FileSlotStorage
import io.github.brunogutierre.bitpocket.core.crypto.KdfParams
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.UnlockService
import io.github.brunogutierre.bitpocket.core.session.LaunchState
import io.github.brunogutierre.bitpocket.keystore.AndroidUnlockClock
import io.github.brunogutierre.bitpocket.keystore.KeyAliases
import io.github.brunogutierre.bitpocket.keystore.KeystoreKeyWrapper
import java.io.File

/**
 * Manual dependency injection: one instance per process, owned by [BitPocketApp]. Everything
 * that touches the Keystore is created lazily, on first use. Secrets live in noBackupFilesDir,
 * which is excluded from backups and device transfers.
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val storageDir: File = appContext.noBackupFilesDir
    private val slotStorage = FileSlotStorage(storageDir)

    val slotStore: SlotStore by lazy {
        SlotStore(slotStorage, Argon2idKdf, KeystoreKeyWrapper(KeyAliases.SLOTS), KdfParams.DEFAULT)
    }

    val unlockService: UnlockService by lazy {
        UnlockService(
            slots = slotStore,
            attempts = FileAttemptsStore(File(storageDir, "attempts.bin"), KeystoreKeyWrapper(KeyAliases.ATTEMPTS)),
            clock = AndroidUnlockClock(appContext.contentResolver),
        )
    }

    fun launchState(): LaunchState = LaunchState.of(slotStorage)
}
