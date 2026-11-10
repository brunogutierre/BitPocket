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

    /** The seed did not finalize every input (it is not this wallet's seed). */
    SIGNING_FAILED,

    /** The signed transaction does not match the reviewed plan; nothing was broadcast. */
    PLAN_MISMATCH,

    /** The network refused the transaction (e.g. its coins were already spent). */
    REJECTED,

    /** A Vault transaction did not use the expected policy branch (ADR 0002 guard). */
    WRONG_BRANCH,
    OTHER,
}

class SpendException(
    val error: SpendError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/** Builds and sends spends of a pocket. Throws [SpendException]. */
interface SpendPort {
    suspend fun planSpend(request: SpendRequest): SpendPlan

    /**
     * Signs [plan] with [seedEntropy] (the caller wipes it), checks the signed transaction against
     * the plan, broadcasts it and records it as pending. Returns the txid.
     * Throws `ChainUnavailableException` when the backend cannot be reached (nothing was sent).
     */
    suspend fun send(
        plan: SpendPlan,
        seedEntropy: ByteArray,
    ): String
}
