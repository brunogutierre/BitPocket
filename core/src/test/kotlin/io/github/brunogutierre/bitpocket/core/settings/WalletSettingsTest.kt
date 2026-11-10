package io.github.brunogutierre.bitpocket.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalletSettingsTest {
    @Test
    fun `round-trips every auto-lock option with stable ids`() {
        AutoLock.entries.forEach { option ->
            assertEquals(WalletSettings(option), WalletSettings.decode(WalletSettings(option).encode()))
        }
        assertEquals(listOf<Byte>(0, 1, 2, 3), AutoLock.entries.map { it.id })
        assertEquals(AutoLock.ONE_MINUTE, WalletSettings().autoLock)
    }

    @Test
    fun `skips unknown records and defaults missing ones`() {
        val unknown = byteArrayOf(99, 0, 2, 7, 7)

        assertEquals(
            WalletSettings(AutoLock.THIRTY_SECONDS),
            WalletSettings.decode(unknown + WalletSettings(AutoLock.THIRTY_SECONDS).encode()),
        )
        assertEquals(WalletSettings(), WalletSettings.decode(ByteArray(0)))
    }

    @Test
    fun `rejects truncated or invalid records`() {
        assertThrows<IllegalArgumentException> { WalletSettings.decode(byteArrayOf(1, 0, 5, 2)) }
        assertThrows<IllegalArgumentException> { WalletSettings.decode(byteArrayOf(1, 0, 2, 2, 2)) }
    }
}
