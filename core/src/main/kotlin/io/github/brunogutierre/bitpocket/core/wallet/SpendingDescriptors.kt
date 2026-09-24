package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic

/**
 * BIP86 (single-key Taproot) descriptors of the Spending pocket, derived at m/86'/1'/0'
 * (coin type 1 = test networks): keychain 0 for receiving (external), 1 for change (internal).
 *
 * The descriptors hold private keys. `toString()` is redacted, but `toStringWithSecret()`
 * is not: never log it or persist it unencrypted.
 */
class SpendingDescriptors private constructor(
    val external: Descriptor,
    val internal: Descriptor,
) {
    companion object {
        fun fromMnemonic(
            mnemonic: Mnemonic,
            network: SupportedNetwork,
            passphrase: String? = null,
        ): SpendingDescriptors {
            val networkKind = network.toBdkNetworkKind()
            return DescriptorSecretKey(networkKind, mnemonic, passphrase).use { rootKey ->
                SpendingDescriptors(
                    external = Descriptor.newBip86(rootKey, KeychainKind.EXTERNAL, networkKind),
                    internal = Descriptor.newBip86(rootKey, KeychainKind.INTERNAL, networkKind),
                )
            }
        }
    }
}
