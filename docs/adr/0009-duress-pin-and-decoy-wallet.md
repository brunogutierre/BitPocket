# 9. Duress PIN and decoy wallet

- Status: Accepted
- Date: 2026-11-09

## Context

Someone forced to unlock the wallet in person (a "$5 wrench attack") should be able to give a PIN
that opens a plausible wallet with little money, without the attacker being able to tell. The slot
storage was designed for this since M1: two slot files of the same size, both rewritten on every
save, and both tried in constant time on every unlock (ADR in the README and manual).

## Decision

- **Slot A is the real wallet, slot B the decoy.** The duress PIN opens slot B, which holds a real
  Signet wallet with its own seed. Only `SlotWalletAccess` knows which slot is which; the app sees
  an unlocked session like any other. CI fails if app code mentions `SlotId`.
- **Set up from the real wallet, after the PIN.** Settings → Duress PIN: re-enter the PIN, choose a
  different duress PIN (PinPolicy applies), and a new decoy seed is generated. The real wallet only
  records that duress is on; the decoy records nothing.
- **Inside the decoy, setup is a harmless look-alike.** It only sets the flag in the decoy itself.
  It never writes the real slot's content and never tests a PIN against it: otherwise the decoy
  session would be an oracle for the real PIN (the same reason a PIN of another slot is a wrong
  PIN during re-authentication, ADR 0004).
- **Removing duress** refills slot B with random bytes of the same size.
- **Erase** deletes both slots, the attempts record, every wallet database and the Keystore keys.
- If the same PIN ever opened both slots, slot A wins; the setup prevents it by comparing the two
  PINs directly.

## Threat model

- In scope: an attacker who watches the user unlock and then explores the decoy in the app.
- Out of scope: device forensics. Two wallet directories exist once a decoy has been used, and a
  forensic examiner could notice that the decoy's database is newer or smaller. A study wallet does
  not try to defeat that.
- The decoy must look used: the user should fund it from a faucet and make a few transactions.

## Consequences

- Every wallet feature is slot-agnostic, so the decoy gets the same screens (Settings, Vault, …).
- A "duress PIN" set from inside the decoy does not work at the lock screen; an attacker who tries
  it learns nothing about the real PIN.
