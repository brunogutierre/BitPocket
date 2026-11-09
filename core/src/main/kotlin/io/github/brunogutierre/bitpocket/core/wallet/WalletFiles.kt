package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import java.io.File
import java.security.MessageDigest

/**
 * Where a wallet keeps its (non-secret) files: `<root>/wallets/<id>/`.
 *
 * The id comes from public data, never from the slot, so the directory name does not reveal which
 * slot is in use. The network is part of it because Signet and Testnet4 share the same `tpub`.
 */
object WalletFiles {
    private const val ID_BYTES = 8

    fun id(
        network: SupportedNetwork,
        descriptors: PublicDescriptors,
    ): String {
        // network.name is part of the on-disk layout: renaming an enum constant moves the wallet.
        val digest = MessageDigest.getInstance("SHA-256").digest("${network.name}\n${descriptors.external}".toByteArray())
        return digest.take(ID_BYTES).joinToString("") { "%02x".format(it) }
    }

    fun dir(
        root: File,
        id: String,
    ): File = File(root, "wallets/$id")
}
