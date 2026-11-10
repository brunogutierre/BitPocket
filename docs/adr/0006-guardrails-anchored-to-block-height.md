# 6. Guardrails anchored to block height

- Status: Accepted
- Date: 2026-11-09

## Context

M2 adds spending guardrails: limits and extra confirmations that protect a user whose unlocked
phone is in someone else's hands (theft, coercion) or who is about to make a mistake. A limit that
can be raised and used in the same sitting protects nothing, so loosening needs a delay; the delay
needs a clock that the person holding the phone cannot move forward.

## Decision

- **Guardrails** (`Guardrails`): per-transaction limit, limit per 144 blocks (about 24 hours),
  confirm addresses never paid before, confirm spends over half the balance. Limits count what
  leaves the wallet (amount + fee). They are security settings stored in the slot (ADR 0005).
- **Tighten now, loosen later.** `GuardrailsPolicy.change` applies the strictest of the current
  and requested values at once and schedules the requested values for **144 blocks** after the
  synced tip. Tightening again cancels a pending loosening. Loosening needs a synced tip.
- **Block height, not wall time.** Both the window and the delay use the chain tip from the last
  sync: the device clock is easy to change, the chain is not.
- **Enforcement on Review.** After a plan is built, `GuardrailCheck` blocks it (showing what can
  still be sent) or asks to tick each confirmation before "Confirm and send". Recent spending comes
  from the wallet's own history (pending sends plus sends confirmed in the window).

## Consequences

- A thief with the PIN can still spend up to the limits, but not more until the delay ends.
- The tip comes from the chain backend: a malicious custom server (M6) could report a higher tip.
  M6 checks the server's genesis block; a full header check is out of scope.
- Testnet4 block times are erratic (sometimes minutes, sometimes far faster), so "about 24 hours"
  is only true on Signet.
- Rejected: wall-clock delays (trivially skipped by changing the time), and enforcing limits in
  `planSpend` (the UI needs the reason and the remaining allowance to explain itself).
