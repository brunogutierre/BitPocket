package io.github.brunogutierre.bitpocket.core.send

import io.github.brunogutierre.bitpocket.core.settings.Guardrails

/** What the guardrails need to know about the wallet's recent spending. */
data class SpendingStats(
    val tipHeight: Int,
    /** Sent (fees included) by pending transactions and those confirmed in the last window. */
    val spentInWindowSats: Long,
    /** Addresses this wallet has paid before. */
    val pastRecipients: Set<String>,
    val balanceSats: Long,
)

enum class BlockReason { PER_TX_LIMIT, WINDOW_LIMIT }

enum class ConfirmReason { NEW_RECIPIENT, LARGE_SHARE }

sealed interface GuardrailVerdict {
    data object Allowed : GuardrailVerdict

    /** Over a hard limit; [allowedSats] is the most that could leave the wallet now. */
    data class Blocked(
        val reason: BlockReason,
        val allowedSats: Long,
    ) : GuardrailVerdict

    /** Allowed after the user confirms each of [reasons] once more. */
    data class NeedsConfirmation(
        val reasons: Set<ConfirmReason>,
    ) : GuardrailVerdict
}

object GuardrailCheck {
    fun evaluate(
        guardrails: Guardrails,
        plan: SpendPlan,
        stats: SpendingStats,
    ): GuardrailVerdict {
        guardrails.perTxLimitSats?.let { limit ->
            if (plan.totalSats > limit) return GuardrailVerdict.Blocked(BlockReason.PER_TX_LIMIT, limit)
        }
        guardrails.windowLimitSats?.let { limit ->
            val left = (limit - stats.spentInWindowSats).coerceAtLeast(0)
            if (plan.totalSats > left) return GuardrailVerdict.Blocked(BlockReason.WINDOW_LIMIT, left)
        }
        val reasons =
            buildSet {
                if (guardrails.confirmNewRecipients && plan.recipient !in stats.pastRecipients) add(ConfirmReason.NEW_RECIPIENT)
                if (guardrails.confirmLargeShare && plan.totalSats * 2 > stats.balanceSats) add(ConfirmReason.LARGE_SHARE)
            }
        return if (reasons.isEmpty()) GuardrailVerdict.Allowed else GuardrailVerdict.NeedsConfirmation(reasons)
    }
}
