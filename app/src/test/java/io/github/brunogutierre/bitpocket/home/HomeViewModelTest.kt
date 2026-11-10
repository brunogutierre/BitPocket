package io.github.brunogutierre.bitpocket.home

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.Balance
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val pocket = FakePocketWallet(SupportedNetwork.TESTNET4)
    private var locked = false

    private fun vm() = HomeViewModel(pocket, lockSession = { locked = true })

    @Test
    fun `shows the saved balance at once, then the synced one`() {
        pocket.balance = Balance(1_000, 0)
        pocket.balanceAfterSync = Balance(1_000, 500)
        val gate = CompletableDeferred<Unit>().also { pocket.syncGate = it }

        val vm = vm()

        assertEquals(HomeUiState(balance = Balance(1_000, 0), syncing = true), vm.state.value)
        gate.complete(Unit)
        assertEquals(HomeUiState(balance = Balance(1_000, 500)), vm.state.value)
        assertEquals(SupportedNetwork.TESTNET4, vm.network)
    }

    @Test
    fun `a failed sync keeps the saved balance and a retry clears the error`() {
        pocket.balance = Balance(2_000, 0)
        pocket.offline = true
        val vm = vm()

        assertEquals(HomeUiState(balance = Balance(2_000, 0), syncFailed = true), vm.state.value)

        pocket.offline = false
        vm.sync()

        assertFalse(vm.state.value.syncFailed)
        assertEquals(2, pocket.syncs)
    }

    @Test
    fun `history is read with the balance after each sync`() {
        val item = HistoryItem("ab", netSats = 5_000, feeSats = null, feeRateSatPerVb = null, confirmations = 0, timestampSec = 1)
        val gate = CompletableDeferred<Unit>().also { pocket.syncGate = it }
        val vm = vm()
        assertTrue(
            vm.state.value.history
                .isEmpty(),
        )

        pocket.historyItems = listOf(item)
        gate.complete(Unit)

        assertEquals(listOf(item), vm.state.value.history)
    }

    @Test
    fun `refreshing while a sync runs does not start another one`() {
        val gate = CompletableDeferred<Unit>().also { pocket.syncGate = it }
        val vm = vm()

        vm.sync()
        gate.complete(Unit)

        assertEquals(1, pocket.syncs)
        assertFalse(vm.state.value.syncing)
    }

    @Test
    fun `reload shows local changes without syncing`() {
        val vm = vm()
        pocket.balance = Balance(7, 0)

        vm.reload()

        assertEquals(Balance(7, 0), vm.state.value.balance)
        assertEquals(1, pocket.syncs)
    }

    @Test
    fun `shows and syncs the Vault once it exists`() {
        val vault = FakePocketWallet().apply { balance = Balance(70_000, 0) }
        var current: FakePocketWallet? = null
        val vm = HomeViewModel(pocket, {}, vault = { current })
        assertEquals(null, vm.state.value.vaultBalance)

        current = vault
        vm.sync()

        assertEquals(Balance(70_000, 0), vm.state.value.vaultBalance)
        assertEquals(1, vault.syncs)
        assertEquals(vault.heartbeat, vm.state.value.vaultHeartbeat)
    }

    @Test
    fun `lock locks the session`() {
        vm().lock()

        assertTrue(locked)
    }
}
