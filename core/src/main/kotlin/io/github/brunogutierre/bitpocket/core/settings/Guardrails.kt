package io.github.brunogutierre.bitpocket.core.settings

import java.nio.ByteBuffer

/**
 * Spending guardrails of a wallet. Limits apply to what leaves the wallet (amount + fee).
 * The window is [WINDOW_BLOCKS] blocks (about 24 hours), counted on the synced chain tip.
 */
data class Guardrails(
    val perTxLimitSats: Long? = null,
    val windowLimitSats: Long? = null,
    val confirmNewRecipients: Boolean = true,
    /** Extra confirmation when a spend takes more than half of the balance. */
    val confirmLargeShare: Boolean = true,
) {
    init {
        require((perTxLimitSats ?: 1) > 0 && (windowLimitSats ?: 1) > 0) { "Limits must be positive" }
    }

    /** The strictest of this and [other], dimension by dimension. */
    fun tightest(other: Guardrails) =
        Guardrails(
            perTxLimitSats = minLimit(perTxLimitSats, other.perTxLimitSats),
            windowLimitSats = minLimit(windowLimitSats, other.windowLimitSats),
            confirmNewRecipients = confirmNewRecipients || other.confirmNewRecipients,
            confirmLargeShare = confirmLargeShare || other.confirmLargeShare,
        )

    internal fun encode(): ByteArray =
        ByteBuffer
            .allocate(ENCODED_SIZE)
            .putLong(perTxLimitSats ?: NO_LIMIT)
            .putLong(windowLimitSats ?: NO_LIMIT)
            .put(((if (confirmNewRecipients) 1 else 0) or (if (confirmLargeShare) 2 else 0)).toByte())
            .array()

    companion object {
        const val WINDOW_BLOCKS = 144
        internal const val ENCODED_SIZE = 8 + 8 + 1
        private const val NO_LIMIT = -1L

        internal fun decode(buffer: ByteBuffer): Guardrails {
            val perTx = buffer.long
            val window = buffer.long
            val flags = buffer.get().toInt()
            return Guardrails(
                perTxLimitSats = perTx.takeIf { it != NO_LIMIT },
                windowLimitSats = window.takeIf { it != NO_LIMIT },
                confirmNewRecipients = flags and 1 != 0,
                confirmLargeShare = flags and 2 != 0,
            )
        }

        /** null means "no limit", the loosest value. */
        private fun minLimit(
            a: Long?,
            b: Long?,
        ): Long? =
            if (a == null) {
                b
            } else if (b == null) {
                a
            } else {
                minOf(a, b)
            }
    }
}

/** A loosening that takes effect once the synced tip reaches [activatesAtHeight]. */
data class ScheduledGuardrails(
    val guardrails: Guardrails,
    val activatesAtHeight: Int,
)

/**
 * Guardrails can be tightened at once but loosened only after a delay (ADR 0006): a thief or a
 * coercer holding the unlocked phone cannot raise a limit and spend in the same sitting.
 */
object GuardrailsPolicy {
    /**
     * Applies the strictest of current and [requested] right away and, when [requested] loosens
     * anything, schedules it for [Guardrails.WINDOW_BLOCKS] blocks after [tipHeight].
     *
     * @throws IllegalStateException when loosening without a synced tip: counting from height 0
     *   would let the loosening apply at the very next sync.
     */
    fun change(
        settings: WalletSettings,
        requested: Guardrails,
        tipHeight: Int,
    ): WalletSettings {
        val current = effective(settings, tipHeight)
        val immediate = current.tightest(requested)
        if (immediate != requested) check(tipHeight > 0) { "Sync before loosening the guardrails" }
        val scheduled = if (immediate == requested) null else ScheduledGuardrails(requested, tipHeight + Guardrails.WINDOW_BLOCKS)
        return settings.copy(guardrails = immediate, scheduledGuardrails = scheduled)
    }

    /** Whether [requested] relaxes anything compared with what is in force at [tipHeight]. */
    fun loosens(
        settings: WalletSettings,
        requested: Guardrails,
        tipHeight: Int,
    ): Boolean = effective(settings, tipHeight).tightest(requested) != requested

    /** The guardrails in force at [tipHeight]. */
    fun effective(
        settings: WalletSettings,
        tipHeight: Int,
    ): Guardrails {
        val scheduled = settings.scheduledGuardrails ?: return settings.guardrails
        return if (tipHeight >= scheduled.activatesAtHeight) scheduled.guardrails else settings.guardrails
    }
}
