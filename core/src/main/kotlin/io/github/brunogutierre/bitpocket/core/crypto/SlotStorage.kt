package io.github.brunogutierre.bitpocket.core.crypto

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Raw persistence of slot files. */
interface SlotStorage {
    fun read(slot: SlotId): ByteArray?

    fun write(
        slot: SlotId,
        bytes: ByteArray,
    )
}

/** Files at `<root>/slots/{a,b}/slot.bin`, replaced atomically (fsync'd temp file + rename). */
class FileSlotStorage(
    private val root: File,
) : SlotStorage {
    private fun fileOf(slot: SlotId) = File(root, "slots/${slot.dirName}/$FILE_NAME")

    override fun read(slot: SlotId): ByteArray? = fileOf(slot).takeIf { it.isFile }?.readBytes()

    override fun write(
        slot: SlotId,
        bytes: ByteArray,
    ) {
        val target = fileOf(slot)
        target.parentFile.mkdirs()
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(temp).use {
            it.write(bytes)
            it.fd.sync() // durable before the rename replaces the old file
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val FILE_NAME = "slot.bin"
    }
}
