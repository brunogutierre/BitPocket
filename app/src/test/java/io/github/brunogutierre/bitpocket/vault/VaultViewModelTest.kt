package io.github.brunogutierre.bitpocket.vault

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class VaultViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val config =
        VaultConfig("[00000000/48'/1'/0'/2']tpubA", "[00000000/48'/1'/0'/2']tpubB", "[00000000/48'/1'/0'/2']tpubC", HeirTimelock.DAY)

    @Test
    fun `shows the Vault balance and history and reloads them`() {
        val item = HistoryItem("ab", 5_000, null, null, 3, 1)
        val vault = FakePocketWallet().apply { balance = Balance(5_000, 0) }
        val vm = VaultViewModel(vault, config)
        assertEquals(Balance(5_000, 0), vm.state.value.balance)

        vault.historyItems = listOf(item)
        vm.reload()

        assertEquals(listOf(item), vm.state.value.history)
        assertEquals(vault.heartbeat, vm.state.value.heartbeat)
        assertEquals(HeirTimelock.DAY, vm.config.timelock)
    }
}
