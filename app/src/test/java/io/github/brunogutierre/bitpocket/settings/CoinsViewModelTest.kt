package io.github.brunogutierre.bitpocket.settings

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.wallet.Coin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CoinsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    @Test
    fun `lists coins and freezes one`() {
        val coin = Coin("a".repeat(64) + ":0", 1_000, 2, frozen = false)
        val pocket = FakePocketWallet().apply { coinList = listOf(coin) }
        val vm = CoinsViewModel(pocket)
        assertEquals(listOf(coin), vm.coins.value)

        vm.setFrozen(coin, true)

        assertEquals(listOf(coin.copy(frozen = true)), vm.coins.value)
    }
}
