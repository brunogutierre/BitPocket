package io.github.brunogutierre.bitpocket.history

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class TransactionDetailsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val item = HistoryItem("ab12", netSats = -1_200, feeSats = 200, feeRateSatPerVb = 2, confirmations = 3, timestampSec = 10)
    private val pocket = FakePocketWallet(SupportedNetwork.TESTNET4).apply { historyItems = listOf(item) }

    @Test
    fun `loads the transaction and links it to the network explorer`() {
        val vm = TransactionDetailsViewModel(pocket, "ab12")

        assertEquals(TransactionDetailsUiState.Loaded(item), vm.state.value)
        assertEquals("https://mempool.space/testnet4/tx/ab12", vm.explorerUrl())
    }

    @Test
    fun `reports a transaction the wallet does not know`() {
        assertEquals(TransactionDetailsUiState.NotFound, TransactionDetailsViewModel(pocket, "ff").state.value)
    }
}
