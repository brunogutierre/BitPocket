package io.github.brunogutierre.bitpocket.onboarding

import app.cash.turbine.test
import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SetPinViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val draft = OnboardingDraft().apply { network = SupportedNetwork.TESTNET4 }
    private val wallet = FakeWalletAccess()
    private val vm by lazy { SetPinViewModel(draft, wallet, main.dispatcher) }

    private fun type(pin: String) = pin.forEach(vm::digit)

    @Test
    fun `choosing and confirming the same PIN creates the wallet and clears the draft`() =
        runTest {
            draft.setMnemonic(ByteArray(16), List(12) { "abandon" })

            vm.created.test {
                type("27490")
                vm.backspace()
                type("5")
                assertEquals(PinStage.CHOOSE, vm.state.value.stage)
                type("8")
                assertEquals(PinStage.CONFIRM, vm.state.value.stage)
                type("274958")
                awaitItem()
            }

            assertEquals("274958", wallet.createdPin)
            assertEquals(SupportedNetwork.TESTNET4, wallet.createdNetwork)
            assertNull(draft.entropy)
        }

    @Test
    fun `a mismatch restarts from the first PIN with an error shake`() {
        draft.setMnemonic(ByteArray(16), List(12) { "abandon" })

        type("274958")
        type("274950")

        val state = vm.state.value
        assertEquals(PinStage.CHOOSE, state.stage)
        assertTrue(state.mismatch)
        assertEquals(1, state.errorSignal)
        assertEquals(0, state.entered)
        assertNull(wallet.createdPin)
    }

    @Test
    fun `a weak PIN is rejected before confirmation`() {
        draft.setMnemonic(ByteArray(16), List(12) { "abandon" })

        type("123123")

        val state = vm.state.value
        assertEquals(PinStage.CHOOSE, state.stage)
        assertTrue(state.weakPin)
        assertEquals(1, state.errorSignal)
        vm.digit('2')
        assertFalse(vm.state.value.weakPin, "the message clears on the next digit")
    }

    @Test
    fun `a failed save lets the user try again and keeps the draft`() {
        draft.setMnemonic(ByteArray(16), List(12) { "abandon" })
        wallet.failCreate = true

        type("274958")
        type("274958")

        assertTrue(vm.state.value.saveFailed)
        assertFalse(vm.state.value.saving)
        assertEquals(PinStage.CHOOSE, vm.state.value.stage)
        assertEquals(16, draft.entropy?.size)
    }
}
