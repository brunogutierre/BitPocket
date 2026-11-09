package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import java.io.File

/** Opens the pockets of a wallet that was just unlocked. */
interface WalletOpener {
    fun open(payload: SlotPayload): PocketWallet

    /** The wallet's own directory (databases, Vault backup), named from public data. */
    fun dirOf(payload: SlotPayload): File
}

/**
 * Opens the watch-only Spending wallet under `<root>/wallets/<id>/`, syncing through the chain
 * client [chainFor] returns for the wallet network. Blocking (SQLite): call off the main thread.
 */
class BdkWalletOpener(
    private val root: File,
    private val chainFor: (SupportedNetwork) -> ChainClient,
) : WalletOpener {
    override fun dirOf(payload: SlotPayload): File {
        val descriptors = SpendingDescriptors.fromEntropy(payload.seedEntropy, payload.network).use { it.toPublic() }
        return WalletFiles.dir(root, WalletFiles.id(payload.network, descriptors))
    }

    override fun open(payload: SlotPayload): PocketWallet {
        val descriptors = SpendingDescriptors.fromEntropy(payload.seedEntropy, payload.network).use { it.toPublic() }
        val dir = WalletFiles.dir(root, WalletFiles.id(payload.network, descriptors))
        val chain = chainFor(payload.network)
        return try {
            BdkWallet.open(dir, descriptors, payload.network, chain)
        } catch (e: Exception) {
            chain.close()
            throw e
        }
    }
}
