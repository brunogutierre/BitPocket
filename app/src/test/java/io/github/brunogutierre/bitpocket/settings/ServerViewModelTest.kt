package io.github.brunogutierre.bitpocket.settings

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.UnlockedWallet
import io.github.brunogutierre.bitpocket.core.wallet.ServerCheck
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ServerViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val access = FakeWalletAccess()
    private val session = Session().apply { open(UnlockedWallet(SlotId.A, SupportedNetwork.SIGNET, null, FakePocketWallet())) }
    private var answer = ServerCheck.OK
    private val checked = mutableListOf<String>()
    private val vm by lazy {
        ServerViewModel(access, session, { url, _ ->
            checked += url
            answer
        }, main.dispatcher, main.dispatcher)
    }

    @Test
    fun `a checked server is saved after the PIN, normalized`() {
        access.settingsOutcomes += SettingsOutcome.Done
        vm.onUrlChange(" https://example.org/api/ ")

        vm.save()
        assertEquals(ServerStage.PIN, vm.state.value.stage)
        "123456".forEach(vm::pinDigit)

        assertEquals(ServerStage.SAVED, vm.state.value.stage)
        assertEquals(listOf(" https://example.org/api/ "), checked)
        assertTrue(access.settingsCalls.single().startsWith("settings 123456"))
    }

    @Test
    fun `a rejected server stays on the form, and the default needs no check`() {
        answer = ServerCheck.WRONG_NETWORK
        vm.onUrlChange("https://mainnet.example/api")
        vm.save()
        assertEquals(ServerCheck.WRONG_NETWORK, vm.state.value.check)
        assertEquals(ServerStage.EDIT, vm.state.value.stage)

        vm.useDefault()
        assertEquals(ServerStage.PIN, vm.state.value.stage)
        assertEquals(1, checked.size)
    }
}
