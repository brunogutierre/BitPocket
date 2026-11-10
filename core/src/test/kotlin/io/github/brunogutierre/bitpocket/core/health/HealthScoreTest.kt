package io.github.brunogutierre.bitpocket.core.health

import io.github.brunogutierre.bitpocket.core.settings.AutoLock
import io.github.brunogutierre.bitpocket.core.settings.Guardrails
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.vault.HeartbeatStatus
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthScoreTest {
    private val vault =
        VaultConfig("[00000000/48'/1'/0'/2']tpubA", "[00000000/48'/1'/0'/2']tpubB", "[00000000/48'/1'/0'/2']tpubC", HeirTimelock.YEAR)
    private val everything =
        WalletSettings(
            autoLock = AutoLock.THIRTY_SECONDS,
            guardrails = Guardrails(perTxLimitSats = 100_000),
            vault = vault,
            duress = true,
            esploraUrl = "https://example.org/api",
        )

    @Test
    fun `the weights add up to 100`() {
        assertEquals(100, HealthCheck.entries.sumOf { it.weight })
    }

    @Test
    fun `a fresh wallet passes only the default auto-lock`() {
        val report = HealthScore.evaluate(HealthInputs(WalletSettings(), biometricOn = false, heartbeat = null))

        assertEquals(setOf(HealthCheck.QUICK_AUTO_LOCK), report.passed)
        assertEquals(15, report.score)
        assertEquals(Grade.F, report.grade)
        assertEquals(6, report.failed.size)
    }

    @Test
    fun `everything set up is an A, and a due heartbeat or a slow lock costs points`() {
        assertEquals(Grade.A, HealthScore.evaluate(HealthInputs(everything, true, HeartbeatStatus.OK)).grade)
        assertEquals(100, HealthScore.evaluate(HealthInputs(everything, true, null)).score)

        val due = HealthScore.evaluate(HealthInputs(everything.copy(autoLock = AutoLock.FIVE_MINUTES), true, HeartbeatStatus.EXPIRED))
        assertEquals(setOf(HealthCheck.QUICK_AUTO_LOCK, HealthCheck.VAULT_HEARTBEAT), due.failed.toSet())
        assertEquals(75, due.score)
        assertEquals(Grade.B, due.grade)
    }
}
