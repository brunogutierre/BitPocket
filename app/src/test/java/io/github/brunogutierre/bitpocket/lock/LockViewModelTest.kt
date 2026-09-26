package io.github.brunogutierre.bitpocket.lock

import app.cash.turbine.test
import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.session.Session
import io.github.brunogutierre.bitpocket.core.session.UnlockOutcome
import io.github.brunogutierre.bitpocket.core.session.UnlockedWallet
import io.github.brunogutierre.bitpocket.home.HomeViewModel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class LockViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val wallet = FakeWalletAccess()
    private val vm by lazy { LockViewModel(wallet, main.dispatcher) }

    private fun type(pin: String) = pin.forEach(vm::digit)

    @Test
    fun `a correct PIN unlocks`() =
        runTest(main.dispatcher) {
            wallet.outcomes += UnlockOutcome.Unlocked

            vm.unlocked.test {
                type("12345")
                vm.backspace()
                type("56")
                awaitItem()
            }

            assertEquals(listOf("123456"), wallet.attempts)
        }

    @Test
    fun `a wrong PIN shakes, then a lockout disables the keys until it expires`() =
        runTest(main.dispatcher) {
            wallet.outcomes += UnlockOutcome.WrongPin(Duration.ZERO)
            wallet.outcomes += UnlockOutcome.WrongPin(Duration.ofSeconds(30))

            type("000000")
            testScheduler.runCurrent()
            assertTrue(vm.state.value.wrongPin)
            assertEquals(1, vm.state.value.errorSignal)
            assertTrue(vm.state.value.keysEnabled)

            type("111111")
            testScheduler.runCurrent()
            assertEquals(30, vm.state.value.lockoutSeconds)
            assertFalse(vm.state.value.keysEnabled)
            vm.digit('1')
            assertEquals(0, vm.state.value.entered, "keys are ignored while locked out")

            testScheduler.advanceTimeBy(30_001)
            assertEquals(0, vm.state.value.lockoutSeconds)
            assertTrue(vm.state.value.keysEnabled)
        }

    @Test
    fun `lockout and failures reported by the service are shown`() =
        runTest(main.dispatcher) {
            wallet.outcomes += UnlockOutcome.LockedOut(Duration.ofMillis(1500))
            wallet.outcomes += UnlockOutcome.Failed

            type("123456")
            testScheduler.runCurrent()
            assertEquals(2, vm.state.value.lockoutSeconds)
            testScheduler.advanceTimeBy(2_001)

            type("123456")
            testScheduler.runCurrent()
            assertTrue(vm.state.value.failed)
        }

    @Test
    fun `home shows the session network and locks it`() {
        val session = Session()
        session.open(UnlockedWallet(SlotId.A, SlotPayload(ByteArray(16), null, SupportedNetwork.TESTNET4)))
        val home = HomeViewModel(session)

        assertEquals(SupportedNetwork.TESTNET4, home.network)
        home.lock()

        assertFalse(session.isUnlocked)
        assertEquals(SupportedNetwork.SIGNET, HomeViewModel(session).network)
    }
}
