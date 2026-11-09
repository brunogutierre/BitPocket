# 4. Watch-only wallets, Esplora sync and short-lived signers

- Status: Accepted
- Date: 2026-11-09

## Context

M1 needs a real Spending wallet: balance, addresses, history, and later sending. BDK keeps its
state (revealed indexes, transactions, chain checkpoints) in a `Persister`. If the wallet were
built from descriptors with private keys, BDK would hold the keys for the whole session, and the
descriptors handed to it would have to be stored somewhere outside the encrypted slot.

## Decision

- **Watch-only wallets.** Every BDK wallet is built from **public** descriptors (`tpub`) only and
  persisted with `Persister.newSqlite` in `noBackupFilesDir/wallets/<id>/spending.sqlite`.
  Private keys never reach a persisted wallet.
- **Short-lived signers.** Spending builds the private descriptors from the seed only for signing
  (`Wallet.signWithSigners`) and closes them right after.
- **No seed in the session.** After unlock, the seed is wiped as soon as the pockets are open; the
  session keeps the slot, network, biometric-sealed seed and open wallets. Each signature gets the
  seed again: by BiometricPrompt (Class 3 only) when biometric signing is on, else by re-entering
  the PIN through the same Argon2id + backoff path as unlocking. A PIN that opens another slot is
  a wrong PIN there (the failure stays recorded), so re-authentication is never a slot oracle.
- **Checked before broadcast.** The signed transaction must match the reviewed plan (recipient
  amount, change, outputs, fee, inputs) and `SpendGuard` runs again on the PSBT.
- **One database per wallet, not per slot.** `id = hex(SHA-256(network + public external
  descriptor))[0..16]`: the directory name does not reveal which slot is in use (duress, M5), and
  the network is part of it because Signet and Testnet4 share the same `tpub`.
- **Disposable database.** It only caches public chain data, so an unreadable database is replaced
  by a new wallet and the next sync rebuilds it.
- **Esplora sync.** The first sync is a full scan (stop gap 20, 4 parallel requests) while the local
  chain is still at genesis; later syncs only check revealed scripts. Sync runs right after unlock
  and on pull-to-refresh. There is no background sync: a background job cannot open the encrypted
  wallet without the PIN.
- **Serial access.** Every BDK call of a wallet runs on `Dispatchers.IO.limitedParallelism(1)`.
  `close()` is queued on the same dispatcher, so locking the app during a sync never frees native
  objects in the middle of a call.
- **Ports.** The UI sees `PocketWallet` (`WalletPort` + `ChainPort`) with plain Kotlin types. The
  Esplora client sits behind `ChainClient`, so wallet logic is tested offline with a fake; the thin
  `EsploraChainClient` is covered by an opt-in live Signet test (`./gradlew :core:test -Plive`).

## Consequences

- Losing or corrupting the database costs one full scan, never funds.
- Unlock opens the wallet database (SQLite, fast) but does not wait for the network.
- An address revealed on one device is unknown to a restored wallet until it is used on chain;
  revealed-but-unused addresses must stay below the stop gap (M1 `feat/receive`).
- Rejected: descriptors with private keys in BDK (keys in memory for the whole session, plus a
  second encrypted store); Electrum (a second backend to maintain, see M6).
