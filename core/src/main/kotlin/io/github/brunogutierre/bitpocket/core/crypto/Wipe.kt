package io.github.brunogutierre.bitpocket.core.crypto

/** Best-effort erasure of secrets in memory (the JVM may still hold copies). */
fun ByteArray.wipe() = fill(0)

fun CharArray.wipe() = fill('\u0000')
