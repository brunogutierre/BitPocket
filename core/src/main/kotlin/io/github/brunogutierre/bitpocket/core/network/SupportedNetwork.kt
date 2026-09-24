package io.github.brunogutierre.bitpocket.core.network

import org.bitcoindevkit.Network
import org.bitcoindevkit.NetworkKind

/**
 * The only Bitcoin networks BitPocket runs on. Mainnet is disabled by design.
 *
 * This file is the single place allowed to map to BDK network types
 * (enforced in CI by `scripts/check-no-mainnet.sh`).
 */
enum class SupportedNetwork(
    val displayName: String,
    val esploraBaseUrl: String,
) {
    SIGNET(displayName = "Signet", esploraBaseUrl = "https://mempool.space/signet/api"),
    TESTNET4(displayName = "Testnet4", esploraBaseUrl = "https://mempool.space/testnet4/api"),
    ;

    fun toBdkNetwork(): Network =
        when (this) {
            SIGNET -> Network.SIGNET
            TESTNET4 -> Network.TESTNET4
        }

    /** Key/descriptor network kind: every supported network uses test keys (`tprv`/`tpub`). */
    fun toBdkNetworkKind(): NetworkKind = NetworkKind.TEST

    companion object {
        val DEFAULT: SupportedNetwork = SIGNET
    }
}
