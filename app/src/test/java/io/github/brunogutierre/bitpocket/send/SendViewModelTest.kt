package io.github.brunogutierre.bitpocket.send

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.send.AmountError
import io.github.brunogutierre.bitpocket.core.send.FeeOptions
import io.github.brunogutierre.bitpocket.core.send.RecipientError
import io.github.brunogutierre.bitpocket.core.send.SendAmount
import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.send.SpendRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SendViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val pocket = FakePocketWallet().apply { fees = FeeOptions(fast = 9, normal = 4, slow = 1) }
    private val plan = SpendPlan("cHNidP8=", ADDRESS, 10_000, 300, 4, 39_700, 1)

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

    private companion object {
        const val ADDRESS = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
    }
}
