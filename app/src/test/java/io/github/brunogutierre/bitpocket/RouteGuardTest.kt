package io.github.brunogutierre.bitpocket

import io.github.brunogutierre.bitpocket.ui.navigation.CreateSeed
import io.github.brunogutierre.bitpocket.ui.navigation.Home
import io.github.brunogutierre.bitpocket.ui.navigation.Lock
import io.github.brunogutierre.bitpocket.ui.navigation.RestoreSeed
import io.github.brunogutierre.bitpocket.ui.navigation.SetPin
import io.github.brunogutierre.bitpocket.ui.navigation.VerifySeed
import io.github.brunogutierre.bitpocket.ui.navigation.Welcome
import io.github.brunogutierre.bitpocket.ui.navigation.guardRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RouteGuardTest {
    @Test
    fun `restored routes fall back when their in-memory state is gone`() {
        // After process death: wallet on disk, nothing in memory.
        fun afterDeath(route: androidx.navigation3.runtime.NavKey) = guardRoute(route, true, false, false, false)
        assertEquals(Lock, afterDeath(Home))
        assertEquals(Lock, afterDeath(Welcome))
        assertEquals(Lock, afterDeath(SetPin))

        // During onboarding, before a wallet exists.
        assertEquals(Welcome, guardRoute(VerifySeed, false, false, false, false))
        assertEquals(CreateSeed, guardRoute(CreateSeed, false, false, true, true))
        assertEquals(Welcome, guardRoute(SetPin, false, false, true, false))
        assertEquals(RestoreSeed, guardRoute(RestoreSeed, false, false, false, false))
        assertEquals(Welcome, guardRoute(Lock, false, false, false, false))
        assertEquals(Home, guardRoute(Home, true, true, false, false))
    }
}
