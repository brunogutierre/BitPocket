package io.github.brunogutierre.bitpocket.core.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PinPolicyTest {
    @ParameterizedTest
    @ValueSource(
        strings = ["000000", "777777", "012345", "456789", "987654", "543210", "123123", "121212", "112233", "696969", "", "999000"],
    )
    fun `rejects repeated, sequential and common PINs`(pin: String) {
        assertTrue(PinPolicy.isTooWeak(pin.toCharArray()))
    }

    @ParameterizedTest
    @ValueSource(strings = ["890123", "210987", "274958", "193847", "012346", "654320"])
    fun `accepts other PINs, including sequences that wrap around`(pin: String) {
        assertFalse(PinPolicy.isTooWeak(pin.toCharArray()))
    }
}
