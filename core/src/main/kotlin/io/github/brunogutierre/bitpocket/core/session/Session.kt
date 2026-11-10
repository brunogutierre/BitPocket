package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.wallet.PocketWallet
import io.github.brunogutierre.bitpocket.core.wallet.VaultPocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.io.File

/**
 * The wallet unlocked in this process. It keeps NO seed: only what is needed while unlocked (the
 * open pockets, the network and the biometric-sealed seed). Signing asks for the seed again,
 * through biometrics or the PIN.
 */
data class UnlockedWallet(
    val slot: SlotId,
    val network: SupportedNetwork,
    /** The seed sealed by the biometric Keystore key, or null when biometric signing is off. */
    val biometricSeed: ByteArray?,
    val spending: PocketWallet,
    val settings: WalletSettings = WalletSettings(),
    /** The wallet's directory (see `WalletOpener.dirOf`), e.g. for the Vault backup file. */
    val dir: File? = null,
    /** The open Vault pocket, once the Vault is set up. */
    val vault: VaultPocket? = null,
)

/** Holds the unlocked wallet in memory only; [lock] closes its pockets. */
class Session {
    private val state = MutableStateFlow<UnlockedWallet?>(null)

    /** The unlocked wallet, observable so the UI can leave wallet screens when it locks. */
    val walletState: StateFlow<UnlockedWallet?> = state.asStateFlow()

    val wallet: UnlockedWallet? get() = state.value

    val isUnlocked: Boolean get() = wallet != null

    fun open(wallet: UnlockedWallet) {
        lock()
        state.value = wallet
    }

    /** Biometric signing was turned on or off: same pockets, new sealed seed (or none). */
    fun updateBiometricSeed(blob: ByteArray?) {
        val current = wallet ?: return
        current.biometricSeed?.wipe()
        state.value = current.copy(biometricSeed = blob)
    }

    /** The Vault was just set up or imported: open its pocket alongside Spending. */
    fun updateVault(vault: VaultPocket) {
        val current = wallet ?: return vault.close()
        current.vault?.close()
        state.value = current.copy(vault = vault)
    }

    fun updateSettings(settings: WalletSettings) {
        state.value = wallet?.copy(settings = settings) ?: return
    }

    fun lock() {
        val current = state.getAndUpdate { null } ?: return
        current.biometricSeed?.wipe()
        current.spending.close()
        current.vault?.close()
    }
}
