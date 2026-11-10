# 10. Custom server: Esplora only, with a genesis check

- Status: Accepted
- Date: 2026-11-09

## Context

Every sync tells the chain backend which addresses belong to the wallet. The default backend is
mempool.space, a third party. M6 lets users point the wallet to their own server. BDK supports both
Esplora (HTTP) and Electrum (TCP/TLS).

## Decision

- **Esplora only.** One adapter (`EsploraChainClient`), one code path, one live test. Electrum is
  rejected for now: a second backend to maintain for the same purpose.
- **Checked before saving** (`EsploraServer.check`): the URL must be `https://` with a host
  (addresses must not travel in clear text), and the server must report the wallet network's
  genesis block hash (kept in `SupportedNetwork`, next to the other network constants). A mainnet
  or other-network server is refused.
- **Stored in the slot** as a settings record (ADR 0005), changed after the PIN, used from the next
  unlock (the open pockets keep their current client).

## Consequences

- The genesis check proves the network, not the server's honesty: a malicious server could still
  hide transactions or report a wrong tip, which matters for the guardrails' block-height delays
  (ADR 0006). Header verification or compact block filters (BDK's Kyoto) are out of scope.
- Fee estimates come from the same server (`/fee-estimates`), so a custom server also controls the
  offered fee rates, which are clamped to 1–500 sat/vB.
