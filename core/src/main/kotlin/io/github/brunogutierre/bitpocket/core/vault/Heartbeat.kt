package io.github.brunogutierre.bitpocket.core.vault

/** How close the oldest Vault coin is to the heir's timelock. */
enum class HeartbeatStatus {
    /** No coins, or every coin is far from the timelock. */
    OK,

    /** The oldest coin will be spendable by the heir soon: time to refresh. */
    DUE_SOON,

    /** The heir key alone can already spend at least one coin. */
    EXPIRED,
}

/** [blocksLeft] until the oldest confirmed coin matures for the heir (0 when expired, null without coins). */
data class Heartbeat(
    val status: HeartbeatStatus,
    val blocksLeft: Int?,
) {
    companion object {
        /**
         * A relative timelock counts per coin from its confirmation: a coin confirmed at height h
         * has `tip - h + 1` confirmations and matures for the heir at `timelock` confirmations.
         * Pending coins have not started counting. "Due soon" is the last tenth of the timelock
         * (at least one day of blocks for long timelocks, but never the whole period).
         */
        fun of(
            tipHeight: Int,
            confirmationHeights: List<Int?>,
            timelockBlocks: Int,
        ): Heartbeat {
            val oldest = confirmationHeights.filterNotNull().minOrNull() ?: return Heartbeat(HeartbeatStatus.OK, null)
            val confirmations = tipHeight - oldest + 1
            val left = (timelockBlocks - confirmations).coerceAtLeast(0)
            val warning = minOf(maxOf(timelockBlocks / 10, DAY_BLOCKS), timelockBlocks / 2)
            val status =
                when {
                    left == 0 -> HeartbeatStatus.EXPIRED
                    left <= warning -> HeartbeatStatus.DUE_SOON
                    else -> HeartbeatStatus.OK
                }
            return Heartbeat(status, left)
        }

        private const val DAY_BLOCKS = 144
    }
}
