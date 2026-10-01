package io.github.brunogutierre.bitpocket.core.seed

import java.security.MessageDigest

/** Why a list of words is not a valid BIP39 mnemonic. */
sealed class MnemonicException(
    message: String,
) : Exception(message) {
    class InvalidWordCount(
        val count: Int,
    ) : MnemonicException("Unsupported word count $count")

    /** [positions] are 1-based. */
    class UnknownWords(
        val positions: List<Int>,
    ) : MnemonicException("Words not in the BIP39 list at $positions")

    class InvalidChecksum : MnemonicException("Checksum does not match")
}

/**
 * BIP39 (English) encoding between entropy and words. BDK's bindings do not expose the wordlist
 * or the entropy of a mnemonic, and both are needed here (entropy is what the slot stores).
 */
object Bip39 {
    val words: List<String> by lazy {
        val stream = checkNotNull(Bip39::class.java.getResourceAsStream("/bip39/english.txt")) { "BIP39 wordlist missing" }
        stream.bufferedReader().use { reader -> reader.readLines().filter { it.isNotBlank() } }.also { check(it.size == 2048) }
    }

    /** Word counts for 16, 20, 24, 28 and 32 bytes of entropy. */
    val WORD_COUNTS = listOf(12, 15, 18, 21, 24)

    /** Index of [word] in the (sorted) wordlist, or -1. */
    fun indexOf(word: String): Int = words.binarySearch(word).coerceAtLeast(-1)

    /** Up to [limit] wordlist entries starting with [prefix] (empty for an empty prefix). */
    fun suggestions(
        prefix: String,
        limit: Int = 4,
    ): List<String> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return words
            .asSequence()
            .filter { it.startsWith(normalized) }
            .take(limit)
            .toList()
    }

    fun toWords(entropy: ByteArray): List<String> {
        require(entropy.size in 16..32 && entropy.size % 4 == 0) { "entropy must be 16..32 bytes in steps of 4" }
        val checksumBits = entropy.size * 8 / 32
        val bits = BooleanArray(entropy.size * 8 + checksumBits)
        entropy.forEachBit { index, bit -> bits[index] = bit }
        sha256(entropy).forEachBit { index, bit -> if (index < checksumBits) bits[entropy.size * 8 + index] = bit }
        return (bits.indices step 11).map { start ->
            words[(0 until 11).fold(0) { acc, offset -> (acc shl 1) or (if (bits[start + offset]) 1 else 0) }]
        }
    }

    /** @throws MnemonicException if [mnemonic] is not a valid BIP39 mnemonic. */
    fun toEntropy(mnemonic: List<String>): ByteArray {
        if (mnemonic.size !in WORD_COUNTS) throw MnemonicException.InvalidWordCount(mnemonic.size)
        val indexes = mnemonic.map { indexOf(it.trim().lowercase()) }
        val unknown = indexes.withIndex().filter { it.value < 0 }.map { it.index + 1 }
        if (unknown.isNotEmpty()) throw MnemonicException.UnknownWords(unknown)

        val bits = BooleanArray(mnemonic.size * 11)
        indexes.forEachIndexed { word, index ->
            for (offset in 0 until 11) bits[word * 11 + offset] = (index shr (10 - offset)) and 1 == 1
        }
        val checksumBits = bits.size / 33
        val entropy = ByteArray((bits.size - checksumBits) / 8)
        for (i in 0 until entropy.size * 8) if (bits[i]) entropy[i / 8] = (entropy[i / 8].toInt() or (0x80 ushr (i % 8))).toByte()

        var valid = true
        sha256(entropy).forEachBit { index, bit -> if (index < checksumBits && bits[entropy.size * 8 + index] != bit) valid = false }
        if (!valid) {
            entropy.fill(0)
            throw MnemonicException.InvalidChecksum()
        }
        return entropy
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    private inline fun ByteArray.forEachBit(action: (index: Int, bit: Boolean) -> Unit) {
        for (i in 0 until size * 8) action(i, (this[i / 8].toInt() shr (7 - i % 8)) and 1 == 1)
    }
}
