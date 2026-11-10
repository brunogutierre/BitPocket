# 8. Heartbeat refresh and heir claim

- Status: Accepted
- Date: 2026-11-09

## Context

The Vault's heir branch (`and_v(v:pkh(HEIR),older(N))`) is a **relative** timelock: each coin
matures for the heir N blocks after it confirms. An owner who wants to keep full control must move
the coins before that; an heir must be able to claim them afterwards without the owner's help.

## Decision

- **Heartbeat status.** `Heartbeat.of(tip, coin heights, N)` looks at the oldest confirmed Vault
  coin: OK, due soon (the last tenth of N, at least 144 blocks but never more than half of N), or
  expired (the heir can already spend). Pending coins have not started counting. It is shown on the
  Vault screen, and Home warns when it is due soon or expired. There is no background job or
  notification: a background task cannot open the encrypted wallet, and a "Vault due" notification
  would reveal the real wallet while a decoy is open (M5).
- **Refresh = 2-of-2 self-transfer.** "Refresh the Vault" is a Vault spend of everything to a new
  Vault address, signed with the phone and the recovery words. The timer restarts only when the
  refresh **confirms**.
- **Heir claim** (M4 `feat/heir-claim-*`): from Welcome or Settings, with no wallet session. The heir
  opens the Vault backup with their own words (ADR 0007), the app rebuilds an in-memory watch-only
  wallet, full-scans it and sweeps only coins with at least N confirmations (`excludeBelowConfirmations`
  plus a per-input check), using the heir branch with `policyPath` on both keychains. Before
  broadcast, every input must carry nSequence = N and one signature (ADR 0002).

## Consequences

- Owners must open the app and refresh before the deadline; the wallet cannot remind them while
  closed. The health score (M7) includes the heartbeat.
- A refresh costs a transaction fee and needs the recovery words each time.
- On Testnet4, block times are erratic, so "days" are only indicative; Signet is the reference.
