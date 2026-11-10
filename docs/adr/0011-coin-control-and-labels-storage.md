# 11. Coin control and labels storage

- Status: Accepted
- Date: 2026-11-09

## Context

M6 adds coin control (freeze coins, pick which coins pay) and wallet labels with BIP329
import/export. Both need storage next to each wallet, and both must stay per wallet so the decoy
wallet (ADR 0009) keeps its own.

## Decision

- **Frozen coins are BDK locked outpoints** (`Wallet.lockOutpoint`/`unlockOutpoint`), persisted in
  the wallet's SQLite database with the rest of the wallet state. BDK leaves locked coins out of
  coin selection; manual selection uses `addUtxos` + `manuallySelectedOnly`, and `SpendGuard` still
  refuses unconfirmed coins. Outpoints cross the ports as `txid:vout` text.
- **Labels are a BIP329 JSON Lines file per wallet** (`wallets/<id>/labels.jsonl`): the storage
  format is the export format, so export is a copy and import is a merge. Unknown record types and
  fields are kept as they are. On import, invalid lines and addresses of another network are skipped,
  and `output` records with `spendable: false` freeze the coin.
- **No database library.** A strict reader for BIP329's flat JSON objects (strings, booleans, null)
  keeps `:core` free of a JSON dependency.

## Consequences

- Labels are not encrypted beyond Android's app sandbox (they live in `noBackupFilesDir`, excluded
  from backups). They reveal what transactions were for, but not keys or balances.
- Rejected: Room (an ORM for one key-value list); labels inside the slot payload (4 KB is too small
  and labels change too often to re-run Argon2id each time).
