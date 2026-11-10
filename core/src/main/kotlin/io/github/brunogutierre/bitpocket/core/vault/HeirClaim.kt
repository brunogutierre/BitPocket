package io.github.brunogutierre.bitpocket.core.vault

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.BdkWallet
import io.github.brunogutierre.bitpocket.core.wallet.ChainClient
import io.github.brunogutierre.bitpocket.core.wallet.VaultPocket

sealed interface HeirClaimOpen {
    /** [vault] is an in-memory watch-only Vault wallet: sync it, then plan and send the claim. */
    class Opened(
        val vault: VaultPocket,
        val config: VaultConfig,
    ) : HeirClaimOpen

    /** The file is not a BitPocket Vault backup (or was modified). */
    data object InvalidBackup : HeirClaimOpen

    /** These words do not open the backup, or are not its heir key. */
    data object NotTheHeir : HeirClaimOpen
}

/** Starts an heir claim (ADR 0008): no wallet, no PIN, just the backup file and the heir's words. */
object HeirClaim {
    fun open(
        backup: ByteArray,
        heirSeed: ByteArray,
        chainFor: (SupportedNetwork) -> ChainClient,
    ): HeirClaimOpen {
        val contents =
            try {
                VaultBackup.open(backup, heirSeed)
            } catch (e: IllegalArgumentException) {
                return HeirClaimOpen.InvalidBackup
            } ?: return HeirClaimOpen.NotTheHeir
        val config = contents.config
        if (config.heirKey != VaultKeys.publicKey(heirSeed, contents.network)) return HeirClaimOpen.NotTheHeir
        val chain = chainFor(contents.network)
        return try {
            HeirClaimOpen.Opened(BdkWallet.inMemory(config.publicDescriptors(contents.network), contents.network, chain, config), config)
        } catch (e: Exception) {
            chain.close()
            throw e
        }
    }
}
