package io.github.brunogutierre.bitpocket.core.send

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RecipientParserTest {
    private fun parse(
        input: String,
        network: SupportedNetwork = SupportedNetwork.SIGNET,
    ) = RecipientParser.parse(input, network)

    private fun valid(
        address: String = TAPROOT,
        amount: Long? = null,
        label: String? = null,
    ) = RecipientResult.Valid(Recipient(address, amount, label))

    @Test
    fun `accepts plain test-network addresses on both supported networks`() {
        assertEquals(valid(), parse("  $TAPROOT \n"))
        assertEquals(valid(SEGWIT), parse(SEGWIT, SupportedNetwork.TESTNET4))
    }

    @Test
    fun `normalizes an uppercase bech32 address`() {
        assertEquals(valid(), parse(TAPROOT.uppercase()))
    }

    @Test
    fun `reads amount and label from a BIP21 URI`() {
        assertEquals(valid(amount = 150_000, label = "Coffee shop"), parse("bitcoin:$TAPROOT?amount=0.0015&label=Coffee%20shop"))
        assertEquals(valid(), parse("BITCOIN:${TAPROOT.uppercase()}"))
        assertEquals(valid(amount = 1), parse("bitcoin:$TAPROOT?amount=0.00000001&message=thanks"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "bitcoin:$TAPROOT?amount=0.123456789",
            "bitcoin:$TAPROOT?amount=-1",
            "bitcoin:$TAPROOT?amount=0",
            "bitcoin:$TAPROOT?amount=1e3",
            "bitcoin:$TAPROOT?amount=1&amount=2",
            "bitcoin:$TAPROOT?req-somethingnew=1",
            "bitcoin:$TAPROOT?label=%zz",
        ],
    )
    fun `rejects malformed BIP21 URIs`(uri: String) {
        assertEquals(RecipientResult.Invalid(RecipientError.BAD_URI), parse(uri))
    }

    @ParameterizedTest
    @CsvSource(
        // Mainnet addresses (BIP173/BIP86 vectors): valid Bitcoin, wrong network.
        "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4, WRONG_NETWORK",
        "bc1p5cyxnuxmeuwuvkwfem96lqzszd02n6xdcjrs20cac6yqjjwudpxqkedrcr, WRONG_NETWORK",
        "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2, WRONG_NETWORK",
        "not an address, NOT_AN_ADDRESS",
        "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsy, NOT_AN_ADDRESS",
    )
    fun `rejects other networks and garbage with a distinct reason`(
        input: String,
        error: RecipientError,
    ) {
        assertEquals(RecipientResult.Invalid(error), parse(input))
    }

    @Test
    fun `empty input is reported as such`() {
        assertEquals(RecipientResult.Invalid(RecipientError.EMPTY), parse("   "))
    }

    private companion object {
        const val TAPROOT = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"

        // BIP173 testnet P2WPKH vector.
        const val SEGWIT = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
    }
}
