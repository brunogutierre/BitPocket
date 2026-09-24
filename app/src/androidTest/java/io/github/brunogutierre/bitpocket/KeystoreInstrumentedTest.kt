package io.github.brunogutierre.bitpocket

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.brunogutierre.bitpocket.biometric.BiometricAvailability
import io.github.brunogutierre.bitpocket.biometric.BiometricStatus
import io.github.brunogutierre.bitpocket.biometric.SeedCipher
import io.github.brunogutierre.bitpocket.core.crypto.AttemptsState
import io.github.brunogutierre.bitpocket.core.crypto.AuthenticationFailedException
import io.github.brunogutierre.bitpocket.core.crypto.FileAttemptsStore
import io.github.brunogutierre.bitpocket.keystore.AndroidUnlockClock
import io.github.brunogutierre.bitpocket.keystore.KeystoreKeyWrapper
import io.github.brunogutierre.bitpocket.keystore.KeystoreKeys
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.GeneralSecurityException

/** Runs on a real device (Keystore, StrongBox, biometrics). Key levels are logged under [TAG]. */
@RunWith(AndroidJUnit4::class)
class KeystoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wrapper = KeystoreKeyWrapper(WRAP_ALIAS)

    @After
    fun deleteTestKeys() {
        listOf(WRAP_ALIAS, SEED_ALIAS).forEach(KeystoreKeys::delete)
    }

    @Test
    fun wrapperRoundTripsWithRandomizedConstantSizeOutput() {
        val secret = ByteArray(100) { it.toByte() }

        val first = wrapper.wrap(secret)
        val second = wrapper.wrap(secret)

        assertArrayEquals(secret, wrapper.unwrap(first))
        assertEquals(secret.size + KeystoreKeyWrapper.OVERHEAD, first.size)
        assertEquals(first.size, second.size)
        assertFalse("IV must be random", first.contentEquals(second))
        Log.i(TAG, "wrapper key level: ${KeystoreKeys.securityLevel(KeystoreKeys.getOrCreate(WRAP_ALIAS))}")
    }

    @Test
    fun tamperedOrForeignValuesFailAuthentication() {
        val wrapped = wrapper.wrap(ByteArray(32))

        for (index in listOf(1, 13, wrapped.size - 1)) {
            val tampered = wrapped.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertThrows(AuthenticationFailedException::class.java) { wrapper.unwrap(tampered) }
        }
        assertThrows(AuthenticationFailedException::class.java) { wrapper.unwrap(ByteArray(10)) }
    }

    @Test
    fun attemptsStoreRoundTripsThroughTheKeystore() {
        val file = File(context.cacheDir, "attempts-test.bin").apply { delete() }
        val store = FileAttemptsStore(file, wrapper)
        val state = AttemptsState(failedAttempts = 6, lockoutRemainingMs = 60_000, bootCount = 1, elapsedAtMs = 42)

        store.write(state)

        assertEquals(state, store.read())
        assertEquals(AttemptsState.SIZE + KeystoreKeyWrapper.OVERHEAD, file.length().toInt())
        file.delete()
    }

    @Test
    fun unlockClockReadsMonotonicTime() {
        val clock = AndroidUnlockClock(context.contentResolver)

        val first = clock.now()
        val second = clock.now()

        assertTrue(second.elapsedRealtimeMs >= first.elapsedRealtimeMs)
        assertEquals(first.bootCount, second.bootCount)
        Log.i(TAG, "boot count: ${first.bootCount}")
    }

    @Test
    fun biometricKeyIsCreatedAndRefusesUseWithoutAuthentication() {
        val status = BiometricAvailability.status(context)
        Log.i(TAG, "BIOMETRIC_STRONG status: $status")
        assumeTrue("needs an enrolled Class 3 biometric", status == BiometricStatus.AVAILABLE)
        val seedCipher = SeedCipher(SEED_ALIAS)

        val cipher = seedCipher.encryptCipher()

        Log.i(TAG, "seed key level: ${KeystoreKeys.securityLevel(KeystoreKeys.getOrCreate(SEED_ALIAS))}")
        assertThrows(GeneralSecurityException::class.java) { seedCipher.seal(cipher, ByteArray(16)) }
    }

    private companion object {
        const val TAG = "KeystoreTest"
        const val WRAP_ALIAS = "test-wrap"
        const val SEED_ALIAS = "test-seed"
    }
}
