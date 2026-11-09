package io.github.brunogutierre.bitpocket.receive

import io.github.brunogutierre.bitpocket.FakePocketWallet
import io.github.brunogutierre.bitpocket.MainDispatcherExtension
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.wallet.ReceiveAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ReceiveViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val pocket = FakePocketWallet(SupportedNetwork.TESTNET4)

    @Test
    fun `shows the first unused address with its QR code`() {
        val vm = ReceiveViewModel(pocket)

        assertEquals(ReceiveAddress(0, "tb1qfirst"), vm.state.value.address)
        assertNotNull(vm.state.value.qr)
        assertEquals(SupportedNetwork.TESTNET4, vm.network)
        assertEquals("bitcoin:tb1qfirst", vm.shareText())
    }

    @Test
    fun `new address replaces the shown one until the unused limit`() {
        pocket.freshAddresses += ReceiveAddress(1, "tb1qsecond")
        val vm = ReceiveViewModel(pocket)

        vm.newAddress()
        assertEquals(ReceiveAddress(1, "tb1qsecond"), vm.state.value.address)
        assertFalse(vm.state.value.atUnusedLimit)

        vm.newAddress()
        assertEquals(ReceiveAddress(1, "tb1qsecond"), vm.state.value.address)
        assertTrue(vm.state.value.atUnusedLimit)
    }
}
