package io.github.brunogutierre.bitpocket.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/** Argon2id (RFC 9106, version 0x13) backed by Bouncy Castle. */
object Argon2idKdf : Kdf {
    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: KdfParams,
    ): ByteArray {
        val encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        encoded.array().wipe()
        return try {
            derive(passwordBytes, salt, params, KdfParams.KEY_BYTES)
        } finally {
            passwordBytes.wipe()
        }
    }

    internal fun derive(
        password: ByteArray,
        salt: ByteArray,
        params: KdfParams,
        outputBytes: Int,
        secret: ByteArray? = null,
        associatedData: ByteArray? = null,
    ): ByteArray {
        val builder =
            Argon2Parameters
                .Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKib)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
        secret?.let(builder::withSecret)
        associatedData?.let(builder::withAdditional)
        val generator = Argon2BytesGenerator().apply { init(builder.build()) }
        return ByteArray(outputBytes).also { generator.generateBytes(password, it) }
    }
}
