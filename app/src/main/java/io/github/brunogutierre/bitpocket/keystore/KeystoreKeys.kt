package io.github.brunogutierre.bitpocket.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/** Neutral Keystore aliases: they must not hint at what each key protects. */
object KeyAliases {
    const val SLOTS = "k1"
    const val ATTEMPTS = "k2"
    const val SEED = "k3"
}

/** AES-256-GCM keys in AndroidKeyStore, StrongBox-backed when available, otherwise TEE. */
object KeystoreKeys {
    private const val PROVIDER = "AndroidKeyStore"

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    /** Returns the key under [alias], creating it with [configure] applied to the base spec. */
    @Synchronized
    fun getOrCreate(
        alias: String,
        configure: KeyGenParameterSpec.Builder.() -> Unit = {},
    ): SecretKey {
        (keyStore.getKey(alias, null) as SecretKey?)?.let { return it }
        return try {
            generate(alias, strongBox = true, configure)
        } catch (e: StrongBoxUnavailableException) {
            generate(alias, strongBox = false, configure)
        }
    }

    @Synchronized
    fun delete(alias: String) = keyStore.deleteEntry(alias)

    /** Human-readable security level of [key], e.g. for logs: STRONGBOX, TEE or SOFTWARE. */
    fun securityLevel(key: SecretKey): String {
        val info = SecretKeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            return if (info.isInsideSecureHardware) "SECURE_HARDWARE" else "SOFTWARE"
        }
        return when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
            else -> "UNKNOWN(${info.securityLevel})"
        }
    }

    private fun generate(
        alias: String,
        strongBox: Boolean,
        configure: KeyGenParameterSpec.Builder.() -> Unit,
    ): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUnlockedDeviceRequired(true)
                .setIsStrongBoxBacked(strongBox)
                .apply(configure)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }
    }
}
