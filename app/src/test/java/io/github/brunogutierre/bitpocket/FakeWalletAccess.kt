package io.github.brunogutierre.bitpocket

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.session.SeedOutcome
import io.github.brunogutierre.bitpocket.core.session.UnlockOutcome
import io.github.brunogutierre.bitpocket.core.session.WalletAccess

class FakeWalletAccess : WalletAccess {
    var createdPin: String? = null
    var createdNetwork: SupportedNetwork? = null
    var failCreate = false
    val outcomes = ArrayDeque<UnlockOutcome>()
    val attempts = mutableListOf<String>()

    override fun create(
        pin: CharArray,
        seedEntropy: ByteArray,
        network: SupportedNetwork,
    ) {
        check(!failCreate) { "keystore unavailable" }
        createdPin = String(pin)
        createdNetwork = network
    }

    override fun unlock(pin: CharArray): UnlockOutcome {
        attempts += String(pin)
        return outcomes.removeFirst()
    }

    /** Answers for [seedForSigning], in order. */
    val seedOutcomes = ArrayDeque<SeedOutcome>()
    val signingPins = mutableListOf<String>()

    override fun seedForSigning(pin: CharArray): SeedOutcome {
        signingPins += String(pin)
        return seedOutcomes.removeFirst()
    }
}
