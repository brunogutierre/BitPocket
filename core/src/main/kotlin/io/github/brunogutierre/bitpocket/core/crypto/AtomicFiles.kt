package io.github.brunogutierre.bitpocket.core.crypto

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Replaces [target] atomically: writes an fsync'd temp file, then renames it over the target. */
fun writeAtomically(
    target: File,
    bytes: ByteArray,
) {
    target.absoluteFile.parentFile.mkdirs()
    val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp")
    FileOutputStream(temp).use {
        it.write(bytes)
        it.fd.sync() // durable before the rename replaces the old file
    }
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
