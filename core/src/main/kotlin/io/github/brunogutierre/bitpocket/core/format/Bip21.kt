package io.github.brunogutierre.bitpocket.core.format

/** BIP21 `bitcoin:` URIs. Parsing arrives with sending. */
object Bip21 {
    /** For sharing as text: `bitcoin:<address>`. */
    fun uri(address: String): String = "bitcoin:$address"

    /**
     * For QR codes: scheme and bech32 address uppercased (both are case-insensitive), so the whole
     * URI fits QR alphanumeric mode and the code gets visibly smaller and easier to scan.
     */
    fun qrUri(address: String): String = "BITCOIN:${address.uppercase()}"
}
