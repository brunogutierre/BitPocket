package io.github.brunogutierre.bitpocket

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.BiometricSeedVault
import io.github.brunogutierre.bitpocket.core.session.SeedOutcome
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.UnlockOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings

class FakeWalletAccess : WalletAccess {
    var createdPin: String? = null
    var createdNetwork: SupportedNetwork? = null
    var failCreate = false
    val outcomes = ArrayDeque<UnlockOutcome>()
    val attempts = mutableListOf<String>()

    override fun create(
        pin: CharArray,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    ) {
        check(!failCreate) { "keystore unavailable" }
        createdPin = String(pin)
        createdNetwork = network
    }

    override fun unlock(pin: CharArray): UnlockOutcome {
        attempts += String(pin)
        return outcomes.removeFirst()
    }

    /** Answers for [seedForSigning], in order. */
    val seedOutcomes = ArrayDeque<SeedOutcome>()
    val signingPins = mutableListOf<String>()

    override fun seedForSigning(pin: CharArray): SeedOutcome {
        signingPins += String(pin)
        return seedOutcomes.removeFirst()
    }

    /** Answers for [changePin], [setBiometricSigning] and [verifyPin], in order. */
    val settingsOutcomes = ArrayDeque<SettingsOutcome>()
    val settingsCalls = mutableListOf<String>()

    override fun changePin(
        currentPin: CharArray,
        newPin: CharArray,
    ): SettingsOutcome {
        settingsCalls += "changePin ${String(currentPin)} ${String(newPin)}"
        return settingsOutcomes.removeFirst()
    }

    override suspend fun setBiometricSigning(
        pin: CharArray,
        vault: BiometricSeedVault?,
    ): SettingsOutcome {
        settingsCalls += "biometric ${String(pin)} ${vault != null}"
        vault?.seal(byteArrayOf(1))
        return settingsOutcomes.removeFirst()
    }

    override fun verifyPin(pin: CharArray): SettingsOutcome {
        settingsCalls += "verify ${String(pin)}"
        return settingsOutcomes.removeFirst()
    }

    override fun updateSettings(
        pin: CharArray,
        settings: WalletSettings,
    ): SettingsOutcome {
        settingsCalls += "settings ${String(pin)} ${settings.autoLock}"
        return settingsOutcomes.removeFirst()
    }
}
