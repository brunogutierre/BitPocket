package io.github.brunogutierre.bitpocket.health

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.health.HealthCheck
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.UnlockedWallet
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HealthViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    @Test
    fun `scores the open wallet and follows changes on reload`() {
        val session = Session()
        session.open(UnlockedWallet(SlotId.A, SupportedNetwork.SIGNET, byteArrayOf(1), FakePocketWallet(), WalletSettings()))
        val vm = HealthViewModel(session)
        assertEquals(setOf(HealthCheck.BIOMETRIC_SIGNING, HealthCheck.QUICK_AUTO_LOCK), vm.report.value!!.passed)

        session.updateSettings(WalletSettings(duress = true))
        vm.reload()
        assertEquals(45, vm.report.value!!.score)

        session.lock()
        vm.reload()
        assertNull(vm.report.value)
    }
}
