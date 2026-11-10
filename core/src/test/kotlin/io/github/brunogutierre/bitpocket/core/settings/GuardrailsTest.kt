package io.github.brunogutierre.bitpocket.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GuardrailsTest {
    private val strict =
        Guardrails(perTxLimitSats = 10_000, windowLimitSats = 50_000, confirmNewRecipients = true, confirmLargeShare = true)

    @Test
    fun `round-trips guardrails and a scheduled change in the settings records`() {
        val settings =
            WalletSettings(
                autoLock = AutoLock.THIRTY_SECONDS,
                guardrails = strict,
                scheduledGuardrails = ScheduledGuardrails(Guardrails(confirmLargeShare = false), activatesAtHeight = 250_144),
            )

        assertEquals(settings, WalletSettings.decode(settings.encode()))
        assertEquals(WalletSettings(), WalletSettings.decode(WalletSettings().encode()))
    }

    @Test
    fun `tightening applies at once`() {
        val settings = WalletSettings(guardrails = Guardrails())

        val changed = GuardrailsPolicy.change(settings, strict, tipHeight = 100)

        assertEquals(strict, changed.guardrails)
        assertNull(changed.scheduledGuardrails)
    }

    @Test
    fun `loosening waits 144 blocks of synced tip`() {
        val settings = WalletSettings(guardrails = strict)
        val looser = strict.copy(perTxLimitSats = 100_000, confirmLargeShare = false)

        val changed = GuardrailsPolicy.change(settings, looser, tipHeight = 1_000)

        assertEquals(strict, changed.guardrails)
        assertEquals(ScheduledGuardrails(looser, 1_144), changed.scheduledGuardrails)
        assertEquals(strict, GuardrailsPolicy.effective(changed, 1_143))
        assertEquals(looser, GuardrailsPolicy.effective(changed, 1_144))
    }

    @Test
    fun `a mixed change tightens now and loosens later`() {
        val settings = WalletSettings(guardrails = strict)
        val mixed = strict.copy(perTxLimitSats = 5_000, windowLimitSats = null)

        val changed = GuardrailsPolicy.change(settings, mixed, tipHeight = 10)

        assertEquals(strict.copy(perTxLimitSats = 5_000), changed.guardrails)
        assertEquals(ScheduledGuardrails(mixed, 154), changed.scheduledGuardrails)
    }

    @Test
    fun `changing again starts from what is in force, and tightening cancels a pending loosening`() {
        val pending = GuardrailsPolicy.change(WalletSettings(guardrails = strict), Guardrails(), tipHeight = 5)

        val cancelled = GuardrailsPolicy.change(pending, strict, tipHeight = 10)

        assertEquals(strict, cancelled.guardrails)
        assertNull(cancelled.scheduledGuardrails)
    }

    @Test
    fun `loosening needs a synced tip, tightening does not`() {
        val settings = WalletSettings(guardrails = strict)

        assertThrows<IllegalStateException> { GuardrailsPolicy.change(settings, Guardrails(), tipHeight = 0) }
        assertEquals(strict, GuardrailsPolicy.change(WalletSettings(), strict, tipHeight = 0).guardrails)
        assertTrue(GuardrailsPolicy.loosens(settings, Guardrails(), 10))
        assertFalse(GuardrailsPolicy.loosens(settings, strict.copy(perTxLimitSats = 1), 10))
    }

    @Test
    fun `limits must be positive`() {
        assertThrows<IllegalArgumentException> { Guardrails(perTxLimitSats = 0) }
    }
}
