package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.SignersContainer
import org.bitcoindevkit.Wallet

/**
 * Signs a watch-only wallet's PSBT with private keys that exist only during this call (ADR 0004):
 * the descriptors and signers are built from the seed entropy, never from a mnemonic String, and
 * every native object holding a key is closed before returning. The caller wipes the entropy.
 */
internal object SeedSigner {
    /** @return true when every input is signed and finalized. */
    fun sign(
        wallet: Wallet,
        psbt: Psbt,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    ): Boolean =
        SpendingDescriptors.fromEntropy(seedEntropy, network).use { descriptors ->
            SignersContainer.fromDescriptor(descriptors.external).use { external ->
                SignersContainer.fromDescriptor(descriptors.internal).use { internal ->
                    // Change inputs are signed by the internal keychain's keys.
                    wallet.signWithSigners(psbt, listOf(external, internal))
                }
            }
        }
}
