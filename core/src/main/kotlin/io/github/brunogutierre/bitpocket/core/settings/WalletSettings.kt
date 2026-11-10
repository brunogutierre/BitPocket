package io.github.brunogutierre.bitpocket.core.settings

import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import java.nio.ByteBuffer

/** How long BitPocket may stay in the background before it locks. Ids are stored: never reuse. */
enum class AutoLock(
    val id: Byte,
    val seconds: Long,
) {
    IMMEDIATELY(0, 0),
    THIRTY_SECONDS(1, 30),
    ONE_MINUTE(2, 60),
    FIVE_MINUTES(3, 300),
    ;

    companion object {
        val DEFAULT = ONE_MINUTE

        fun of(id: Byte): AutoLock? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Per-wallet security settings. They live in the encrypted slot payload and change only after the
 * PIN is entered again (ADR 0005), so they are authenticated and each wallet (real or decoy) keeps
 * its own.
 */
data class WalletSettings(
    val autoLock: AutoLock = AutoLock.DEFAULT,
    val guardrails: Guardrails = Guardrails(),
    /** A loosening of [guardrails] waiting for its activation height (ADR 0006). */
    val scheduledGuardrails: ScheduledGuardrails? = null,
    /** The Vault pocket's public keys and timelock, once set up (ADR 0007). */
    val vault: VaultConfig? = null,
    /** Whether a duress PIN was set up from this wallet (ADR 0009). */
    val duress: Boolean = false,
    /** A custom Esplora server (https), or null for the network's default (ADR 0010). */
    val esploraUrl: String? = null,
) {
    /**
     * Tag-length-value records: `tag (1) | length (2) | value`. Unknown tags are skipped when
     * decoding, so a newer app can add settings without breaking older payloads.
     */
    fun encode(): ByteArray {
        val scheduled =
            scheduledGuardrails?.let {
                record(TAG_SCHEDULED_GUARDRAILS, it.guardrails.encode() + ByteBuffer.allocate(4).putInt(it.activatesAtHeight).array())
            } ?: ByteArray(0)
        val vaultRecord = vault?.let { record(TAG_VAULT, it.encode()) } ?: ByteArray(0)
        val duressRecord = if (duress) record(TAG_DURESS, byteArrayOf(1)) else ByteArray(0)
        val serverRecord = esploraUrl?.let { record(TAG_ESPLORA_URL, it.toByteArray(Charsets.UTF_8)) } ?: ByteArray(0)
        return record(TAG_AUTO_LOCK, byteArrayOf(autoLock.id)) + record(TAG_GUARDRAILS, guardrails.encode()) + scheduled + vaultRecord +
            duressRecord + serverRecord
    }

    companion object {
        private const val TAG_AUTO_LOCK: Byte = 1
        private const val TAG_GUARDRAILS: Byte = 2
        private const val TAG_SCHEDULED_GUARDRAILS: Byte = 3
        private const val TAG_VAULT: Byte = 4
        private const val TAG_DURESS: Byte = 5
        private const val TAG_ESPLORA_URL: Byte = 6

        /** @throws IllegalArgumentException for malformed records or values. */
        fun decode(bytes: ByteArray): WalletSettings {
            var settings = WalletSettings()
            val buffer = ByteBuffer.wrap(bytes)
            try {
                while (buffer.hasRemaining()) {
                    val tag = buffer.get()
                    val value = ByteArray(buffer.short.toInt() and 0xFFFF).also { buffer.get(it) }
                    settings =
                        when (tag) {
                            TAG_AUTO_LOCK -> {
                                require(value.size == 1) { "Bad auto-lock record" }
                                settings.copy(autoLock = requireNotNull(AutoLock.of(value[0])) { "Unknown auto-lock id" })
                            }

                            TAG_GUARDRAILS -> {
                                require(value.size == Guardrails.ENCODED_SIZE) { "Bad guardrails record" }
                                settings.copy(guardrails = Guardrails.decode(ByteBuffer.wrap(value)))
                            }

                            TAG_SCHEDULED_GUARDRAILS -> {
                                require(value.size == Guardrails.ENCODED_SIZE + 4) { "Bad scheduled guardrails record" }
                                val buffer = ByteBuffer.wrap(value)
                                settings.copy(scheduledGuardrails = ScheduledGuardrails(Guardrails.decode(buffer), buffer.int))
                            }

                            TAG_ESPLORA_URL -> {
                                settings.copy(esploraUrl = value.toString(Charsets.UTF_8))
                            }

                            TAG_DURESS -> {
                                settings.copy(duress = value.singleOrNull() == 1.toByte())
                            }

                            TAG_VAULT -> {
                                settings.copy(vault = VaultConfig.decode(value))
                            }

                            else -> {
                                settings
                            }
                        }
                }
            } catch (e: java.nio.BufferUnderflowException) {
                throw IllegalArgumentException("Truncated settings", e)
            }
            return settings
        }

        private fun record(
            tag: Byte,
            value: ByteArray,
        ): ByteArray =
            ByteBuffer
                .allocate(3 + value.size)
                .put(tag)
                .putShort(value.size.toShort())
                .put(value)
                .array()
    }
}
