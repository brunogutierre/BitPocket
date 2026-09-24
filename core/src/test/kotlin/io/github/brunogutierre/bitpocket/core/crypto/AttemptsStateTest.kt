package io.github.brunogutierre.bitpocket.core.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class AttemptsStateTest {
    @Test
    fun `has a fixed-size round-trippable encoding`() {
        val state = AttemptsState(failedAttempts = 7, lockoutRemainingMs = 60_000, bootCount = 3, elapsedAtMs = 123_456)

        assertEquals(AttemptsState.SIZE, state.encode().size)
        assertEquals(state, AttemptsState.decode(state.encode()))
        assertEquals(AttemptsState.NONE, AttemptsState.decode(AttemptsState.NONE.encode()))
    }

    @Test
    fun `rejects malformed records`() {
        val valid = AttemptsState.NONE.encode()

        assertThrows<IllegalArgumentException> { AttemptsState.decode(ByteArray(3)) }
        assertThrows<IllegalArgumentException> { AttemptsState.decode(valid.copyOf().also { it[0] = 2 }) }
        assertThrows<IllegalArgumentException> {
            AttemptsState.decode(valid.copyOf().also { ByteBuffer.wrap(it).putInt(1, -1) })
        }
    }
}
