package io.github.brunogutierre.bitpocket.core.crypto

/** Argon2id cost parameters. Stored in each slot header so they can be tuned later. */
data class KdfParams(
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    init {
        require(parallelism in 1..255) { "parallelism must be in 1..255" }
        require(iterations >= 1) { "iterations must be >= 1" }
        require(memoryKib >= 8 * parallelism) { "memory must be >= 8 KiB per lane" }
    }

    companion object {
        /** Placeholder until the on-device benchmark picks values (target ~1 s for two runs). */
        val DEFAULT = KdfParams(memoryKib = 64 * 1024, iterations = 2, parallelism = 1)
        const val SALT_BYTES = 16
        const val KEY_BYTES = 32
    }
}

/** Derives a symmetric key from a user secret (PIN). */
fun interface Kdf {
    /** Returns a new [KdfParams.KEY_BYTES]-byte key. Does not modify [password]. */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: KdfParams,
    ): ByteArray
}
