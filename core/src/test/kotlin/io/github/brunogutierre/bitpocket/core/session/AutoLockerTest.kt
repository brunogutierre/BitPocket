package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.settings.AutoLock
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.testing.FakePocketWallet
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoLockerTest {
    private val session = Session()
    private val locker = AutoLocker(session)

    private fun unlock(autoLock: AutoLock) =
        session.open(UnlockedWallet(SlotId.A, SupportedNetwork.SIGNET, null, FakePocketWallet(), WalletSettings(autoLock)))

    @Test
    fun `locks on return after the timeout, not before`() {
        unlock(AutoLock.ONE_MINUTE)

        locker.onBackground(nowMs = 1_000)
        locker.onForeground(nowMs = 60_999)
        assertTrue(session.isUnlocked)

        locker.onBackground(nowMs = 100_000)
        locker.onForeground(nowMs = 160_000)
        assertFalse(session.isUnlocked)
    }

    @Test
    fun `immediately locks as soon as the app leaves the screen`() {
        unlock(AutoLock.IMMEDIATELY)

        locker.onBackground(nowMs = 5)

        assertNull(session.wallet)
    }

    @Test
    fun `does nothing while locked or without a background period`() {
        locker.onBackground(nowMs = 0)
        unlock(AutoLock.THIRTY_SECONDS)
        locker.onForeground(nowMs = 999_999)

        assertTrue(session.isUnlocked)
    }
}
