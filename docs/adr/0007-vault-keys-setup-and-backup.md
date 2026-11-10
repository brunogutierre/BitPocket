# 7. Vault keys, setup on the phone and a multi-recipient backup

- Status: Accepted
- Date: 2026-11-09

## Context

ADR 0002 fixed the Vault policy, `wsh(or_d(multi(2,PHONE,RECOVERY),and_v(v:pkh(HEIR),older(N))))`,
and how BDK signs it. M3 must decide where the three keys come from, what the phone stores,
how the heir timelock is chosen, and how the Vault can be rebuilt after losing the phone.

## Decision

- **Keys at BIP48 `m/48'/1'/0'/2'`** (P2WSH multisig, test coin type), stored as public account
  keys with their origin (`[fingerprint/48'/1'/0'/2']tpub…`).
  - PHONE is derived from the wallet seed.
  - RECOVERY and HEIR are generated on the phone during setup. Their 12 words are shown once,
    verified, and then wiped. Only their public keys stay.
- **Heir from day one.** The full policy, heir included, is used from the start. Adding the heir
  later would change every address and need a migration. The heir may be the owner themselves,
  acting as a delayed backup key.
- **Timelock chosen at setup:** 6 blocks (to try the heir path on Signet), 144, 4 320 or 52 560
  (about a year). The maximum relative timelock is 65 535 blocks.
- **Storage.** `VaultConfig` (3 public keys + timelock) is a record in the encrypted slot
  (ADR 0005). The watch-only Vault wallet uses the BIP389 multipath descriptor. Signers use
  one descriptor per keychain, with private keys only for the signing call (ADR 0002).
- **Backup for any cosigner.** `VaultBackup` encrypts the `VaultConfig` and network under a random
  key, wrapped once per cosigner with `HKDF-SHA256(seed entropy)`. Whoever holds any one of the three
  seeds can open it, with no extra password: the owner after a restore, the recovery-key holder, or
  the heir. The file goes out through the share sheet or the system file picker.
- **Co-signing with RECOVERY:** the recovery words are typed on the phone for each 2-of-2 spend, as a
  short-lived signer that is wiped afterwards.

## Consequences

- The recovery key briefly exists on an online phone when spending from the Vault. This is the main
  trade-off of a single-device study wallet. The upgrade path is PSBT export/import to an offline
  signer (needs a second app and an animated-QR or file format), which is out of scope.
- Losing every copy of the backup is not fatal: the descriptor can be rebuilt from the three seeds.
  Without the backup, however, the heir cannot find the Vault from their seed alone, because the
  other public keys are unknown.
- The private account key is briefly a JVM String: BDK only takes miniscript descriptors as text.
- Rejected: a plain 2-of-2 first and the heir in M4 (address migration); a password-encrypted backup
  (one more secret to lose); storing private descriptors (ADR 0004).
