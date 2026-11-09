# 5. Security settings live in the slot payload

- Status: Accepted
- Date: 2026-11-09

## Context

M1 adds the first security setting (auto-lock), and later milestones add more: guardrails (M2),
vault keys and timelock (M3/M4), a custom Esplora server (M6). Android's usual places for settings
(SharedPreferences, DataStore) are plain files: anyone with the device unlocked, a backup or a
debugger could weaken them (e.g. disable auto-lock, raise a spending limit, point the wallet to a
malicious server), and with a duress PIN (M5) a single settings file would show which wallet is
real.

## Decision

- Security settings are a versioned section of the **encrypted slot payload** (`WalletSettings`,
  tag-length-value records; unknown tags are skipped). Slot payload v2 adds it; v1 decodes with
  defaults.
- **Every change re-enters the PIN** (`WalletAccess.updateSettings`), through the same Argon2id +
  backoff path as unlocking, and re-encrypts the slot.
- The session carries the settings while unlocked; each slot (real or decoy) has its own.
- Auto-lock: `AutoLocker` locks once the app returns after the chosen time in the background
  (ProcessLifecycleOwner, monotonic `elapsedRealtime`), or right away for "immediately". The
  recents thumbnail is disabled on Android 13+.

## Consequences

- Settings are authenticated (AES-GCM) and cannot be changed without the PIN.
- The slot keeps over 2.5 KB free for later settings (tested).
- Changing a setting costs about one second (Argon2id), which is acceptable for rare changes.
- Rejected: SharedPreferences/DataStore for security settings (unauthenticated, per app, not per
  wallet); a separate encrypted settings file (a second store to keep consistent with each slot).
