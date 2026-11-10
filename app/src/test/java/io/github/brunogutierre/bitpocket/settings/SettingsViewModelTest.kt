package io.github.brunogutierre.bitpocket.settings

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.BiometricSeedVault
import io.github.brunogutierre.bitpocket.core.session.SeedOutcome
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.UnlockedWallet
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val access = FakeWalletAccess()
    private val session = Session().apply { open(UnlockedWallet(SlotId.A, SupportedNetwork.TESTNET4, null, FakePocketWallet())) }
    private var wiped = false

    private fun vm(biometricAvailable: Boolean = true) =
        SettingsViewModel(access, session, biometricAvailable, {
            wiped = true
        }, main.dispatcher)

    private fun SettingsViewModel.typePin(pin: String = "123456") = pin.forEach(::pinDigit)

    @Test
    fun `shows the recovery words after the PIN and wipes the seed`() {
        val seed = ByteArray(16)
        access.seedOutcomes += SeedOutcome.Seed(seed)
        val vm = vm()

        vm.showRecoveryPhrase()
        assertEquals(PinAction.SHOW_PHRASE, vm.state.value.action)
        vm.typePin()

        assertEquals(List(11) { "abandon" } + "about", vm.state.value.phrase)
        assertNull(vm.state.value.action)
        assertArrayEquals(ByteArray(16), seed)
        vm.closePhrase()
        assertNull(vm.state.value.phrase)
        assertEquals(SupportedNetwork.TESTNET4, vm.network)
    }

    @Test
    fun `turning biometric signing on waits for the screen to hand over a vault`() {
        access.settingsOutcomes += SettingsOutcome.Done
        val vm = vm()
        assertFalse(vm.state.value.biometricOn)

        vm.setBiometricSigning(true)
        vm.typePin()
        assertTrue(vm.state.value.awaitingSeal)
        assertTrue(access.settingsCalls.isEmpty(), "nothing runs before the prompt can be shown")

        val vault = RecordingVault()
        vm.seal(vault)

        assertEquals(listOf("biometric 123456 true"), access.settingsCalls)
        assertTrue(vault.sealed)
        assertTrue(vm.state.value.biometricOn)
        assertFalse(vm.state.value.awaitingSeal)
        assertNull(vm.state.value.action)
    }

    @Test
    fun `a cancelled prompt reports that biometric signing stayed off`() {
        access.settingsOutcomes += SettingsOutcome.Failed
        val vm = vm()
        vm.setBiometricSigning(true)
        vm.typePin()

        vm.seal(RecordingVault())

        assertFalse(vm.state.value.biometricOn)
        assertTrue(vm.state.value.biometricSetupFailed)
    }

    @Test
    fun `turning it off and wrong PINs`() {
        session.updateBiometricSeed(byteArrayOf(1))
        access.settingsOutcomes += SettingsOutcome.WrongPin(Duration.ZERO)
        access.settingsOutcomes += SettingsOutcome.Done
        val vm = vm()
        assertTrue(vm.state.value.biometricOn)

        vm.setBiometricSigning(false)
        vm.typePin("000000")
        assertTrue(vm.state.value.wrongPin)
        assertEquals(PinAction.DISABLE_BIOMETRICS, vm.state.value.action)
        vm.typePin()

        assertFalse(vm.state.value.biometricOn)
        assertEquals(listOf("biometric 000000 false", "biometric 123456 false"), access.settingsCalls)
    }

    @Test
    fun `erasing asks for confirmation, then the PIN`() {
        access.settingsOutcomes += SettingsOutcome.Done
        val vm = vm()

        vm.requestWipe()
        assertTrue(vm.state.value.confirmingWipe)
        vm.dismissWipe()
        assertNull(vm.state.value.action)
        vm.requestWipe()
        vm.confirmWipe()
        vm.typePin()

        assertEquals(listOf("verify 123456"), access.settingsCalls)
        assertTrue(wiped)
        assertTrue(vm.state.value.wiped)
    }

    @Test
    fun `a lockout disables the keys and cancelling leaves the PIN step`() {
        access.seedOutcomes += SeedOutcome.LockedOut(Duration.ofSeconds(30))
        val vm = vm()
        vm.showRecoveryPhrase()
        vm.typePin()

        assertEquals(30L, vm.state.value.lockoutSeconds)
        assertFalse(vm.state.value.pinKeysEnabled)
        vm.cancelPin()
        assertNull(vm.state.value.action)
    }

    private class RecordingVault : BiometricSeedVault {
        var sealed = false

        override fun resetKey() = Unit

        override suspend fun seal(seed: ByteArray): ByteArray {
            sealed = true
            return seed
        }
    }
}
