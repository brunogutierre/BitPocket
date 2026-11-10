package io.github.brunogutierre.bitpocket.history

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.labels.LabelStore
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.HistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.io.File

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
    fun `edits and saves the transaction label`(
        @TempDir dir: File,
    ) {
        val store = LabelStore(File(dir, "labels.jsonl"))
        store.setLabel(LabelStore.TX, "ab12", "Rent")
        val vm = TransactionDetailsViewModel(pocket, "ab12", store)
        assertEquals("Rent", vm.label.value)

        vm.onLabelChange("Rent, November")
        vm.saveLabel()

        val saved =
            (1..50).firstNotNullOfOrNull {
                Thread.sleep(20)
                LabelStore(File(dir, "labels.jsonl")).label(LabelStore.TX, "ab12")?.takeIf {
                    it !=
                        "Rent"
                }
            }
        assertEquals("Rent, November", saved)
    }

    @Test
    fun `reports a transaction the wallet does not know`() {
        assertEquals(TransactionDetailsUiState.NotFound, TransactionDetailsViewModel(pocket, "ff").state.value)
    }
}
