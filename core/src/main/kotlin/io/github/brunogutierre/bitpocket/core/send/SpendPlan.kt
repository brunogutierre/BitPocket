package io.github.brunogutierre.bitpocket.core.send

sealed interface SendAmount {
    data class Exact(
        val sats: Long,
    ) : SendAmount

    /** Everything spendable, minus the fee (no change output). */
    data object Max : SendAmount
}

data class SpendRequest(
    val address: String,
    val amount: SendAmount,
    val feeRateSatPerVb: Long,
)

/**
 * A built but unsigned spend, as the user reviews it. The PSBT travels as base64 (plain String),
 * and is parsed and checked again before signing.
 */
data class SpendPlan(
    val psbtBase64: String,
    val recipient: String,
    val amountSats: Long,
    val feeSats: Long,
    val feeRateSatPerVb: Long,
    val changeSats: Long,
    val inputCount: Int,
) {
    /** What leaves the wallet: amount plus fee. */
    val totalSats: Long get() = amountSats + feeSats
}

enum class SpendError {
    INVALID_ADDRESS,

    /** Not enough CONFIRMED coins: unconfirmed ones are never spent (ADR 0002). */
    INSUFFICIENT_FUNDS,
    BELOW_DUST,

    /** The guard found an input that is not a confirmed coin of this wallet. */
    UNSAFE_INPUT,
    OTHER,
}

class SpendException(
    val error: SpendError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/** Builds spends of a pocket. Throws [SpendException]. */
interface SpendPort {
    suspend fun planSpend(request: SpendRequest): SpendPlan
}
