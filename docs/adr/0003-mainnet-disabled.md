# 3. Mainnet disabled by design

- Status: Accepted
- Date: 2026-09-24

## Context

BitPocket is an unaudited study wallet. A bug must never put real funds at risk, so mainnet
support has to be structurally impossible, not just hidden in the UI.

## Decision

Three layers:

1. **Types**: the app uses its own `SupportedNetwork` enum (`SIGNET`, `TESTNET4`; default Signet).
   Its `toBdkNetwork()` is the only mapping to BDK's `Network`, and keys are always
   `NetworkKind.TEST` (`tprv`/`tpub`, coin type 1).
2. **CI guard**: `scripts/check-no-mainnet.sh` fails the build if `Network.BITCOIN` or
   `NetworkKind.MAIN` appears in any `src/main` Kotlin file other than `SupportedNetwork.kt`.
3. **Tests**: unit tests assert that every `SupportedNetwork` maps to a test network, and that
   derived keys and addresses are never `xprv` or `bc1`.

## Consequences

- Adding mainnet would take deliberate changes in all three layers, visible in review.
- Test code may still reference mainnet types to assert their absence (`src/test` is not scanned).
