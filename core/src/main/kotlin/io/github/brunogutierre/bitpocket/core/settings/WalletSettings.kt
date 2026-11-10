package io.github.brunogutierre.bitpocket.core.settings

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
) {
    /**
     * Tag-length-value records: `tag (1) | length (2) | value`. Unknown tags are skipped when
     * decoding, so a newer app can add settings without breaking older payloads.
     */
    fun encode(): ByteArray = record(TAG_AUTO_LOCK, byteArrayOf(autoLock.id))

    companion object {
        private const val TAG_AUTO_LOCK: Byte = 1

        /** @throws IllegalArgumentException for malformed records or values. */
        fun decode(bytes: ByteArray): WalletSettings {
            var settings = WalletSettings()
            val buffer = ByteBuffer.wrap(bytes)
            try {
                while (buffer.hasRemaining()) {
                    val tag = buffer.get()
                    val value = ByteArray(buffer.short.toInt() and 0xFFFF).also { buffer.get(it) }
                    if (tag == TAG_AUTO_LOCK) {
                        require(value.size == 1) { "Bad auto-lock record" }
                        settings = settings.copy(autoLock = requireNotNull(AutoLock.of(value[0])) { "Unknown auto-lock id" })
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
