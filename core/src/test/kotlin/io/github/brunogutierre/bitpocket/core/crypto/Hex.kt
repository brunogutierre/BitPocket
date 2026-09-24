package io.github.brunogutierre.bitpocket.core.crypto

@OptIn(ExperimentalStdlibApi::class)
fun String.hex(): ByteArray = replace(" ", "").hexToByteArray()

@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.hex(): String = toHexString()
