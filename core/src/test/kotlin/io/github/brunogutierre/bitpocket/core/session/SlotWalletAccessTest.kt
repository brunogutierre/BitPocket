package io.github.brunogutierre.bitpocket.core.session

import io.github.brunogutierre.bitpocket.core.crypto.Argon2idKdf
import io.github.brunogutierre.bitpocket.core.crypto.FakeKeyWrapper
import io.github.brunogutierre.bitpocket.core.crypto.FakeUnlockClock
import io.github.brunogutierre.bitpocket.core.crypto.InMemoryAttemptsStore
import io.github.brunogutierre.bitpocket.core.crypto.InMemorySlotStorage
import io.github.brunogutierre.bitpocket.core.crypto.KeyUnavailableException
import io.github.brunogutierre.bitpocket.core.crypto.KeyWrapper
import io.github.brunogutierre.bitpocket.core.crypto.SlotId
import io.github.brunogutierre.bitpocket.core.crypto.SlotStore
import io.github.brunogutierre.bitpocket.core.crypto.TEST_KDF_PARAMS
import io.github.brunogutierre.bitpocket.core.crypto.UnlockService
import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import io.github.brunogutierre.bitpocket.core.seed.FakeBiometricSeedVault
import io.github.brunogutierre.bitpocket.core.settings.AutoLock
import io.github.brunogutierre.bitpocket.core.settings.WalletSettings
import io.github.brunogutierre.bitpocket.core.testing.FakePocketWallet
import io.github.brunogutierre.bitpocket.core.testing.FakeWalletOpener
import io.github.brunogutierre.bitpocket.core.vault.HeirTimelock
import io.github.brunogutierre.bitpocket.core.vault.VaultBackup
import io.github.brunogutierre.bitpocket.core.vault.VaultBackupContents
import io.github.brunogutierre.bitpocket.core.vault.VaultConfig
import io.github.brunogutierre.bitpocket.core.vault.VaultKeys
import io.github.brunogutierre.bitpocket.core.vault.VaultRole
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration

class SlotWalletAccessTest {
    private val storage = InMemorySlotStorage()
    private val session = Session()
    private val entropy = ByteArray(16) { 3 }
    private val opener = FakeWalletOpener()

    private fun access(keyWrapper: KeyWrapper = FakeKeyWrapper()): SlotWalletAccess {
        val slots = SlotStore(storage, Argon2idKdf, keyWrapper, TEST_KDF_PARAMS)
        return SlotWalletAccess(slots, UnlockService(slots, InMemoryAttemptsStore(), FakeUnlockClock()), session, opener)
    }

    @Test
    fun `creating saves slot A without a biometric copy and opens the session`() {
        val access = access()

        access.create("123456".toCharArray(), entropy, SupportedNetwork.TESTNET4)

        assertTrue(SlotId.A in storage.files && SlotId.B in storage.files)
        val wallet = session.wallet!!
        assertEquals(SlotId.A, wallet.slot)
        assertNull(wallet.biometricSeed)
        assertEquals(SupportedNetwork.TESTNET4, wallet.network)
        assertSame(opener.opened.single(), wallet.spending)
        assertEquals(SupportedNetwork.TESTNET4, wallet.spending.network)
    }

    @Test
    fun `the session keeps no seed once the pockets are open`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        session.lock()

        assertEquals(UnlockOutcome.WrongPin(Duration.ZERO), access.unlock("000000".toCharArray()))
        assertEquals(UnlockOutcome.Unlocked, access.unlock("123456".toCharArray()))

        // The opener saw the real seed, which was wiped right after the pockets opened.
        assertEquals(2, opener.payloads.size)
        assertTrue(opener.payloads.all { it.seedEntropy.contentEquals(ByteArray(16)) })
        assertEquals(listOf(true, false), opener.opened.map { it.closed })
    }

    @Test
    fun `signing re-enters the session PIN to get the seed`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)

        val seed = access.seedForSigning("123456".toCharArray())
        assertArrayEquals(entropy, (seed as SeedOutcome.Seed).entropy)
        assertEquals(SeedOutcome.WrongPin(Duration.ZERO), access.seedForSigning("000000".toCharArray()))

        session.lock()
        assertEquals(SeedOutcome.Failed, access.seedForSigning("123456".toCharArray()))
    }

    @Test
    fun `a wallet database that cannot be opened fails the unlock without a PIN error`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        session.lock()
        opener.failure = IllegalStateException("disk full")

        assertEquals(UnlockOutcome.Failed, access.unlock("123456".toCharArray()))
        assertFalse(session.isUnlocked)
    }

    @Test
    fun `reports lockouts and unusable device keys`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        repeat(4) { access.unlock("000000".toCharArray()) }

        assertEquals(UnlockOutcome.WrongPin(Duration.ofSeconds(30)), access.unlock("000000".toCharArray()))
        assertEquals(UnlockOutcome.LockedOut(Duration.ofSeconds(30)), access.unlock("123456".toCharArray()))

        val broken =
            object : KeyWrapper {
                override fun wrap(plaintext: ByteArray) = throw KeyUnavailableException("device locked")

                override fun unwrap(wrapped: ByteArray) = throw KeyUnavailableException("device locked")
            }
        assertEquals(UnlockOutcome.Failed, access(broken).unlock("123456".toCharArray()))
    }

    @Test
    fun `changing the PIN needs the current one`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)

        assertEquals(SettingsOutcome.WrongPin(Duration.ZERO), access.changePin("000000".toCharArray(), "258147".toCharArray()))
        assertEquals(SettingsOutcome.Done, access.changePin("123456".toCharArray(), "258147".toCharArray()))

        session.lock()
        assertEquals(UnlockOutcome.WrongPin(Duration.ZERO), access.unlock("123456".toCharArray()))
        assertEquals(UnlockOutcome.Unlocked, access.unlock("258147".toCharArray()))
        assertEquals(SettingsOutcome.Done, access.verifyPin("258147".toCharArray()))
    }

    @Test
    fun `biometric signing seals the seed into the slot and the session, and can be turned off`() =
        runTest {
            val access = access()
            val vault = FakeBiometricSeedVault()
            access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)

            assertEquals(SettingsOutcome.Done, access.setBiometricSigning("123456".toCharArray(), vault))
            assertArrayEquals(vault.sealed(entropy), session.wallet!!.biometricSeed)
            val spending = session.wallet!!.spending
            session.lock()
            access.unlock("123456".toCharArray())
            assertArrayEquals(vault.sealed(entropy), session.wallet!!.biometricSeed)

            assertEquals(SettingsOutcome.Done, access.setBiometricSigning("123456".toCharArray(), null))
            assertNull(session.wallet!!.biometricSeed)
            assertTrue((spending as FakePocketWallet).closed)
            assertFalse((session.wallet!!.spending as FakePocketWallet).closed, "toggling keeps the pockets open")
        }

    @Test
    fun `a cancelled biometric prompt leaves biometric signing off`() =
        runTest {
            val access = access()
            val vault = FakeBiometricSeedVault().apply { failSeal = true }
            access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)

            assertEquals(SettingsOutcome.Failed, access.setBiometricSigning("123456".toCharArray(), vault))
            assertNull(session.wallet!!.biometricSeed)
            assertEquals(SettingsOutcome.WrongPin(Duration.ZERO), access.setBiometricSigning("000000".toCharArray(), vault))
        }

    @Test
    fun `settings are saved in the slot and applied to the session`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)

        assertEquals(
            SettingsOutcome.WrongPin(Duration.ZERO),
            access.updateSettings("000000".toCharArray(), WalletSettings(AutoLock.IMMEDIATELY)),
        )
        assertEquals(SettingsOutcome.Done, access.updateSettings("123456".toCharArray(), WalletSettings(AutoLock.IMMEDIATELY)))

        assertEquals(
            AutoLock.IMMEDIATELY,
            session.walletState.value!!
                .settings.autoLock,
        )
        session.lock()
        access.unlock("123456".toCharArray())
        assertEquals(AutoLock.IMMEDIATELY, session.wallet!!.settings.autoLock)
    }

    @Test
    fun `sets up the Vault once, with a backup every cosigner can open`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        val recovery = ByteArray(16) { 7 }
        val heir = ByteArray(16) { 8 }

        assertEquals(
            VaultSetupOutcome.Denied(SettingsOutcome.WrongPin(Duration.ZERO)),
            access.setUpVault("000000".toCharArray(), recovery, heir, HeirTimelock.HOUR),
        )
        val done = access.setUpVault("123456".toCharArray(), recovery, heir, HeirTimelock.HOUR) as VaultSetupOutcome.Done

        val vault = session.wallet!!.settings.vault!!
        assertEquals(VaultKeys.publicKey(entropy, SupportedNetwork.SIGNET), vault.phoneKey)
        assertEquals(HeirTimelock.HOUR, vault.timelock)
        listOf(entropy, recovery, heir).forEach { assertEquals(vault, VaultBackup.open(done.backup, it)!!.config) }
        assertEquals(VaultSetupOutcome.AlreadySetUp, access.setUpVault("123456".toCharArray(), recovery, heir, HeirTimelock.DAY))
        assertSame(opener.vaults.single(), session.wallet!!.vault)
        session.lock()
        assertTrue(opener.vaults.single().closed)
        access.unlock("123456".toCharArray())
        assertEquals(vault, session.wallet!!.settings.vault)
        assertSame(opener.vaults.last(), session.wallet!!.vault, "unlocking opens the Vault pocket too")
    }

    @Test
    fun `imports the Vault of a restored wallet only from its own backup`() {
        val network = SupportedNetwork.SIGNET
        val config =
            VaultConfig(
                VaultKeys.publicKey(entropy, network),
                VaultKeys.publicKey(
                    ByteArray(16) {
                        7
                    },
                    network,
                ),
                VaultKeys.publicKey(ByteArray(16) { 8 }, network),
                HeirTimelock.DAY,
            )
        val backup =
            VaultBackup.seal(
                VaultBackupContents(network, config),
                mapOf(
                    VaultRole.PHONE to entropy,
                    VaultRole.HEIR to ByteArray(16) { 8 },
                ),
            )
        val othersBackup = VaultBackup.seal(VaultBackupContents(network, config), mapOf(VaultRole.HEIR to ByteArray(16) { 8 }))
        val access = access()
        access.create("123456".toCharArray(), entropy, network)

        assertEquals(VaultSetupOutcome.NotThisWallet, access.importVault("123456".toCharArray(), othersBackup))
        assertEquals(VaultSetupOutcome.NotThisWallet, access.importVault("123456".toCharArray(), "junk".toByteArray()))
        assertTrue(access.importVault("123456".toCharArray(), backup) is VaultSetupOutcome.Done)
        assertEquals(config, session.wallet!!.settings.vault)
    }

    @Test
    fun `the duress PIN opens a decoy wallet that knows nothing of the real one`() {
        val access = access()
        val decoy = ByteArray(16) { 6 }
        access.create("123456".toCharArray(), entropy, SupportedNetwork.TESTNET4)

        assertEquals(SettingsOutcome.Failed, access.setUpDuress("123456".toCharArray(), "123456".toCharArray(), decoy))
        assertEquals(SettingsOutcome.Done, access.setUpDuress("123456".toCharArray(), "258147".toCharArray(), decoy))
        assertTrue(session.wallet!!.settings.duress)
        session.lock()

        assertEquals(UnlockOutcome.Unlocked, access.unlock("258147".toCharArray()))
        val decoySession = session.wallet!!
        assertEquals(SlotId.B, decoySession.slot)
        assertEquals(SupportedNetwork.TESTNET4, decoySession.network)
        assertFalse(decoySession.settings.duress, "the decoy shows no duress PIN")
        assertTrue(
            opener.payloads
                .last()
                .seedEntropy
                .contentEquals(ByteArray(16)),
            "decoy seed wiped after opening",
        )
        val decoySeed = (access.seedForSigning("258147".toCharArray()) as SeedOutcome.Seed).entropy
        assertArrayEquals(decoy, decoySeed)
    }

    @Test
    fun `setting up duress inside the decoy changes nothing in the real wallet`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        access.setUpDuress("123456".toCharArray(), "258147".toCharArray(), ByteArray(16) { 6 })
        session.lock()
        access.unlock("258147".toCharArray())

        // From the decoy, "set up duress" with the real PIN as the new one: no oracle, no change.
        assertEquals(SettingsOutcome.Done, access.setUpDuress("258147".toCharArray(), "123456".toCharArray(), ByteArray(16) { 7 }))
        assertTrue(session.wallet!!.settings.duress)

        session.lock()
        assertEquals(UnlockOutcome.Unlocked, access.unlock("123456".toCharArray()))
        assertEquals(SlotId.A, session.wallet!!.slot)
        assertArrayEquals(entropy, (access.seedForSigning("123456".toCharArray()) as SeedOutcome.Seed).entropy)
        assertTrue(session.wallet!!.settings.duress, "the real wallet's own setting is untouched")
    }

    @Test
    fun `removing duress turns the decoy slot back into random bytes`() {
        val access = access()
        access.create("123456".toCharArray(), entropy, SupportedNetwork.SIGNET)
        access.setUpDuress("123456".toCharArray(), "258147".toCharArray(), ByteArray(16) { 6 })
        val sizeBefore = storage.files.getValue(SlotId.B).size

        assertEquals(SettingsOutcome.Done, access.removeDuress("123456".toCharArray()))

        assertFalse(session.wallet!!.settings.duress)
        assertEquals(sizeBefore, storage.files.getValue(SlotId.B).size)
        session.lock()
        assertEquals(UnlockOutcome.WrongPin(Duration.ZERO), access.unlock("258147".toCharArray()))
    }

    @Test
    fun `wiping deletes slots, attempts and wallet databases`(
        @TempDir root: File,
    ) {
        listOf("slots/a/slot.bin", "slots/b/slot.bin", "attempts.bin", "wallets/ab/spending.sqlite", "other.txt").forEach {
            File(root, it).apply { parentFile.mkdirs() }.writeText("x")
        }

        assertTrue(WalletWipe.wipe(root))

        assertEquals(listOf("other.txt"), root.list()!!.toList())
        assertTrue(WalletWipe.wipe(root), "wiping twice is fine")
    }
}
