package io.github.brunogutierre.bitpocket.core.send

import io.github.brunogutierre.bitpocket.core.settings.Guardrails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GuardrailCheckTest {
    private val plan = SpendPlan("", "tb1qfriend", amountSats = 9_000, feeSats = 1_000, feeRateSatPerVb = 2, changeSats = 0, inputCount = 1)
    private val stats = SpendingStats(tipHeight = 500, spentInWindowSats = 0, pastRecipients = setOf("tb1qfriend"), balanceSats = 100_000)
    private val noConfirmations = Guardrails(confirmNewRecipients = false, confirmLargeShare = false)

    @Test
    fun `nothing to check with default guardrails for a known recipient and a small amount`() {
        assertEquals(GuardrailVerdict.Allowed, GuardrailCheck.evaluate(Guardrails(), plan, stats))
    }

    @Test
    fun `limits count amount plus fee`() {
        assertEquals(GuardrailVerdict.Allowed, GuardrailCheck.evaluate(noConfirmations.copy(perTxLimitSats = 10_000), plan, stats))
        assertEquals(
            GuardrailVerdict.Blocked(BlockReason.PER_TX_LIMIT, 9_999),
            GuardrailCheck.evaluate(noConfirmations.copy(perTxLimitSats = 9_999), plan, stats),
        )
    }

    @Test
    fun `the window limit subtracts what was already spent`() {
        val spent = stats.copy(spentInWindowSats = 45_000)

        assertEquals(
            GuardrailVerdict.Blocked(BlockReason.WINDOW_LIMIT, 5_000),
            GuardrailCheck.evaluate(noConfirmations.copy(windowLimitSats = 50_000), plan, spent),
        )
        assertEquals(
            GuardrailVerdict.Blocked(BlockReason.WINDOW_LIMIT, 0),
            GuardrailCheck.evaluate(noConfirmations.copy(windowLimitSats = 40_000), plan, spent),
        )
    }

    @Test
    fun `always asks to confirm a recipient that looks like a known address`() {
        val known = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"
        val poisoned = plan.copy(recipient = "tb1p8wptqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqt9zj")
        val noConfirmations = Guardrails(confirmNewRecipients = false, confirmLargeShare = false)

        val verdict = GuardrailCheck.evaluate(noConfirmations, poisoned, stats.copy(ownAddresses = setOf(known)))

        assertEquals(GuardrailVerdict.NeedsConfirmation(setOf(ConfirmReason.LOOKALIKE)), verdict)
    }

    @Test
    fun `asks to confirm a new recipient and a spend over half the balance`() {
        val verdict = GuardrailCheck.evaluate(Guardrails(), plan, stats.copy(pastRecipients = emptySet(), balanceSats = 19_999))

        assertEquals(GuardrailVerdict.NeedsConfirmation(setOf(ConfirmReason.NEW_RECIPIENT, ConfirmReason.LARGE_SHARE)), verdict)
    }
}
