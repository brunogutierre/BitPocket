package io.github.brunogutierre.bitpocket.settings

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.UnlockedWallet
import io.github.brunogutierre.bitpocket.core.settings.Guardrails
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class GuardrailsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val access = FakeWalletAccess()
    private val pocket = FakePocketWallet()
    private val strict = Guardrails(perTxLimitSats = 10_000, windowLimitSats = 50_000)
    private val session =
        Session().apply { open(UnlockedWallet(SlotId.A, SupportedNetwork.SIGNET, null, pocket, WalletSettings(guardrails = strict))) }

    private fun vm() = GuardrailsViewModel(access, session, pocket, main.dispatcher)

    private fun GuardrailsViewModel.typePin() = "123456".forEach(::pinDigit)

    @Test
    fun `shows the guardrails in force`() {
        val state = vm().state.value

        assertEquals("10000", state.perTxText)
        assertEquals("50000", state.windowText)
        assertTrue(state.confirmNewRecipients)
    }

    @Test
    fun `tightening saves at once after the PIN`() {
        access.settingsOutcomes += SettingsOutcome.Done
        val vm = vm()
        vm.onPerTxChange("5 000")

        vm.save()
        assertEquals(GuardrailsStage.PIN, vm.state.value.stage)
        vm.typePin()

        assertEquals(GuardrailsStage.SAVED, vm.state.value.stage)
        assertNull(vm.state.value.scheduledAt)
        assertTrue(access.settingsCalls.single().startsWith("settings 123456"))
    }

    @Test
    fun `loosening is scheduled 144 blocks after the synced tip`() {
        access.settingsOutcomes += SettingsOutcome.Done
        val vm = vm()
        vm.onPerTxChange("")

        vm.save()
        vm.typePin()

        assertEquals(1_144, vm.state.value.scheduledAt)
    }

    @Test
    fun `loosening without a synced tip asks to sync, and bad numbers are flagged`() {
        pocket.stats = pocket.stats.copy(tipHeight = 0)
        val vm = vm()
        vm.onWindowChange("")
        vm.save()
        assertTrue(vm.state.value.needsSync)

        vm.onPerTxChange("12.5")
        vm.save()
        assertTrue(vm.state.value.invalidPerTx)
        assertEquals(GuardrailsStage.EDIT, vm.state.value.stage)
    }
}
