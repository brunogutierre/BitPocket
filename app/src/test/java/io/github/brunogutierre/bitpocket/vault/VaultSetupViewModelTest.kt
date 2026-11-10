package io.github.brunogutierre.bitpocket.vault

import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.seed.MnemonicService
import io.github.brunogutierre.bitpocket.core.session.SettingsOutcome
import io.github.brunogutierre.bitpocket.core.session.VaultSetupOutcome
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.ui.components.PickResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import kotlin.random.Random

class VaultSetupViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val access = FakeWalletAccess()
    private val kept = mutableListOf<ByteArray>()
    private val vm by lazy { VaultSetupViewModel(access, MnemonicService(), { kept += it }, Random(7), main.dispatcher) }

    /** Answers every challenge of the current word list. */
    private fun passVerification() {
        repeat(vm.state.value.challengeCount) {
            vm.pick(
                vm.state.value.challenge!!
                    .answer,
            )
        }
    }

    private fun typePin(pin: String = "123456") = pin.forEach(vm::pinDigit)

    @Test
    fun `walks through timelock, both word lists and the PIN, then offers the backup`() {
        access.vaultOutcomes += VaultSetupOutcome.Done(byteArrayOf(9, 9))

        vm.next()
        vm.chooseTimelock(HeirTimelock.HOUR)
        vm.next()
        assertEquals(VaultSetupStage.WORDS, vm.state.value.stage)
        assertEquals(VaultKeyStep.RECOVERY, vm.state.value.keyStep)
        val recoveryWords = vm.state.value.words
        assertEquals(12, recoveryWords.size)

        vm.next()
        passVerification()
        assertEquals(VaultKeyStep.HEIR, vm.state.value.keyStep)
        assertNotEquals(recoveryWords, vm.state.value.words)

        vm.next()
        passVerification()
        assertEquals(VaultSetupStage.PIN, vm.state.value.stage)
        assertTrue(
            vm.state.value.words
                .isEmpty(),
            "words leave the screen state before the PIN",
        )
        typePin()

        assertEquals(VaultSetupStage.BACKUP, vm.state.value.stage)
        assertTrue(access.vaultCalls.single().startsWith("setUp 123456"))
        assertTrue(access.vaultCalls.single().endsWith("HOUR"))
        assertArrayEquals(byteArrayOf(9, 9), vm.backup)
        assertArrayEquals(byteArrayOf(9, 9), kept.single())
    }

    @Test
    fun `a wrong word shakes and a wrong PIN asks again`() {
        access.vaultOutcomes += VaultSetupOutcome.Denied(SettingsOutcome.WrongPin(Duration.ZERO))
        vm.next()
        vm.next()
        vm.next()

        val challenge = vm.state.value.challenge!!
        vm.pick(challenge.options.first { it != challenge.answer })
        assertEquals(PickResult.WRONG, vm.state.value.pickResult)
        assertEquals(1, vm.state.value.pickErrorSignal)

        passVerification()
        vm.next()
        passVerification()
        typePin("000000")

        assertEquals(VaultSetupStage.PIN, vm.state.value.stage)
        assertTrue(vm.state.value.wrongPin)
    }

    @Test
    fun `importing goes straight to the PIN and reports a foreign backup`() {
        access.vaultOutcomes += VaultSetupOutcome.NotThisWallet

        vm.startImport(byteArrayOf(1, 2, 3))
        assertEquals(VaultSetupStage.PIN, vm.state.value.stage)
        typePin()

        assertEquals(listOf("import 123456 3"), access.vaultCalls)
        assertEquals(VaultSetupStage.FAILED, vm.state.value.stage)
        assertEquals(VaultSetupError.NOT_THIS_WALLET, vm.state.value.error)
    }
}
