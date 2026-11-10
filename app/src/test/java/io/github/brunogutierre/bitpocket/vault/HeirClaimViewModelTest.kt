package io.github.brunogutierre.bitpocket.vault

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.seed.Bip39
import io.github.brunogutierre.bitpocket.core.send.SpendPlan
import io.github.brunogutierre.bitpocket.core.vault.HeirClaimOpen
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.wallet.HeirCoin
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HeirClaimViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val heirEntropy = ByteArray(16) { 3 }
    private val words = Bip39.toWords(heirEntropy).joinToString(" ")
    private val config =
        VaultConfig("[00000000/48'/1'/0'/2']tpubA", "[00000000/48'/1'/0'/2']tpubB", "[00000000/48'/1'/0'/2']tpubC", HeirTimelock.HOUR)
    private val vault = FakePocketWallet()
    private var answer: HeirClaimOpen = HeirClaimOpen.Opened(vault, config)
    private val seen = mutableListOf<ByteArray>()
    private val vm by lazy {
        HeirClaimViewModel({ _, seed ->
            seen += seed.copyOf()
            answer
        }, workDispatcher = main.dispatcher)
    }

    @Test
    fun `backup, heir words, scan, then a sweep of the matured coins`() {
        vault.coins = listOf(HeirCoin(60_000, 10, 0), HeirCoin(40_000, 2, 4))
        vault.plans += SpendPlan("cHNidP8=", ADDRESS, 59_700, 300, 1, 0, 1)

        vm.onBackupPicked(byteArrayOf(1))
        vm.onWordsChange(words.uppercase())
        vm.submitWords()

        assertArrayEquals(heirEntropy, seen.single())
        assertEquals(HeirClaimStage.COINS, vm.state.value.stage)
        assertEquals(60_000, vm.state.value.maturedSats)
        assertEquals(1, vault.syncs)

        vm.onAddressChange(ADDRESS)
        vm.review()
        assertEquals(HeirClaimStage.REVIEW, vm.state.value.stage)
        vm.claim()

        assertEquals(HeirClaimStage.DONE, vm.state.value.stage)
        assertEquals("heir-txid-1", vm.state.value.txid)
        assertEquals(listOf(3.toByte()), vault.heirClaims)
    }

    @Test
    fun `wrong words, a foreign key and a bad address are explained`() {
        vm.onBackupPicked(byteArrayOf(1))
        vm.onWordsChange("abandon abandon")
        vm.submitWords()
        assertEquals(HeirClaimError.BAD_WORDS, vm.state.value.error)

        answer = HeirClaimOpen.NotTheHeir
        vm.onWordsChange(words)
        vm.submitWords()
        assertEquals(HeirClaimError.NOT_THE_HEIR, vm.state.value.error)
        assertEquals(HeirClaimStage.WORDS, vm.state.value.stage)

        answer = HeirClaimOpen.Opened(vault, config)
        vm.onWordsChange(words)
        vm.submitWords()
        vm.onAddressChange("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
        vm.review()
        assertEquals(HeirClaimError.BAD_ADDRESS, vm.state.value.error)
    }

    @Test
    fun `nothing matured yet is reported on review`() {
        vault.coins = listOf(HeirCoin(40_000, 2, 4))
        vm.onBackupPicked(byteArrayOf(1))
        vm.onWordsChange(words)
        vm.submitWords()
        vm.onAddressChange(ADDRESS)

        vm.review()

        assertEquals(HeirClaimError.NOTHING_MATURED, vm.state.value.error)
        assertTrue(vault.heirClaims.isEmpty())
    }

    private companion object {
        const val ADDRESS = "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"
    }
}
