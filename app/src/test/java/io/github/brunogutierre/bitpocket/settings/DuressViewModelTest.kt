package io.github.brunogutierre.bitpocket.settings

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.UnlockedWallet
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class DuressViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val access = FakeWalletAccess()

    private fun vm(duress: Boolean = false): DuressViewModel {
        val session =
            Session().apply {
                open(UnlockedWallet(SlotId.A, SupportedNetwork.SIGNET, null, FakePocketWallet(), WalletSettings(duress = duress)))
            }
        return DuressViewModel(access, session, workDispatcher = main.dispatcher)
    }

    private fun DuressViewModel.type(pin: String) = pin.forEach(::digit)

    @Test
    fun `PIN, then a different non-trivial duress PIN twice, creates the decoy`() {
        access.settingsOutcomes += SettingsOutcome.Done
        val vm = vm()

        vm.start()
        vm.type("123456")
        assertEquals(DuressStage.CHOOSE, vm.state.value.stage)
        vm.type("123456")
        assertTrue(vm.state.value.samePin)
        vm.type("111111")
        assertTrue(vm.state.value.weakPin)
        vm.type("258147")
        vm.type("258148")
        assertTrue(vm.state.value.mismatch)
        vm.type("258147")
        vm.type("258147")

        assertEquals(DuressStage.DONE, vm.state.value.stage)
        assertTrue(vm.state.value.enabled)
        assertTrue(access.settingsCalls.single().startsWith("duress 123456 258147"))
    }

    @Test
    fun `a wrong PIN goes back to the PIN, and removal needs only the PIN`() {
        access.settingsOutcomes += SettingsOutcome.WrongPin(Duration.ZERO)
        access.settingsOutcomes += SettingsOutcome.Done
        val vm = vm(duress = true)
        assertTrue(vm.state.value.enabled)

        vm.startRemoval()
        vm.type("000000")
        assertTrue(vm.state.value.wrongPin)
        assertEquals(DuressStage.PIN, vm.state.value.stage)
        vm.type("123456")

        assertEquals(listOf("removeDuress 000000", "removeDuress 123456"), access.settingsCalls)
        assertEquals(DuressStage.DONE, vm.state.value.stage)
        assertFalse(vm.state.value.enabled)
    }
}
