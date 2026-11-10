package io.github.brunogutierre.bitpocket.core.health

import io.github.brunogutierre.bitpocket.core.settings.AutoLock
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.vault.HeartbeatStatus

/** One check of the security health score, worth [weight] points when passed. */
enum class HealthCheck(
    val weight: Int,
) {
    /** Signing needs a strong biometric, so a watched PIN alone does not move coins. */
    BIOMETRIC_SIGNING(15),

    /** The app locks within a minute in the background. */
    QUICK_AUTO_LOCK(15),

    /** A per-transaction or 24-hour limit caps what a thief can send. */
    SPENDING_LIMIT(15),

    /** Larger amounts sit in the 2-of-2 Vault. */
    VAULT(20),

    /** The Vault's heir timelock is not about to expire (or there is no Vault coin yet). */
    VAULT_HEARTBEAT(10),

    /** Addresses go to the user's own server, not a third party. */
    OWN_SERVER(10),

    /** A duress PIN exists. */
    DURESS_PIN(15),
}

/** What the score looks at; everything comes from the unlocked session and its pockets. */
data class HealthInputs(
    val settings: WalletSettings,
    val biometricOn: Boolean,
    val heartbeat: HeartbeatStatus?,
)

enum class Grade { A, B, C, D, F }

data class HealthReport(
    val passed: Set<HealthCheck>,
    /** 0..100. */
    val score: Int,
    val grade: Grade,
) {
    val failed: List<HealthCheck> get() = HealthCheck.entries.filter { it !in passed }
}

/**
 * Grades the wallet's setup (M7). It only measures what BitPocket can see: whether the recovery
 * words are safely stored on paper is up to the user. Inside a decoy wallet the score is computed
 * the same way, from the decoy's own settings.
 */
object HealthScore {
    fun evaluate(inputs: HealthInputs): HealthReport {
        val settings = inputs.settings
        val passed =
            buildSet {
                if (inputs.biometricOn) add(HealthCheck.BIOMETRIC_SIGNING)
                if (settings.autoLock.seconds <= AutoLock.ONE_MINUTE.seconds) add(HealthCheck.QUICK_AUTO_LOCK)
                if (settings.guardrails.perTxLimitSats != null ||
                    settings.guardrails.windowLimitSats != null
                ) {
                    add(HealthCheck.SPENDING_LIMIT)
                }
                if (settings.vault != null) add(HealthCheck.VAULT)
                if (settings.vault != null && inputs.heartbeat != HeartbeatStatus.DUE_SOON && inputs.heartbeat != HeartbeatStatus.EXPIRED) {
                    add(HealthCheck.VAULT_HEARTBEAT)
                }
                if (settings.esploraUrl != null) add(HealthCheck.OWN_SERVER)
                if (settings.duress) add(HealthCheck.DURESS_PIN)
            }
        val score = passed.sumOf { it.weight }
        return HealthReport(passed, score, gradeOf(score))
    }

    private fun gradeOf(score: Int): Grade =
        when {
            score >= 90 -> Grade.A
            score >= 75 -> Grade.B
            score >= 55 -> Grade.C
            score >= 35 -> Grade.D
            else -> Grade.F
        }
}
