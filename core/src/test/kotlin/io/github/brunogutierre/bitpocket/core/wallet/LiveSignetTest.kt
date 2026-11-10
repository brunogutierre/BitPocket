package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.SecureRandom
import kotlin.time.Duration.Companion.minutes

/** Talks to the public Signet Esplora server: run with `./gradlew :core:test -Plive`, never in CI. */
@Tag("live")
class LiveSignetTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `full scan then incremental sync against Signet Esplora`() =
        runTest(timeout = 3.minutes) {
            val network = SupportedNetwork.SIGNET
            val descriptors = SpendingDescriptors.fromEntropy(ByteArray(16).also(SecureRandom()::nextBytes), network).use { it.toPublic() }

            BdkWallet.open(dir, descriptors, network, EsploraChainClient(network.esploraBaseUrl)).use { wallet ->
                wallet.sync()
                wallet.sync()
                // A fresh random wallet has no history (the public "abandon ... about" one has too much).
                assertEquals(Balance(0, 0), wallet.balance())
                val fees = wallet.feeRates()
                assertTrue(fees.fast >= fees.normal && fees.normal >= fees.slow && fees.slow >= 1, fees.toString())
            }
        }
}
