package io.github.brunogutierre.bitpocket.di

import android.content.Context
import io.github.brunogutierre.bitpocket.biometric.BiometricAvailability
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedOpener
import io.github.brunogutierre.bitpocket.biometric.BiometricStatus
import io.github.brunogutierre.bitpocket.biometric.SeedCipher
import io.github.brunogutierre.bitpocket.core.crypto.Argon2idKdf
import io.github.brunogutierre.bitpocket.core.crypto.FileAttemptsStore
import io.github.brunogutierre.bitpocket.core.crypto.FileSlotStorage
import io.github.brunogutierre.bitpocket.core.crypto.KdfParams
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.UnlockService
import io.github.brunogutierre.bitpocket.core.crypto.writeAtomically
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.session.AutoLocker
import io.github.brunogutierre.bitpocket.core.session.LaunchState
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SlotWalletAccess
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.core.session.WalletWipe
import io.github.brunogutierre.bitpocket.core.vault.HeirClaim
import io.github.brunogutierre.bitpocket.core.vault.HeirClaimOpen
import io.github.brunogutierre.bitpocket.core.wallet.BdkWalletOpener
import io.github.brunogutierre.bitpocket.core.wallet.EsploraChainClient
import io.github.brunogutierre.bitpocket.keystore.AndroidUnlockClock
import io.github.brunogutierre.bitpocket.keystore.KeyAliases
import io.github.brunogutierre.bitpocket.keystore.KeystoreKeyWrapper
import io.github.brunogutierre.bitpocket.keystore.KeystoreKeys
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

    /** The unlocked wallet, in memory only. */
    val session = Session()

    val autoLocker = AutoLocker(session)

    /** Watch-only wallet databases live next to the slots, in `wallets/<id>/`. */
    private val walletOpener = BdkWalletOpener(storageDir) { network -> EsploraChainClient(network.esploraBaseUrl) }

    val walletAccess: WalletAccess by lazy { SlotWalletAccess(slotStore, unlockService, session, walletOpener) }

    val mnemonicService: MnemonicService by lazy { MnemonicService() }

    /** Biometric-bound seed encryption (Keystore key created on first use). */
    val seedCipher: SeedCipher by lazy { SeedCipher() }

    val biometricSeedOpener: BiometricSeedOpener by lazy { BiometricSeedOpener(seedCipher) }

    private fun vaultBackupFile(): File? = session.wallet?.dir?.let { File(it, "vault-backup.bpvb") }

    /** Keeps the Vault backup next to the wallet databases, so Settings can export it again. */
    fun keepVaultBackup(bytes: ByteArray) {
        val file = vaultBackupFile() ?: return
        file.parentFile?.mkdirs()
        writeAtomically(file, bytes)
    }

    fun vaultBackup(): ByteArray? = vaultBackupFile()?.takeIf { it.isFile }?.readBytes()

    /** Opens a Vault backup for an heir claim, with this app's Esplora clients. */
    fun openHeirClaim(
        backup: ByteArray,
        heirSeed: ByteArray,
    ): HeirClaimOpen = HeirClaim.open(backup, heirSeed) { network -> EsploraChainClient(network.esploraBaseUrl) }

    fun biometricAvailable(): Boolean = BiometricAvailability.status(appContext) == BiometricStatus.AVAILABLE

    /** Locks, then deletes every wallet file and Keystore key: the app starts over at onboarding. */
    fun wipeAll() {
        session.lock()
        onboardingDraft.clear()
        WalletWipe.wipe(storageDir)
        listOf(KeyAliases.SLOTS, KeyAliases.ATTEMPTS, KeyAliases.SEED).forEach(KeystoreKeys::delete)
    }

    /** The wallet being created or restored; memory only. */
    val onboardingDraft = OnboardingDraft()

    fun launchState(): LaunchState = LaunchState.of(slotStorage)
}
