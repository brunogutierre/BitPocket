package io.github.brunogutierre.bitpocket.core.onboarding

import io.github.brunogutierre.bitpocket.core.crypto.wipe
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork

/**
 * The wallet being created or restored, shared by the onboarding screens. Memory only: it is
 * never persisted and is cleared once the wallet is saved or onboarding restarts.
 */
class OnboardingDraft {
    var network: SupportedNetwork = SupportedNetwork.DEFAULT

    var entropy: ByteArray? = null
        private set

    var words: List<String> = emptyList()
        private set

    fun setMnemonic(
        entropy: ByteArray,
        words: List<String>,
    ) {
        clear()
        this.entropy = entropy
        this.words = words
    }

    fun clear() {
        entropy?.wipe()
        entropy = null
        words = emptyList()
    }
}
