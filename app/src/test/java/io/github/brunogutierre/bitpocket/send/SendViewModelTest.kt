package io.github.brunogutierre.bitpocket.send

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.FakeWalletAccess
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.biometric.BiometricFailure
import io.github.brunogutierre.bitpocket.biometric.BiometricSeedResult
import io.github.brunogutierre.bitpocket.core.send.AmountError
import io.github.brunogutierre.bitpocket.core.send.BlockReason
import io.github.brunogutierre.bitpocket.core.send.ConfirmReason
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.GuardrailVerdict
import io.github.brunogutierre.bitpocket.core.send.RecipientError
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import io.github.brunogutierre.bitpocket.core.session.SeedOutcome
import io.github.brunogutierre.bitpocket.core.settings.Guardrails
import io.github.brunogutierre.bitpocket.core.settings.ScheduledGuardrails
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.wallet.ChainUnavailableException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

class SendViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    // A known recipient and a large balance: the default guardrails then ask for nothing.
    private val pocket =
        FakePocketWallet().apply {
            fees = FeeOptions(fast = 9, normal = 4, slow = 1)
            stats = stats.copy(pastRecipients = setOf(ADDRESS), balanceSats = 1_000_000)
        }
    private val access = FakeWalletAccess()
    private val plan = SpendPlan("cHNidP8=", ADDRESS, 10_000, 300, 4, 39_700, 1)

    private fun SendViewModel(
        pocket: FakePocketWallet,
        biometricSeed: ByteArray? = null,
        settings: WalletSettings = WalletSettings(),
    ) = SendViewModel(pocket, access, biometricSeed, settings, main.dispatcher)

    /** A view model already on REVIEW with [plan]. */
    private fun reviewing(
        biometricSeed: ByteArray? = null,
        settings: WalletSettings = WalletSettings(),
    ): SendViewModel {
        pocket.plans += plan
        return SendViewModel(pocket, biometricSeed, settings).apply {
            onAddressChange(ADDRESS)
            onAmountChange("10000")
            review()
        }
    }

    @Test
    fun `reviews a valid spend at the chosen fee speed`() {
        pocket.plans += plan
        val vm = SendViewModel(pocket)
        vm.onAddressChange(" $ADDRESS ")
        vm.onAmountChange("10 000")
        vm.onSpeedChange(FeeSpeed.FAST)

        vm.review()

        assertEquals(SpendRequest(ADDRESS, SendAmount.Exact(10_000), 9), pocket.lastSpendRequest)
        assertEquals(SendStage.REVIEW, vm.state.value.stage)
        assertEquals(plan, vm.state.value.plan)
    }

    @Test
    fun `send max ignores the amount field`() {
        pocket.plans += plan
        val vm = SendViewModel(pocket)
        vm.onAddressChange(ADDRESS)
        vm.onAmountChange("garbage")
        vm.onSendMaxChange(true)

        vm.review()

        assertEquals(SpendRequest(ADDRESS, SendAmount.Max, 4), pocket.lastSpendRequest)
    }

    @Test
    fun `validation errors are shown only on review and cleared by typing`() {
        val vm = SendViewModel(pocket)
        vm.onAddressChange("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        vm.onAmountChange("12")
        assertNull(vm.state.value.addressError)

        vm.review()

        assertEquals(RecipientError.WRONG_NETWORK, vm.state.value.addressError)
        assertEquals(AmountError.BELOW_DUST, vm.state.value.amountError)
        assertNull(pocket.lastSpendRequest)
        vm.onAddressChange(ADDRESS)
        assertNull(vm.state.value.addressError)
    }

    @Test
    fun `a BIP21 amount fills an empty amount field`() {
        val vm = SendViewModel(pocket)

        vm.onAddressChange("bitcoin:$ADDRESS?amount=0.0002")

        assertEquals("20000", vm.state.value.amountText)
    }

    @Test
    fun `spend errors keep the user on the form, and edit returns from review`() {
        val vm = SendViewModel(pocket)
        vm.onAddressChange(ADDRESS)
        vm.onAmountChange("1000")

        vm.review()
        assertEquals(SpendError.INSUFFICIENT_FUNDS, vm.state.value.spendError)
        assertEquals(SendStage.EDIT, vm.state.value.stage)

        pocket.plans += plan
        vm.review()
        vm.edit()
        assertEquals(SendStage.EDIT, vm.state.value.stage)
        assertNull(vm.state.value.plan)
        assertEquals("1000", vm.state.value.amountText)
    }

    @Test
    fun `without fee rates nothing can be reviewed until a retry succeeds`() {
        pocket.offline = true
        val vm = SendViewModel(pocket)
        assertTrue(vm.state.value.feesFailed)
        assertFalse(vm.state.value.canReview)

        pocket.offline = false
        vm.loadFees()

        assertTrue(vm.state.value.canReview)
        assertEquals(4L, vm.state.value.feeRate)
    }

    @Test
    fun `without biometrics the PIN unlocks the seed, which is wiped after sending`() {
        val seed = ByteArray(16) { 5 }
        access.seedOutcomes += SeedOutcome.Seed(seed)
        val vm = reviewing()

        vm.confirm()
        assertEquals(SendStage.AUTH_PIN, vm.state.value.stage)
        "123456".forEach(vm::pinDigit)

        assertEquals(listOf("123456"), access.signingPins)
        assertArrayEquals(ByteArray(16) { 5 }, pocket.sentWith.single())
        assertArrayEquals(ByteArray(16), seed)
        assertEquals(SendStage.DONE, vm.state.value.stage)
        assertEquals("txid-1", vm.state.value.txid)
    }

    @Test
    fun `a wrong PIN shakes and keeps asking`() {
        access.seedOutcomes += SeedOutcome.WrongPin(Duration.ZERO)
        val vm = reviewing()
        vm.confirm()

        "000000".forEach(vm::pinDigit)

        assertEquals(SendStage.AUTH_PIN, vm.state.value.stage)
        assertTrue(vm.state.value.wrongPin)
        assertEquals(1, vm.state.value.pinErrorSignal)
        assertTrue(pocket.sentWith.isEmpty())
        vm.cancelAuth()
        assertEquals(SendStage.REVIEW, vm.state.value.stage)
    }

    @Test
    fun `biometrics sign when set up, and fall back to the PIN when they fail`() {
        val vm = reviewing(biometricSeed = byteArrayOf(1, 2, 3))
        vm.confirm()
        assertEquals(SendStage.AUTH_BIOMETRIC, vm.state.value.stage)

        vm.onBiometricResult(BiometricSeedResult.Failed(BiometricFailure.INVALIDATED))
        assertEquals(SendStage.AUTH_PIN, vm.state.value.stage)
        assertEquals(BiometricFailure.INVALIDATED, vm.state.value.biometricFailure)

        vm.cancelAuth()
        vm.confirm()
        val seed = ByteArray(16) { 8 }
        vm.onBiometricResult(BiometricSeedResult.Opened(seed))
        assertEquals(SendStage.DONE, vm.state.value.stage)
        assertArrayEquals(ByteArray(16), seed)
    }

    @Test
    fun `an unreachable backend returns to review and says nothing was sent`() {
        pocket.sendError = ChainUnavailableException(RuntimeException("offline"))
        val vm = reviewing(biometricSeed = byteArrayOf(1))
        vm.confirm()

        vm.onBiometricResult(BiometricSeedResult.Opened(ByteArray(16)))

        assertEquals(SendStage.REVIEW, vm.state.value.stage)
        assertTrue(vm.state.value.broadcastFailed)
        pocket.sendError = SpendException(SpendError.REJECTED)
        vm.confirm()
        vm.onBiometricResult(BiometricSeedResult.Opened(ByteArray(16)))
        assertEquals(SpendError.REJECTED, vm.state.value.spendError)
        assertFalse(vm.state.value.broadcastFailed)
    }

    @Test
    fun `a spend over a hard limit cannot be confirmed`() {
        val vm = reviewing(settings = WalletSettings(guardrails = Guardrails(perTxLimitSats = 5_000)))

        assertEquals(GuardrailVerdict.Blocked(BlockReason.PER_TX_LIMIT, 5_000), vm.state.value.guardrail)
        assertFalse(vm.state.value.canConfirm)
        vm.confirm()
        assertEquals(SendStage.REVIEW, vm.state.value.stage)
    }

    @Test
    fun `a loosening only counts once the synced tip reaches it`() {
        val strict = Guardrails(perTxLimitSats = 5_000)
        val settings =
            WalletSettings(guardrails = strict, scheduledGuardrails = ScheduledGuardrails(Guardrails(), activatesAtHeight = 1_001))

        assertFalse(reviewing(settings = settings).state.value.canConfirm)
        pocket.stats = pocket.stats.copy(tipHeight = 1_001)
        assertTrue(reviewing(settings = settings).state.value.canConfirm)
    }

    @Test
    fun `new recipients and large shares must be ticked before confirming`() {
        pocket.stats = pocket.stats.copy(pastRecipients = emptySet(), balanceSats = 15_000)
        val vm = reviewing(biometricSeed = byteArrayOf(1))
        val reasons = setOf(ConfirmReason.NEW_RECIPIENT, ConfirmReason.LARGE_SHARE)
        assertEquals(GuardrailVerdict.NeedsConfirmation(reasons), vm.state.value.guardrail)

        vm.acknowledge(ConfirmReason.NEW_RECIPIENT, true)
        vm.confirm()
        assertEquals(SendStage.REVIEW, vm.state.value.stage)

        vm.acknowledge(ConfirmReason.LARGE_SHARE, true)
        vm.confirm()
        assertEquals(SendStage.AUTH_BIOMETRIC, vm.state.value.stage)
    }

    @Test
    fun `a recipient that looks like an own address must be confirmed`() {
        val own = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
        pocket.stats = pocket.stats.copy(pastRecipients = setOf(LOOKALIKE), ownAddresses = setOf(own))
        pocket.plans += plan.copy(recipient = LOOKALIKE)
        val vm = SendViewModel(pocket)
        vm.onAddressChange(LOOKALIKE)
        vm.onAmountChange("10000")

        vm.review()

        assertEquals(own, vm.state.value.lookalikeOf)
        assertEquals(GuardrailVerdict.NeedsConfirmation(setOf(ConfirmReason.LOOKALIKE)), vm.state.value.guardrail)
        assertFalse(vm.state.value.canConfirm)
    }

    @Test
    fun `a scanned BIP21 link fills recipient and amount, and cancelling keeps the form`() {
        val vm = SendViewModel(pocket)
        vm.onScanned("ignored outside the scanner")
        assertEquals("", vm.state.value.addressText)

        vm.startScan()
        assertEquals(SendStage.SCAN, vm.state.value.stage)
        vm.cancelScan()
        assertEquals(SendStage.EDIT, vm.state.value.stage)

        vm.startScan()
        vm.onScanned("bitcoin:$ADDRESS?amount=0.0005")
        assertEquals(SendStage.EDIT, vm.state.value.stage)
        assertEquals("bitcoin:$ADDRESS?amount=0.0005", vm.state.value.addressText)
        assertEquals("50000", vm.state.value.amountText)
    }

    private companion object {
        const val ADDRESS = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"

        // A valid testnet P2WPKH address with the same "qw50" start and "jzsx" end as ADDRESS,
        // found by brute force in ~4 million tries: what a poisoning address looks like.
        const val LOOKALIKE = "tb1qw50vy30pdhwf3xn46m0u2auv3d7gm5fdhnjzsx"
    }
}
