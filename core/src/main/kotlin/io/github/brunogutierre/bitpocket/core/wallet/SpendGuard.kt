package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.send.SpendError
import io.github.brunogutierre.bitpocket.core.send.SpendException
import org.bitcoindevkit.ChainPosition
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Wallet

/**
 * ADR 0002 guard, applied to EVERY spend (Spending and Vault): only confirmed coins of this
 * wallet may be inputs. Finalizing a spend of an unconfirmed coin under an `older()` descriptor
 * aborts the whole process in BDK 3.1.0, so the rule is enforced twice: when building
 * ([restrict]) and on the built PSBT ([check]).
 */
internal object SpendGuard {
    fun restrict(builder: TxBuilder): TxBuilder = builder.excludeUnconfirmed()

    /** @throws SpendException with [SpendError.UNSAFE_INPUT] for any other input. */
    fun check(
        psbt: Psbt,
        wallet: Wallet,
    ) {
        psbt.extractTxUncheckedFeeRate().use { tx ->
            tx.input().forEach { input ->
                val utxo = wallet.getUtxo(input.previousOutput)
                if (utxo == null || utxo.isSpent || utxo.chainPosition !is ChainPosition.Confirmed) {
                    throw SpendException(SpendError.UNSAFE_INPUT)
                }
            }
        }
    }
}
