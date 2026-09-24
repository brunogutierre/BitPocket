package io.github.brunogutierre.bitpocket

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.brunogutierre.bitpocket.core.crypto.Argon2idKdf
import io.github.brunogutierre.bitpocket.core.crypto.KdfParams
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures Argon2id cost on the device to pick [KdfParams.DEFAULT]. An unlock runs the KDF for
 * both slots, so each sample times TWO derivations (target: ~1 s). Never fails on timing: it
 * only logs a table under the [TAG] logcat tag.
 */
@RunWith(AndroidJUnit4::class)
class Argon2BenchmarkTest {
    @Test
    fun logUnlockCostForParameterMatrix() {
        val pin = "123456".toCharArray()
        val salt = ByteArray(KdfParams.SALT_BYTES) { it.toByte() }
        Log.i(TAG, "device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
        Log.i(TAG, "m_MiB | t | p | unlock_ms (2 derivations): median [min..max] of $SAMPLES")

        for (memoryMib in listOf(32, 48, 64)) {
            for (iterations in listOf(2, 3)) {
                val params = KdfParams(memoryKib = memoryMib * 1024, iterations = iterations, parallelism = 1)
                Argon2idKdf.deriveKey(pin, salt, params) // warm-up (JIT, allocation)
                val samples =
                    List(SAMPLES) {
                        val start = System.nanoTime()
                        repeat(2) { Argon2idKdf.deriveKey(pin, salt, params) }
                        (System.nanoTime() - start) / 1_000_000
                    }.sorted()
                Log.i(TAG, "$memoryMib | $iterations | 1 | ${samples[SAMPLES / 2]} [${samples.first()}..${samples.last()}]")
            }
        }
    }

    private companion object {
        const val TAG = "Argon2Bench"
        const val SAMPLES = 3
    }
}
