package io.github.brunogutierre.bitpocket.core.vault

import io.github.brunogutierre.bitpocket.core.crypto.AesGcmEnvelope
import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.security.SecureRandom

/** What a Vault backup holds: enough public data to rebuild the watch-only Vault wallet. */
data class VaultBackupContents(
    val network: SupportedNetwork,
    val config: VaultConfig,
)

/**
 * Encrypted Vault backup that ANY one cosigner can open with their own recovery words, so no extra
 * password has to be kept (ADR 0007). The contents are public keys, but knowing them reveals the
 * Vault's addresses and balance, so they are encrypted.
 *
 * Format: `magic "BPVB" | version (1) | network name length (1) | network name | count (1) |
 * count x (role (1) | AES-GCM(KEK_role, DEK)) | AES-GCM(DEK, VaultConfig record)`, where
 * `KEK_role = HKDF-SHA256(salt "bitpocket", seed entropy, info "bitpocket/vault-backup/v1")`.
 * Every envelope is bound to the header (and its role) as additional data.
 */
object VaultBackup {
    private val MAGIC = "BPVB".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val KEY_BYTES = 32
    private val WRAPPED_SIZE = AesGcmEnvelope.sealedSize(KEY_BYTES)

    /** Seals [contents] for each cosigner in [entropies] (seed entropy per role). */
    fun seal(
        contents: VaultBackupContents,
        entropies: Map<VaultRole, ByteArray>,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(entropies.isNotEmpty()) { "At least one cosigner" }
        val header = header(contents.network, entropies.size)
        val dek = ByteArray(KEY_BYTES).also(random::nextBytes)
        try {
            val wrapped =
                entropies.entries.sortedBy { it.key.id }.flatMap { (role, entropy) ->
                    val kek = backupKey(entropy)
                    try {
                        listOf(byteArrayOf(role.id), AesGcmEnvelope.seal(kek, dek, header + role.id, random))
                    } finally {
                        kek.wipe()
                    }
                }
            val payload = AesGcmEnvelope.seal(dek, contents.config.encode(), header, random)
            return (listOf(header) + wrapped + payload).reduce(ByteArray::plus)
        } finally {
            dek.wipe()
        }
    }

    /**
     * Opens [backup] with one cosigner's seed [entropy].
     * @return null when this seed is not one of the backup's cosigners.
     * @throws IllegalArgumentException for a file that is not a valid backup (or was tampered with).
     */
    fun open(
        backup: ByteArray,
        entropy: ByteArray,
    ): VaultBackupContents? {
        val buffer = ByteBuffer.wrap(backup)
        try {
            require(ByteArray(MAGIC.size).also { buffer.get(it) }.contentEquals(MAGIC)) { "Not a BitPocket vault backup" }
            require(buffer.get() == VERSION) { "Unsupported vault backup version" }
            val name = ByteArray(buffer.get().toInt() and 0xFF).also { buffer.get(it) }.toString(Charsets.US_ASCII)
            val network = requireNotNull(SupportedNetwork.entries.firstOrNull { it.name == name }) { "Unsupported network $name" }
            val count = buffer.get().toInt() and 0xFF
            require(count in 1..VaultRole.entries.size) { "Bad cosigner count" }
            val header = header(network, count)
            val wrapped = List(count) { buffer.get() to ByteArray(WRAPPED_SIZE).also { buffer.get(it) } }
            val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val kek = backupKey(entropy)
            val dek =
                try {
                    wrapped.firstNotNullOfOrNull { (role, envelope) -> tryOpen(kek, envelope, header + role) } ?: return null
                } finally {
                    kek.wipe()
                }
            try {
                return VaultBackupContents(network, VaultConfig.decode(AesGcmEnvelope.open(dek, payload, header)))
            } catch (e: AuthenticationFailedException) {
                throw IllegalArgumentException("Vault backup was modified", e)
            } finally {
                dek.wipe()
            }
        } catch (e: java.nio.BufferUnderflowException) {
            throw IllegalArgumentException("Truncated vault backup", e)
        }
    }

    /** HKDF-SHA256 (RFC 5869) of a cosigner's seed entropy into a 32-byte backup key. */
    internal fun backupKey(entropy: ByteArray): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(entropy, SALT, INFO))
        return ByteArray(KEY_BYTES).also { generator.generateBytes(it, 0, it.size) }
    }

    private fun tryOpen(
        kek: ByteArray,
        envelope: ByteArray,
        aad: ByteArray,
    ): ByteArray? =
        try {
            AesGcmEnvelope.open(kek, envelope, aad)
        } catch (e: AuthenticationFailedException) {
            null
        }

    private fun header(
        network: SupportedNetwork,
        count: Int,
    ): ByteArray {
        val name = network.name.toByteArray(Charsets.US_ASCII)
        return MAGIC + VERSION + name.size.toByte() + name + count.toByte()
    }

    private val SALT = "bitpocket".toByteArray(Charsets.US_ASCII)
    private val INFO = "bitpocket/vault-backup/v1".toByteArray(Charsets.US_ASCII)
}
