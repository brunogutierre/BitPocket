package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.UnlockResult
import io.github.brunogutierre.bitpocket.core.crypto.UnlockService
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.BiometricRewrap
import io.github.brunogutierre.bitpocket.core.seed.BiometricSeedVault
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.core.vault.VaultBackup
import io.github.brunogutierre.bitpocket.core.vault.VaultBackupContents
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.vault.VaultKeys
import io.github.brunogutierre.bitpocket.core.vault.VaultRole
import io.github.brunogutierre.bitpocket.core.wallet.WalletOpener
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

sealed interface UnlockOutcome {
    data object Unlocked : UnlockOutcome

    /** [lockout] is non-zero when this failure started a lockout. */
    data class WrongPin(
        val lockout: Duration,
    ) : UnlockOutcome

    data class LockedOut(
        val remaining: Duration,
    ) : UnlockOutcome

    /** The device key is unavailable or the slot is unreadable (not a PIN problem). */
    data object Failed : UnlockOutcome
}

/** The seed for one signing operation. */
sealed interface SeedOutcome {
    /** The caller wipes [entropy] as soon as it has signed. */
    class Seed(
        val entropy: ByteArray,
    ) : SeedOutcome

    data class WrongPin(
        val lockout: Duration,
    ) : SeedOutcome

    data class LockedOut(
        val remaining: Duration,
    ) : SeedOutcome

    data object Failed : SeedOutcome
}

/** Result of a settings change that re-enters the PIN. */
sealed interface SettingsOutcome {
    data object Done : SettingsOutcome

    data class WrongPin(
        val lockout: Duration,
    ) : SettingsOutcome

    data class LockedOut(
        val remaining: Duration,
    ) : SettingsOutcome

    /** Keystore/storage error, or the biometric sealing was cancelled. */
    data object Failed : SettingsOutcome
}

/** Result of setting up or importing the Vault. */
sealed interface VaultSetupOutcome {
    /** [backup] is the encrypted Vault backup to keep and export (ADR 0007). */
    class Done(
        val backup: ByteArray,
    ) : VaultSetupOutcome

    /** Wrong PIN, lockout or storage error. */
    data class Denied(
        val outcome: SettingsOutcome,
    ) : VaultSetupOutcome

    /** This wallet already has a Vault: a second one would hide the first one's coins. */
    data object AlreadySetUp : VaultSetupOutcome

    /** The backup is malformed, for another network, or not for this wallet's seed. */
    data object NotThisWallet : VaultSetupOutcome
}

/** Creates the wallet and unlocks it into the [Session]. Blocking and slow: call off the main thread. */
interface WalletAccess {
    fun create(
        pin: CharArray,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    )

    fun unlock(pin: CharArray): UnlockOutcome

    /**
     * Re-enters the PIN of the open session to get its seed for signing. It goes through the same
     * backoff as unlocking, and a PIN of another slot is just a wrong PIN.
     */
    fun seedForSigning(pin: CharArray): SeedOutcome

    /** Re-encrypts the slot under [newPin] after checking [currentPin]. */
    fun changePin(
        currentPin: CharArray,
        newPin: CharArray,
    ): SettingsOutcome

    /**
     * Turns biometric signing on (a new biometric key seals the seed; [vault] shows the prompt) or
     * off ([vault] null). Either way the slot is saved again under [pin].
     */
    suspend fun setBiometricSigning(
        pin: CharArray,
        vault: BiometricSeedVault?,
    ): SettingsOutcome

    /** Checks the session PIN (e.g. before wiping) without decrypting anything for the caller. */
    fun verifyPin(pin: CharArray): SettingsOutcome

    /**
     * Creates the Vault after checking [pin]: PHONE comes from the wallet seed, RECOVERY and HEIR from
     * the given seed entropies (the caller wipes them). Saves the public description in the slot.
     */
    fun setUpVault(
        pin: CharArray,
        recoveryEntropy: ByteArray,
        heirEntropy: ByteArray,
        timelock: HeirTimelock,
    ): VaultSetupOutcome

    /** Restores the Vault of a restored wallet from its backup, opened with this wallet's seed. */
    fun importVault(
        pin: CharArray,
        backup: ByteArray,
    ): VaultSetupOutcome

    /**
     * Sets up the duress PIN after checking [pin] (ADR 0009): [duressPin] will open a decoy wallet
     * made from [decoyEntropy] (the caller wipes it). [duressPin] must differ from [pin].
     */
    fun setUpDuress(
        pin: CharArray,
        duressPin: CharArray,
        decoyEntropy: ByteArray,
    ): SettingsOutcome

    /** Removes the decoy wallet after checking [pin]; its slot becomes random bytes again. */
    fun removeDuress(pin: CharArray): SettingsOutcome

    /** Saves new security settings in the slot after checking [pin] (ADR 0005). */
    fun updateSettings(
        pin: CharArray,
        settings: WalletSettings,
    ): SettingsOutcome
}

/**
 * [WalletAccess] over the slot store. Slot A holds the real wallet; slot B holds the decoy opened by
 * the duress PIN, or random bytes. Only this class knows which is which: the app never sees a slot.
 */
class SlotWalletAccess(
    private val slots: SlotStore,
    private val unlockService: UnlockService,
    private val session: Session,
    private val opener: WalletOpener,
) : WalletAccess {
    override fun create(
        pin: CharArray,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    ) {
        // Biometric signing is turned on later, in Settings, so no biometric copy yet.
        val payload = SlotPayload(seedEntropy.copyOf(), biometricSeed = null, network = network)
        save(REAL, pin, payload)
        openSession(REAL, payload)
    }

    override fun unlock(pin: CharArray): UnlockOutcome =
        try {
            when (val result = unlockService.unlock(pin)) {
                is UnlockResult.Unlocked -> open(result)
                is UnlockResult.Wrong -> UnlockOutcome.WrongPin(result.lockout)
                is UnlockResult.LockedOut -> UnlockOutcome.LockedOut(result.remaining)
            }
        } catch (e: KeyUnavailableException) {
            UnlockOutcome.Failed
        } catch (e: AuthenticationFailedException) {
            UnlockOutcome.Failed
        }

    private fun open(result: UnlockResult.Unlocked): UnlockOutcome {
        val payload =
            try {
                SlotPayload.decode(result.payload)
            } catch (e: IllegalArgumentException) {
                return UnlockOutcome.Failed
            } finally {
                result.payload.wipe()
            }
        return try {
            openSession(result.slot, payload)
            UnlockOutcome.Unlocked
        } catch (e: Exception) {
            // The wallet database could not be opened (I/O error): not a PIN problem.
            UnlockOutcome.Failed
        }
    }

    override fun seedForSigning(pin: CharArray): SeedOutcome =
        when (val result = reauthenticate(pin)) {
            is Reauth.Ok -> {
                result.payload.biometricSeed?.wipe()
                SeedOutcome.Seed(result.payload.seedEntropy)
            }

            is Reauth.Denied -> {
                when (val outcome = result.outcome) {
                    is SettingsOutcome.WrongPin -> SeedOutcome.WrongPin(outcome.lockout)
                    is SettingsOutcome.LockedOut -> SeedOutcome.LockedOut(outcome.remaining)
                    else -> SeedOutcome.Failed
                }
            }
        }

    override fun changePin(
        currentPin: CharArray,
        newPin: CharArray,
    ): SettingsOutcome =
        withPayload(currentPin) { slot, payload ->
            save(slot, newPin, payload)
            SettingsOutcome.Done
        }

    override suspend fun setBiometricSigning(
        pin: CharArray,
        vault: BiometricSeedVault?,
    ): SettingsOutcome =
        when (val result = reauthenticate(pin)) {
            is Reauth.Denied -> {
                result.outcome
            }

            is Reauth.Ok -> {
                try {
                    val saved =
                        if (vault == null) {
                            result.payload.withBiometricSeed(null).also { save(result.slot, pin, it) }
                        } else {
                            BiometricRewrap(slots, vault).rewrap(result.slot, pin, result.payload)
                        }
                    session.updateBiometricSeed(saved.biometricSeed?.copyOf())
                    SettingsOutcome.Done
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sealing failed or was cancelled: the slot was saved without a biometric copy.
                    session.updateBiometricSeed(null)
                    SettingsOutcome.Failed
                } finally {
                    result.payload.seedEntropy.wipe()
                }
            }
        }

    override fun verifyPin(pin: CharArray): SettingsOutcome = withPayload(pin) { _, _ -> SettingsOutcome.Done }

    override fun setUpDuress(
        pin: CharArray,
        duressPin: CharArray,
        decoyEntropy: ByteArray,
    ): SettingsOutcome {
        if (pin.contentEquals(duressPin)) return SettingsOutcome.Failed
        return withPayload(pin) { slot, payload ->
            if (slot == REAL) {
                // The decoy gets its own wallet in slot B; the real wallet only records the flag.
                save(DECOY, duressPin, SlotPayload(decoyEntropy.copyOf(), null, payload.network))
                saveSettings(slot, pin, payload, payload.settings.copy(duress = true))
            } else {
                // Inside the decoy: look the same, but never touch the real wallet (no PIN oracle).
                saveSettings(slot, pin, payload, payload.settings.copy(duress = true))
            }
            SettingsOutcome.Done
        }
    }

    override fun removeDuress(pin: CharArray): SettingsOutcome =
        withPayload(pin) { slot, payload ->
            if (slot == REAL) slots.reset(DECOY)
            saveSettings(slot, pin, payload, payload.settings.copy(duress = false))
            SettingsOutcome.Done
        }

    private fun saveSettings(
        slot: SlotId,
        pin: CharArray,
        payload: SlotPayload,
        settings: WalletSettings,
    ) {
        save(slot, pin, payload.withSettings(settings))
        session.updateSettings(settings)
    }

    override fun updateSettings(
        pin: CharArray,
        settings: WalletSettings,
    ): SettingsOutcome =
        withPayload(pin) { slot, payload ->
            save(slot, pin, payload.withSettings(settings))
            session.updateSettings(settings)
            SettingsOutcome.Done
        }

    override fun setUpVault(
        pin: CharArray,
        recoveryEntropy: ByteArray,
        heirEntropy: ByteArray,
        timelock: HeirTimelock,
    ): VaultSetupOutcome =
        withVaultPayload(pin) { slot, payload ->
            if (payload.settings.vault != null) return@withVaultPayload VaultSetupOutcome.AlreadySetUp
            val network = payload.network
            val config =
                VaultConfig(
                    phoneKey = VaultKeys.publicKey(payload.seedEntropy, network),
                    recoveryKey = VaultKeys.publicKey(recoveryEntropy, network),
                    heirKey = VaultKeys.publicKey(heirEntropy, network),
                    timelock = timelock,
                )
            val seeds = mapOf(VaultRole.PHONE to payload.seedEntropy, VaultRole.RECOVERY to recoveryEntropy, VaultRole.HEIR to heirEntropy)
            val backup = VaultBackup.seal(VaultBackupContents(network, config), seeds)
            saveVault(slot, pin, payload, config)
            VaultSetupOutcome.Done(backup)
        }

    override fun importVault(
        pin: CharArray,
        backup: ByteArray,
    ): VaultSetupOutcome =
        withVaultPayload(pin) { slot, payload ->
            if (payload.settings.vault != null) return@withVaultPayload VaultSetupOutcome.AlreadySetUp
            val contents =
                try {
                    VaultBackup.open(backup, payload.seedEntropy)
                } catch (e: IllegalArgumentException) {
                    null
                }
            // Opened with this seed AND this seed is the Vault's PHONE key, on the same network.
            val ours =
                contents?.takeIf {
                    it.network == payload.network &&
                        it.config.phoneKey == VaultKeys.publicKey(payload.seedEntropy, it.network)
                }
            if (ours == null) return@withVaultPayload VaultSetupOutcome.NotThisWallet
            saveVault(slot, pin, payload, ours.config)
            VaultSetupOutcome.Done(backup)
        }

    private fun saveVault(
        slot: SlotId,
        pin: CharArray,
        payload: SlotPayload,
        config: VaultConfig,
    ) {
        val settings = payload.settings.copy(vault = config)
        save(slot, pin, payload.withSettings(settings))
        session.updateSettings(settings)
        session.updateVault(opener.openVault(payload, config))
    }

    private fun withVaultPayload(
        pin: CharArray,
        block: (SlotId, SlotPayload) -> VaultSetupOutcome,
    ): VaultSetupOutcome =
        when (val result = reauthenticate(pin)) {
            is Reauth.Denied -> {
                VaultSetupOutcome.Denied(result.outcome)
            }

            is Reauth.Ok -> {
                try {
                    block(result.slot, result.payload)
                } catch (e: KeyUnavailableException) {
                    VaultSetupOutcome.Denied(SettingsOutcome.Failed)
                } finally {
                    result.payload.seedEntropy.wipe()
                }
            }
        }

    private companion object {
        val REAL = SlotId.A
        val DECOY = SlotId.B
    }

    private sealed interface Reauth {
        class Ok(
            val slot: SlotId,
            val payload: SlotPayload,
        ) : Reauth

        class Denied(
            val outcome: SettingsOutcome,
        ) : Reauth
    }

    /** The session PIN, checked like an unlock (same backoff); another slot's PIN is wrong here. */
    private fun reauthenticate(pin: CharArray): Reauth {
        val slot = session.wallet?.slot ?: return Reauth.Denied(SettingsOutcome.Failed)
        val result =
            try {
                unlockService.unlock(pin, expected = slot)
            } catch (e: KeyUnavailableException) {
                return Reauth.Denied(SettingsOutcome.Failed)
            } catch (e: AuthenticationFailedException) {
                return Reauth.Denied(SettingsOutcome.Failed)
            }
        return when (result) {
            is UnlockResult.Wrong -> {
                Reauth.Denied(SettingsOutcome.WrongPin(result.lockout))
            }

            is UnlockResult.LockedOut -> {
                Reauth.Denied(SettingsOutcome.LockedOut(result.remaining))
            }

            is UnlockResult.Unlocked -> {
                try {
                    Reauth.Ok(result.slot, SlotPayload.decode(result.payload))
                } catch (e: IllegalArgumentException) {
                    Reauth.Denied(SettingsOutcome.Failed)
                } finally {
                    result.payload.wipe()
                }
            }
        }
    }

    /** Runs [block] with the re-authenticated payload, then wipes its seed. */
    private fun withPayload(
        pin: CharArray,
        block: (SlotId, SlotPayload) -> SettingsOutcome,
    ): SettingsOutcome =
        when (val result = reauthenticate(pin)) {
            is Reauth.Denied -> {
                result.outcome
            }

            is Reauth.Ok -> {
                try {
                    block(result.slot, result.payload)
                } catch (e: KeyUnavailableException) {
                    SettingsOutcome.Failed
                } finally {
                    result.payload.seedEntropy.wipe()
                }
            }
        }

    private fun save(
        slot: SlotId,
        pin: CharArray,
        payload: SlotPayload,
    ) {
        val encoded = payload.encode()
        try {
            slots.save(slot, pin, encoded)
        } finally {
            encoded.wipe()
        }
    }

    /** Opens the pockets, then drops the seed: the session keeps public data only. */
    private fun openSession(
        slot: SlotId,
        payload: SlotPayload,
    ) {
        try {
            val spending = opener.open(payload)
            val vault =
                try {
                    payload.settings.vault?.let { opener.openVault(payload, it) }
                } catch (e: Exception) {
                    spending.close()
                    throw e
                }
            session.open(
                UnlockedWallet(slot, payload.network, payload.biometricSeed, spending, payload.settings, opener.dirOf(payload), vault),
            )
        } finally {
            payload.seedEntropy.wipe()
        }
    }
}
