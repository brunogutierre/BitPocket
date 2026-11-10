package io.github.brunogutierre.bitpocket.core.vault

import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import org.bitcoindevkit.Transaction

/**
 * ADR 0002 pre-broadcast checks for Vault transactions: BDK can finalize through a branch other
 * than the one chosen, so the signed transaction is inspected before it leaves the phone.
 */
object VaultGuard {
    /** The 2-of-2 branch: no input carries the heir's relative timelock and each has two signatures. */
    fun checkTwoOfTwo(
        tx: Transaction,
        timelockBlocks: Int,
    ) {
        tx.input().forEach { input ->
            if (input.sequence == timelockBlocks.toUInt() || signatureCount(input.witness) != 2) {
                throw SpendException(SpendError.WRONG_BRANCH)
            }
        }
    }

    /** DER-encoded ECDSA signatures (0x30 ... + sighash byte) in a witness stack. */
    internal fun signatureCount(witness: List<ByteArray>): Int = witness.count { it.size in 9..73 && it[0] == 0x30.toByte() }
}
