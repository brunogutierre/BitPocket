package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.SlotPayload
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import java.io.File

/** Opens the pockets of a wallet that was just unlocked. */
interface WalletOpener {
    fun open(payload: SlotPayload): PocketWallet

    /** The wallet's own directory (databases, Vault backup), named from public data. */
    fun dirOf(payload: SlotPayload): File

    /** Opens the watch-only Vault pocket described by [config], next to the Spending one. */
    fun openVault(
        payload: SlotPayload,
        config: VaultConfig,
    ): VaultPocket
}

/**
 * Opens the watch-only pockets under `<root>/wallets/<id>/`, syncing through the chain client
 * [chainFor] returns for the wallet network and server. Blocking (SQLite): call off the main thread.
 */
class BdkWalletOpener(
    private val root: File,
    /** A chain client for the network, at the wallet's custom server URL when it has one. */
    private val chainFor: (SupportedNetwork, String?) -> ChainClient,
) : WalletOpener {
    override fun dirOf(payload: SlotPayload): File {
        val descriptors = SpendingDescriptors.fromEntropy(payload.seedEntropy, payload.network).use { it.toPublic() }
        return WalletFiles.dir(root, WalletFiles.id(payload.network, descriptors))
    }

    override fun open(payload: SlotPayload): PocketWallet {
        val descriptors = SpendingDescriptors.fromEntropy(payload.seedEntropy, payload.network).use { it.toPublic() }
        return withChain(payload) { chain -> BdkWallet.open(dirOf(payload), descriptors, payload.network, chain) }
    }

    override fun openVault(
        payload: SlotPayload,
        config: VaultConfig,
    ): VaultPocket =
        withChain(payload) { chain ->
            BdkWallet.open(dirOf(payload), config.publicDescriptors(payload.network), payload.network, chain, vault = config)
        }

    private fun <T : PocketWallet> withChain(
        payload: SlotPayload,
        open: (ChainClient) -> T,
    ): T {
        val chain = chainFor(payload.network, payload.settings.esploraUrl)
        return try {
            open(chain)
        } catch (e: Exception) {
            chain.close()
            throw e
        }
    }
}
