package io.github.brunogutierre.bitpocket.core.crypto

import java.security.SecureRandom

/**
 * Seals and opens slot files. Every save rewrites BOTH slots (the other one unchanged, or a
 * random decoy if it does not exist yet), so file sizes and timestamps do not reveal which
 * slot is in use.
 */
class SlotStore(
    private val storage: SlotStorage,
    private val kdf: Kdf,
    private val keyWrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) {
    fun save(
        slot: SlotId,
        pin: CharArray,
        payload: ByteArray,
        params: KdfParams = KdfParams.DEFAULT,
    ) {
        val header = SlotHeader(params, randomBytes(KdfParams.SALT_BYTES))
        val aad = aadOf(slot, header)
        val pinKey = kdf.deriveKey(pin, header.salt, params)
        val dek = randomBytes(DEK_BYTES)
        val padded = SlotFile.pad(payload)
        try {
            val wrappedDek = keyWrapper.wrap(AesGcmEnvelope.seal(pinKey, dek, aad, random))
            val sealedPayload = AesGcmEnvelope.seal(dek, padded, aad, random)
            val other = SlotId.entries.single { it != slot }
            val otherBytes = storage.read(other) ?: decoy(params).encode()
            storage.write(slot, SlotFile(header, wrappedDek, sealedPayload).encode())
            storage.write(other, otherBytes)
        } finally {
            pinKey.wipe()
            dek.wipe()
            padded.wipe()
        }
    }

    /** The slot file, or null if missing or unreadable. */
    fun read(slot: SlotId): SlotFile? = storage.read(slot)?.let { runCatching { SlotFile.decode(it) }.getOrNull() }

    /**
     * Returns the slot DEK if [pin] opens [file], else null. Always runs the KDF once, even for a
     * missing file, so callers can keep the work constant across slots.
     */
    fun openDek(
        slot: SlotId,
        file: SlotFile?,
        pin: CharArray,
    ): ByteArray? {
        val header = file?.header ?: SlotHeader(KdfParams.DEFAULT, ByteArray(KdfParams.SALT_BYTES))
        val pinKey = kdf.deriveKey(pin, header.salt, header.params)
        return try {
            file?.let {
                runCatching {
                    val inner = keyWrapper.unwrap(it.wrappedDek)
                    try {
                        AesGcmEnvelope.open(pinKey, inner, aadOf(slot, header))
                    } finally {
                        inner.wipe()
                    }
                }.getOrNull()
            }
        } finally {
            pinKey.wipe()
        }
    }

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

    /** Same layout and sizes as a real slot, filled with random bytes. */
    private fun decoy(params: KdfParams): SlotFile {
        val innerSize = AesGcmEnvelope.sealedSize(DEK_BYTES)
        return SlotFile(
            header = SlotHeader(params, randomBytes(KdfParams.SALT_BYTES)),
            wrappedDek = keyWrapper.wrap(versioned(innerSize)),
            payload = versioned(SlotFile.PAYLOAD_ENVELOPE_SIZE),
        )
    }

    private fun versioned(size: Int) = randomBytes(size).also { it[0] = AesGcmEnvelope.VERSION }

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private fun aadOf(
        slot: SlotId,
        header: SlotHeader,
    ) = header.encode() + slot.dirName.toByteArray()

    private companion object {
        const val DEK_BYTES = 32
    }
}
