package io.github.brunogutierre.bitpocket.core.crypto

import java.security.SecureRandom

/**
 * Seals and opens slot files. Each file is stored as `KeyWrapper.wrap(SlotFile)`, so all of
 * its bytes change on every write.
 *
 * Every save rewrites BOTH slots in the same order (A, then B): the saved slot with new
 * content, the other one re-wrapped (or regenerated as a random decoy if it does not exist),
 * so neither order, sizes, timestamps nor bytes reveal which slot is in use.
 */
class SlotStore(
    private val storage: SlotStorage,
    private val kdf: Kdf,
    private val keyWrapper: KeyWrapper,
    private val params: KdfParams = KdfParams.DEFAULT,
    private val random: SecureRandom = SecureRandom(),
) {
    fun save(
        slot: SlotId,
        pin: CharArray,
        payload: ByteArray,
    ) {
        val padded = SlotFile.pad(payload)
        val header = SlotHeader(params, randomBytes(KdfParams.SALT_BYTES))
        val aad = aadOf(slot, header)
        val pinKey = kdf.deriveKey(pin, header.salt, params)
        val dek = randomBytes(SlotFile.DEK_BYTES)
        try {
            val file =
                SlotFile(
                    header,
                    AesGcmEnvelope.seal(pinKey, dek, aad, random),
                    AesGcmEnvelope.seal(dek, padded, aad, random),
                )
            val sealed = SlotId.entries.associateWith { if (it == slot) wrap(file.encode()) else rewrap(it) }
            SlotId.entries.forEach { storage.write(it, sealed.getValue(it)) }
        } finally {
            padded.wipe()
            pinKey.wipe()
            dek.wipe()
        }
    }

    /** The slot file, or null if missing, not wrapped by this device, or malformed. */
    fun read(slot: SlotId): SlotFile? {
        val plain = storage.read(slot)?.let(::unwrapOrNull) ?: return null
        return try {
            SlotFile.decode(plain)
        } catch (e: IllegalArgumentException) {
            null
        } finally {
            plain.wipe()
        }
    }

    /**
     * Returns the slot DEK if [pin] opens [file], else null. Always runs the KDF once (with the
     * configured params for a missing file), so callers can keep the work constant across slots.
     */
    fun openDek(
        slot: SlotId,
        file: SlotFile?,
        pin: CharArray,
    ): ByteArray? {
        val header = file?.header ?: SlotHeader(params, ByteArray(KdfParams.SALT_BYTES))
        val pinKey = kdf.deriveKey(pin, header.salt, header.params)
        return try {
            file?.let { AesGcmEnvelope.open(pinKey, it.dekEnvelope, aadOf(slot, header)) }
        } catch (e: AuthenticationFailedException) {
            null
        } finally {
            pinKey.wipe()
        }
    }

    /** @throws AuthenticationFailedException if the payload was tampered with. */
    fun openPayload(
        slot: SlotId,
        file: SlotFile,
        dek: ByteArray,
    ): ByteArray {
        val padded = AesGcmEnvelope.open(dek, file.payload, aadOf(slot, file.header))
        return try {
            SlotFile.unpad(padded)
        } finally {
            padded.wipe()
        }
    }

    /** Re-wraps an untouched slot (fresh wrapping nonce), or generates a new random decoy. */
    private fun rewrap(slot: SlotId): ByteArray {
        val plain = storage.read(slot)?.let(::unwrapOrNull) ?: decoy()
        return wrap(plain)
    }

    private fun wrap(plain: ByteArray): ByteArray =
        try {
            keyWrapper.wrap(plain)
        } finally {
            plain.wipe()
        }

    private fun unwrapOrNull(wrapped: ByteArray): ByteArray? =
        try {
            keyWrapper.unwrap(wrapped)
        } catch (e: AuthenticationFailedException) {
            null
        }

    /** Same layout and size as a real slot file, filled with random bytes. */
    private fun decoy(): ByteArray =
        SlotFile(
            SlotHeader(params, randomBytes(KdfParams.SALT_BYTES)),
            versioned(SlotFile.DEK_ENVELOPE_SIZE),
            versioned(SlotFile.PAYLOAD_ENVELOPE_SIZE),
        ).encode()

    private fun versioned(size: Int) = randomBytes(size).also { it[0] = AesGcmEnvelope.VERSION }

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private fun aadOf(
        slot: SlotId,
        header: SlotHeader,
    ) = header.encode() + slot.dirName.toByteArray()
}
