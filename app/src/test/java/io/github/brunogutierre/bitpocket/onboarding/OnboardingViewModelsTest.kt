package io.github.brunogutierre.bitpocket.onboarding

import app.cash.turbine.test
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.onboarding.OnboardingDraft
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.ui.components.PickResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.random.Random

class OnboardingViewModelsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val draft = OnboardingDraft()
    private val mnemonics = MnemonicService()
    private val legal = "legal winner thank year wave sausage worth useful legal winner thank yellow".split(" ")

    @Test
    fun `welcome creates a mnemonic on the chosen network`() =
        runTest {
            val vm = WelcomeViewModel(draft, mnemonics)
            vm.onAction(WelcomeAction.ToggleAdvanced)
            vm.onAction(WelcomeAction.SelectNetwork(SupportedNetwork.TESTNET4))

            vm.events.test {
                vm.onAction(WelcomeAction.Create)
                assertEquals(WelcomeEvent.SHOW_NEW_SEED, awaitItem())
            }

            assertTrue(vm.state.value.advancedExpanded)
            assertEquals(SupportedNetwork.TESTNET4, draft.network)
            assertEquals(12, draft.words.size)
            assertNotNull(draft.entropy)
        }

    @Test
    fun `welcome restore starts from an empty draft on the default network`() =
        runTest {
            draft.setMnemonic(ByteArray(16), legal)
            val vm = WelcomeViewModel(draft, mnemonics)

            vm.events.test {
                vm.onAction(WelcomeAction.Restore)
                assertEquals(WelcomeEvent.RESTORE, awaitItem())
            }

            assertEquals(SupportedNetwork.SIGNET, draft.network)
            assertNull(draft.entropy)
        }

    @Test
    fun `create screen allows continuing only after revealing`() {
        draft.setMnemonic(ByteArray(16), legal)
        val vm = CreateSeedViewModel(draft)

        assertFalse(vm.state.value.canContinue)
        vm.reveal()

        assertTrue(vm.state.value.canContinue)
        assertEquals(legal, vm.state.value.words)
    }

    @Test
    fun `verification retries wrong picks and finishes after three correct ones`() =
        runTest {
            draft.setMnemonic(ByteArray(16), legal)
            val vm = VerifySeedViewModel(draft, mnemonics, Random(1))

            vm.verified.test {
                repeat(3) { step ->
                    val challenge = vm.state.value.challenge
                    assertEquals(step + 1, vm.state.value.step)
                    vm.pick(challenge.options.first { it != challenge.answer })
                    assertEquals(PickResult.WRONG, vm.state.value.result)
                    assertEquals(1, vm.state.value.errorSignal)
                    vm.pick(challenge.answer)
                    assertEquals(PickResult.CORRECT, vm.state.value.result)
                    testScheduler.advanceUntilIdle()
                }
                awaitItem()
            }
        }

    @Test
    fun `restore accepts only wordlist words and reports typed errors`() =
        runTest {
            val vm = RestoreSeedViewModel(draft, mnemonics)

            vm.onAction(RestoreAction.InputChanged("LEG"))
            assertEquals(listOf("leg", "legal", "legend"), vm.state.value.suggestions)
            vm.onAction(RestoreAction.AcceptWord("legx"))
            assertEquals(emptyList<String>(), vm.state.value.words)
            (legal.dropLast(1) + "zoo").forEach { vm.onAction(RestoreAction.InputChanged("$it ")) }
            assertEquals("", vm.state.value.input)
            assertTrue(vm.state.value.complete)
            vm.onAction(RestoreAction.Submit)
            assertEquals(RestoreError.InvalidChecksum, vm.state.value.error)

            vm.onAction(RestoreAction.RemoveLast)
            vm.onAction(RestoreAction.AcceptWord("yellow"))
            vm.restored.test {
                vm.onAction(RestoreAction.Submit)
                awaitItem()
            }
            assertEquals(legal, draft.words)
            vm.onAction(RestoreAction.SetWordCount(24))
            assertFalse(vm.state.value.complete)
        }

    @Test
    fun `restore accepts a pasted mnemonic and keeps an unknown word in the input`() {
        val vm = RestoreSeedViewModel(draft, mnemonics)

        vm.onAction(RestoreAction.InputChanged("legal Winner thank bitcoinz wave"))

        assertEquals(listOf("legal", "winner", "thank"), vm.state.value.words)
        assertEquals("bitcoinz wave", vm.state.value.input)
    }
}
