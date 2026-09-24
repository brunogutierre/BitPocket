package io.github.brunogutierre.bitpocket.core.crypto

import java.io.File

/** Raw persistence of slot files. */
interface SlotStorage {
    fun read(slot: SlotId): ByteArray?

    fun write(
        slot: SlotId,
        bytes: ByteArray,
    )
}

/** Files at `<root>/slots/{a,b}/slot.bin`, replaced atomically ([writeAtomically]). */
class FileSlotStorage(
    private val root: File,
) : SlotStorage {
    private fun fileOf(slot: SlotId) = File(root, "slots/${slot.dirName}/slot.bin")

    override fun read(slot: SlotId): ByteArray? = fileOf(slot).takeIf { it.isFile }?.readBytes()

    override fun write(
        slot: SlotId,
        bytes: ByteArray,
    ) = writeAtomically(fileOf(slot), bytes)
}
