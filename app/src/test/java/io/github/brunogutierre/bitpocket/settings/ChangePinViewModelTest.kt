package io.github.brunogutierre.bitpocket.settings

import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class ChangePinViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val access = FakeWalletAccess()
    private val vm by lazy { ChangePinViewModel(access, main.dispatcher) }

    private fun type(pin: String) = pin.forEach(vm::digit)

    @Test
    fun `checks the current PIN, then saves a confirmed new one`() {
        access.settingsOutcomes += SettingsOutcome.Done
        access.settingsOutcomes += SettingsOutcome.Done

        type("123456")
        assertEquals(ChangePinStage.NEW, vm.state.value.stage)
        type("258147")
        assertEquals(ChangePinStage.CONFIRM, vm.state.value.stage)
        type("258147")

        assertEquals(listOf("verify 123456", "changePin 123456 258147"), access.settingsCalls)
        assertEquals(ChangePinStage.DONE, vm.state.value.stage)
    }

    @Test
    fun `rejects a wrong current PIN, a weak new PIN and a mismatch`() {
        access.settingsOutcomes += SettingsOutcome.WrongPin(Duration.ZERO)
        access.settingsOutcomes += SettingsOutcome.Done

        type("000000")
        assertTrue(vm.state.value.wrongPin)
        assertEquals(ChangePinStage.CURRENT, vm.state.value.stage)

        type("123456")
        type("111111")
        assertTrue(vm.state.value.weakPin)
        assertEquals(ChangePinStage.NEW, vm.state.value.stage)

        type("258147")
        type("258148")
        assertTrue(vm.state.value.mismatch)
        assertEquals(ChangePinStage.NEW, vm.state.value.stage)
        assertEquals(3, vm.state.value.errorSignal, "wrong PIN, weak PIN and mismatch each shake")
    }
}
