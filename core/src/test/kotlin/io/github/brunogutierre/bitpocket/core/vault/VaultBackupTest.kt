package io.github.brunogutierre.bitpocket.core.vault

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VaultBackupTest {
    private val network = SupportedNetwork.TESTNET4
    private val seeds =
        mapOf(
            VaultRole.PHONE to ByteArray(16) { 1 },
            VaultRole.RECOVERY to ByteArray(16) { 2 },
            VaultRole.HEIR to ByteArray(16) { 3 },
        )
    private val config =
        VaultConfig(
            VaultKeys.publicKey(seeds.getValue(VaultRole.PHONE), network),
            VaultKeys.publicKey(seeds.getValue(VaultRole.RECOVERY), network),
            VaultKeys.publicKey(seeds.getValue(VaultRole.HEIR), network),
            HeirTimelock.MONTH,
        )
    private val contents = VaultBackupContents(network, config)

    @Test
    fun `any cosigner opens it with their own seed`() {
        val backup = VaultBackup.seal(contents, seeds)

        seeds.values.forEach { seed -> assertEquals(contents, VaultBackup.open(backup, seed)) }
        assertNull(VaultBackup.open(backup, ByteArray(16) { 9 }), "another seed is not a cosigner")
    }

    @Test
    fun `can be sealed for some cosigners only`() {
        val backup = VaultBackup.seal(contents, mapOf(VaultRole.HEIR to seeds.getValue(VaultRole.HEIR)))

        assertEquals(contents, VaultBackup.open(backup, seeds.getValue(VaultRole.HEIR)))
        assertNull(VaultBackup.open(backup, seeds.getValue(VaultRole.PHONE)))
    }

    @Test
    fun `rejects tampering, truncation and other files`() {
        val backup = VaultBackup.seal(contents, seeds)
        val phone = seeds.getValue(VaultRole.PHONE)

        assertThrows<IllegalArgumentException> {
            VaultBackup.open(
                backup.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() },
                phone,
            )
        }
        assertThrows<IllegalArgumentException> { VaultBackup.open(backup.copyOf(20), phone) }
        assertThrows<IllegalArgumentException> { VaultBackup.open("not a backup".toByteArray(), phone) }
        // The network is authenticated: renaming it breaks every envelope.
        val renamed = String(backup, Charsets.ISO_8859_1).replace("TESTNET4", "TESTNET5").toByteArray(Charsets.ISO_8859_1)
        assertThrows<IllegalArgumentException> { VaultBackup.open(renamed, phone) }
    }

    @Test
    fun `the backup key derivation is frozen`() {
        // HKDF-SHA256(salt "bitpocket", ikm 16 zero bytes, info "bitpocket/vault-backup/v1"),
        // computed independently with Python's hmac/hashlib.
        val expected = "a7e03f4388f57d2db2f00bafbcdeb418b4d40b67afc34e7c215126839d85e49c"

        assertEquals(expected, VaultBackup.backupKey(ByteArray(16)).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `backup keys are deterministic per seed and differ between seeds`() {
        assertArrayEquals(VaultBackup.backupKey(ByteArray(16)), VaultBackup.backupKey(ByteArray(16)))
        assertNotEquals(VaultBackup.backupKey(ByteArray(16)).toList(), VaultBackup.backupKey(ByteArray(16) { 1 }).toList())
        assertEquals(32, VaultBackup.backupKey(ByteArray(32)).size)
    }
}
