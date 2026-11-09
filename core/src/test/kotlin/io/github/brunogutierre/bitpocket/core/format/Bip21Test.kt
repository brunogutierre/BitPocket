package io.github.brunogutierre.bitpocket.core.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Bip21Test {
    private val address = "tb1p8wpt9v4frpf3tkn0srd97pksgsxc5hs52lafxwru9kgeephvs7rqlqt9zj"

    @Test
    fun `share URI keeps the address as is`() {
        assertEquals("bitcoin:$address", Bip21.uri(address))
    }

    @Test
    fun `QR URI is fully uppercase for alphanumeric mode`() {
        val uri = Bip21.qrUri(address)

        assertEquals("BITCOIN:TB1P8WPT9V4FRPF3TKN0SRD97PKSGSXC5HS52LAFXWRU9KGEEPHVS7RQLQT9ZJ", uri)
        assertEquals(uri.uppercase(), uri)
    }
}
