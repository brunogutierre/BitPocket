package io.github.brunogutierre.bitpocket.core.wallet

import io.github.brunogutierre.bitpocket.core.network.SupportedNetwork
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.LoadWithPersistException
import org.bitcoindevkit.PersistenceException
import org.bitcoindevkit.Persister
import org.bitcoindevkit.Wallet
import java.io.File

/**
 * A watch-only BDK wallet: public descriptors only, persisted in SQLite. Private keys never reach
 * it; spending signs with short-lived signers (ADR 0004).
 *
 * BDK objects are not meant for concurrent use, so every call runs on [dispatcher], one at a
 * time (by default a per-wallet view of `Dispatchers.IO` with parallelism 1).
 */
class BdkWallet internal constructor(
    private val wallet: Wallet,
    private val persister: Persister,
    override val network: SupportedNetwork,
    private val dispatcher: CoroutineDispatcher = serialIoDispatcher(),
) : WalletPort,
    AutoCloseable {
    override suspend fun balance(): Balance =
        onWallet {
            val balance = wallet.balance()
            Balance(
                confirmedSats = balance.confirmed.toSat().toLong(),
                pendingSats = (balance.trustedPending.toSat() + balance.untrustedPending.toSat()).toLong(),
            )
        }

    override suspend fun nextReceiveAddress(): ReceiveAddress =
        onWallet {
            val info = wallet.nextUnusedAddress(KeychainKind.EXTERNAL)
            wallet.persist(persister)
            ReceiveAddress(index = info.index.toInt(), address = info.address.toString())
        }

    override fun close() {
        wallet.close()
        persister.close()
    }

    private suspend fun <T> onWallet(block: () -> T): T = withContext(dispatcher) { block() }

    companion object {
        private const val DB_FILE = "spending.sqlite"

        /**
         * Opens the wallet stored in [dir], creating it on first use. The database only caches
         * public chain data, so an unreadable one is replaced by a new wallet (the next sync
         * rescans the chain) instead of locking the user out.
         */
        fun open(
            dir: File,
            descriptors: PublicDescriptors,
            network: SupportedNetwork,
        ): BdkWallet {
            check(dir.isDirectory || dir.mkdirs()) { "Cannot create $dir" }
            val db = File(dir, DB_FILE)
            val kind = network.toBdkNetworkKind()
            return Descriptor(descriptors.external, kind).use { external ->
                Descriptor(descriptors.internal, kind).use { internal ->
                    loadOrNull(db, external, internal, network)
                        ?: Persister.newSqlite(db.path).let { persister ->
                            BdkWallet(Wallet(external, internal, network.toBdkNetwork(), persister), persister, network)
                        }
                }
            }
        }

        private fun loadOrNull(
            db: File,
            external: Descriptor,
            internal: Descriptor,
            network: SupportedNetwork,
        ): BdkWallet? {
            if (!db.exists()) return null
            var persister: Persister? = null
            return try {
                persister = Persister.newSqlite(db.path)
                BdkWallet(Wallet.load(external, internal, persister), persister, network)
            } catch (e: LoadWithPersistException) {
                discard(db, persister)
            } catch (e: PersistenceException) {
                discard(db, persister)
            }
        }

        private fun discard(
            db: File,
            persister: Persister?,
        ): BdkWallet? {
            persister?.close()
            check(db.delete()) { "Cannot replace the unreadable wallet database" }
            return null
        }

        /** A serial view of the IO pool: one BDK call at a time per wallet, no explicit locks. */
        fun serialIoDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
