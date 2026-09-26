package io.github.brunogutierre.bitpocket.core.seed

import java.security.SecureRandom
import kotlin.random.Random

/** A freshly generated mnemonic: [entropy] is what the slot stores, [words] are shown once. */
class GeneratedMnemonic(
    val entropy: ByteArray,
    val words: List<String>,
)

sealed interface RestoreResult {
    class Valid(
        val entropy: ByteArray,
    ) : RestoreResult

    data class InvalidWordCount(
        val count: Int,
    ) : RestoreResult

    /** [positions] are 1-based. */
    data class UnknownWords(
        val positions: List<Int>,
    ) : RestoreResult

    data object InvalidChecksum : RestoreResult
}

/** "Which is word #[position]?": [options] contains [answer] plus distinct decoys. */
data class WordChallenge(
    val position: Int,
    val options: List<String>,
    val answer: String,
)

/** Creates, restores and verifies BIP39 mnemonics for the Spending wallet. */
class MnemonicService(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    /** A new 12-word mnemonic (128 bits of entropy). */
    fun create(): GeneratedMnemonic {
        val entropy = ByteArray(CREATE_ENTROPY_BYTES).also(secureRandom::nextBytes)
        return GeneratedMnemonic(entropy, Bip39.toWords(entropy))
    }

    /** Restores 12 or 24 words; any other count is rejected even if BIP39 allows it. */
    fun restore(words: List<String>): RestoreResult {
        if (words.size !in RESTORE_WORD_COUNTS) return RestoreResult.InvalidWordCount(words.size)
        return try {
            RestoreResult.Valid(Bip39.toEntropy(words))
        } catch (e: MnemonicException.UnknownWords) {
            RestoreResult.UnknownWords(e.positions)
        } catch (e: MnemonicException.InvalidChecksum) {
            RestoreResult.InvalidChecksum
        }
    }

    /**
     * [count] challenges at distinct random positions (ascending), each with [optionCount]
     * distinct shuffled options. [random] is injectable so tests are deterministic.
     */
    fun challenges(
        words: List<String>,
        random: Random = Random.Default,
        count: Int = 3,
        optionCount: Int = 4,
    ): List<WordChallenge> =
        words.indices.shuffled(random).take(count).sorted().map { index ->
            val answer = words[index]
            val decoys =
                Bip39.words
                    .filter { it != answer }
                    .shuffled(random)
                    .take(optionCount - 1)
            WordChallenge(position = index + 1, options = (decoys + answer).shuffled(random), answer = answer)
        }

    fun suggestions(prefix: String): List<String> = Bip39.suggestions(prefix)

    private companion object {
        const val CREATE_ENTROPY_BYTES = 16
        val RESTORE_WORD_COUNTS = setOf(12, 24)
    }
}
