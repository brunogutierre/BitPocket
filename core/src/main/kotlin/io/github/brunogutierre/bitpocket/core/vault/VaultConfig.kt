package io.github.brunogutierre.bitpocket.core.vault

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.bitcoindevkit.DerivationPath
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.Mnemonic
import java.nio.ByteBuffer

/** The three Vault cosigners (ADR 0002, ADR 0007). */
enum class VaultRole(
    val id: Byte,
) {
    /** Derived from the wallet seed: this phone. */
    PHONE(1),

    /** An offline key (12 words on paper): needed with PHONE for every normal spend. */
    RECOVERY(2),

    /** Spends alone once a coin is [HeirTimelock] blocks old (inheritance, or a delayed backup). */
    HEIR(3),
}

/** How old (in blocks) a Vault coin must be before the heir key alone can spend it. Ids are stored. */
enum class HeirTimelock(
    val id: Byte,
    val blocks: Int,
) {
    /** For trying the heir path on Signet. */
    HOUR(0, 6),
    DAY(1, 144),
    MONTH(2, 4_320),
    YEAR(3, 52_560),
    ;

    companion object {
        fun of(id: Byte): HeirTimelock? = entries.firstOrNull { it.id == id }
    }
}

/** Account keys at the BIP48 P2WSH path m/48'/1'/0'/2' (coin type 1 = test networks). */
object VaultKeys {
    const val ACCOUNT_PATH = "m/48'/1'/0'/2'"

    /** `[fingerprint/48'/1'/0'/2']tpub...`, safe to store and share. */
    fun publicKey(
        entropy: ByteArray,
        network: SupportedNetwork,
    ): String = account(entropy, network) { it.asPublic().use { public -> public.toString() } }

    /**
     * `[fingerprint/48'/1'/0'/2']tprv...`: only for building a signer, right before signing.
     * BDK takes miniscript descriptors as text, so this secret is briefly a JVM String.
     */
    internal fun secretKey(
        entropy: ByteArray,
        network: SupportedNetwork,
    ): String = account(entropy, network) { it.toString() }

    private fun <T> account(
        entropy: ByteArray,
        network: SupportedNetwork,
        block: (DescriptorSecretKey) -> T,
    ): T =
        Mnemonic.fromEntropy(entropy).use { mnemonic ->
            DescriptorSecretKey(network.toBdkNetworkKind(), mnemonic, null).use { root ->
                DerivationPath(ACCOUNT_PATH).use { path -> root.derive(path).use(block) }
            }
        }
}

/**
 * Public description of a Vault: the cosigners' account keys and the heir timelock. Stored in the
 * slot payload and in the encrypted backup; with it, any cosigner can rebuild the watch-only wallet.
 *
 * Policy: `wsh(or_d(multi(2,PHONE,RECOVERY),and_v(v:pkh(HEIR),older(N))))`.
 */
data class VaultConfig(
    val phoneKey: String,
    val recoveryKey: String,
    val heirKey: String,
    val timelock: HeirTimelock,
) {
    init {
        listOf(phoneKey, recoveryKey, heirKey).forEach { require("tpub" in it && "prv" !in it) { "Vault keys must be test public keys" } }
        require(setOf(phoneKey, recoveryKey, heirKey).size == 3) { "The three cosigner keys must differ" }
    }

    fun keyOf(role: VaultRole): String =
        when (role) {
            VaultRole.PHONE -> phoneKey
            VaultRole.RECOVERY -> recoveryKey
            VaultRole.HEIR -> heirKey
        }

    /**
     * The descriptor for [branch]: `0` (receive), `1` (change) or `<0;1>` (BIP389 multipath, for the
     * watch-only wallet). [secrets] replaces some public keys by private ones, for a signer; BDK
     * rejects multipath descriptors with private keys, so signers use `0` or `1`.
     */
    fun descriptor(
        branch: String,
        secrets: Map<VaultRole, String> = emptyMap(),
    ): String {
        require(secrets.isEmpty() || branch == "0" || branch == "1") { "Signers need a single branch" }

        fun key(role: VaultRole) = (secrets[role] ?: keyOf(role)) + "/$branch/*"
        return "wsh(or_d(multi(2,${key(VaultRole.PHONE)},${key(VaultRole.RECOVERY)})," +
            "and_v(v:pkh(${key(VaultRole.HEIR)}),older(${timelock.blocks}))))"
    }

    /** `timelock id (1) | 3 x (length (2) | key)` in PHONE, RECOVERY, HEIR order. */
    fun encode(): ByteArray {
        val keys = listOf(phoneKey, recoveryKey, heirKey).map { it.toByteArray(Charsets.US_ASCII) }
        val buffer = ByteBuffer.allocate(1 + keys.sumOf { 2 + it.size }).put(timelock.id)
        keys.forEach { buffer.putShort(it.size.toShort()).put(it) }
        return buffer.array()
    }

    companion object {
        /** @throws IllegalArgumentException for a malformed record. */
        fun decode(bytes: ByteArray): VaultConfig {
            val buffer = ByteBuffer.wrap(bytes)
            try {
                val timelock = requireNotNull(HeirTimelock.of(buffer.get())) { "Unknown heir timelock" }
                val keys = List(3) { ByteArray(buffer.short.toInt() and 0xFFFF).also { buffer.get(it) }.toString(Charsets.US_ASCII) }
                require(!buffer.hasRemaining()) { "Trailing bytes in vault record" }
                return VaultConfig(keys[0], keys[1], keys[2], timelock)
            } catch (e: java.nio.BufferUnderflowException) {
                throw IllegalArgumentException("Truncated vault record", e)
            }
        }
    }
}
