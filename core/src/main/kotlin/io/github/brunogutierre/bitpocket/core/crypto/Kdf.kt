package io.github.brunogutierre.bitpocket.core.crypto

/** Argon2id cost parameters. Stored in each slot header so they can be tuned later. */
data class KdfParams(
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    init {
        // Upper bounds also protect against a tampered header forcing a huge (OOM) derivation.
        require(parallelism in 1..MAX_PARALLELISM) { "parallelism must be in 1..$MAX_PARALLELISM" }
        require(iterations in 1..MAX_ITERATIONS) { "iterations must be in 1..$MAX_ITERATIONS" }
        require(memoryKib in 8 * parallelism..MAX_MEMORY_KIB) { "memory must be 8 KiB per lane..256 MiB" }
    }

    companion object {
        /** Placeholder until the on-device benchmark picks values (target ~1 s for two runs). */
        val DEFAULT = KdfParams(memoryKib = 64 * 1024, iterations = 2, parallelism = 1)
        const val SALT_BYTES = 16
        const val KEY_BYTES = 32
        const val MAX_MEMORY_KIB = 256 * 1024
        const val MAX_ITERATIONS = 10
        const val MAX_PARALLELISM = 4
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
