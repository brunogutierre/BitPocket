package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.InMemorySlotStorage
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LaunchStateTest {
    private val storage = InMemorySlotStorage()

    @Test
    fun `starts onboarding when no slot file exists`() {
        assertEquals(LaunchState.ONBOARDING, LaunchState.of(storage))
    }

    @ParameterizedTest
    @EnumSource(SlotId::class)
    fun `starts locked when any slot file exists`(slot: SlotId) {
        storage.write(slot, byteArrayOf(1))

        assertEquals(LaunchState.LOCKED, LaunchState.of(storage))
    }
}
