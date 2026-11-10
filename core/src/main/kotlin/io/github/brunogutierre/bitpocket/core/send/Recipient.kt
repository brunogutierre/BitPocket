package io.github.brunogutierre.bitpocket.core.send

import io.github.brunogutierre.bitpocket.core.format.BitcoinFormat
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.bitcoindevkit.Address
import org.bitcoindevkit.AddressParseException
import java.math.BigDecimal

/** Where to send, from a pasted address or a BIP21 URI (which may also carry an amount). */
data class Recipient(
    val address: String,
    val amountSats: Long? = null,
    val label: String? = null,
)

enum class RecipientError {
    EMPTY,
    NOT_AN_ADDRESS,

    /** A valid address of another network (e.g. mainnet `bc1...`): never sendable here. */
    WRONG_NETWORK,

    /** A BIP21 URI with a malformed amount or an unknown required (`req-`) parameter. */
    BAD_URI,
}

sealed interface RecipientResult {
    data class Valid(
        val recipient: Recipient,
    ) : RecipientResult

    data class Invalid(
        val error: RecipientError,
    ) : RecipientResult
}

object RecipientParser {
    private const val SCHEME = "bitcoin:"

    fun parse(
        input: String,
        network: SupportedNetwork,
    ): RecipientResult {
        val text = input.trim()
        if (text.isEmpty()) return RecipientResult.Invalid(RecipientError.EMPTY)
        if (!text.startsWith(SCHEME, ignoreCase = true)) return checkAddress(text, network, amount = null, label = null)

        val body = text.substring(SCHEME.length)
        val address = body.substringBefore('?')
        val params =
            parseQuery(body.substringAfter('?', missingDelimiterValue = "")) ?: return RecipientResult.Invalid(RecipientError.BAD_URI)
        if (params.keys.any { it.startsWith("req-") }) return RecipientResult.Invalid(RecipientError.BAD_URI)
        val amount =
            params["amount"]?.let { btcToSats(it) ?: return RecipientResult.Invalid(RecipientError.BAD_URI) }
        return checkAddress(address, network, amount, params["label"])
    }

    /** Validates against the wallet network; bech32 addresses are normalized to lowercase. */
    private fun checkAddress(
        text: String,
        network: SupportedNetwork,
        amount: Long?,
        label: String?,
    ): RecipientResult =
        try {
            val normalized = Address(text, network.toBdkNetwork()).use { it.toString() }
            RecipientResult.Valid(Recipient(normalized, amount, label))
        } catch (e: AddressParseException.NetworkValidation) {
            RecipientResult.Invalid(RecipientError.WRONG_NETWORK)
        } catch (e: AddressParseException) {
            RecipientResult.Invalid(RecipientError.NOT_AN_ADDRESS)
        }

    /** `a=1&b=x%20y` -> map; null when a key repeats or a value is not valid percent-encoding. */
    private fun parseQuery(query: String): Map<String, String>? {
        if (query.isEmpty()) return emptyMap()
        val pairs =
            query.split('&').filter { it.isNotEmpty() }.map { part ->
                val key = part.substringBefore('=')
                val value = part.substringAfter('=', missingDelimiterValue = "")
                key to (runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8) }.getOrNull() ?: return null)
            }
        return pairs.toMap().takeIf { it.size == pairs.size }
    }

    /** BIP21 amounts are decimal BTC with at most 8 decimals, and must be positive. */
    private fun btcToSats(text: String): Long? {
        if (!text.matches(Regex("""\d+(\.\d{1,8})?"""))) return null
        val sats = BigDecimal(text).movePointRight(8)
        return sats.longValueExact().takeIf { it > 0 && it <= 21_000_000L * BitcoinFormat.SATS_PER_BTC }
    }
}
