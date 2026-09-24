# 2. Vault policy and signing with BDK

- Status: Accepted (M0 gate: **GO with changes**)
- Date: 2026-09-24

## Context

The Vault pocket needs 2-of-2 spending (PHONE + RECOVERY) at any time, and a single-key heir
fallback after about a year without movement. Before building on it, a spike
(`core/src/test/kotlin/.../spike/VaultSpikeTest.kt`) checked this against BDK 3.1.0 Kotlin
bindings, fully offline.

## Decision

Policy (P2WSH, test keys at BIP48 `m/48'/1'/0'/2'`, N = 52560 blocks, about one year):

```
wsh(or_d(multi(2,PHONE,RECOVERY),and_v(v:pkh(HEIR),older(N))))
```

What worked, and the APIs used:

- **Parsing and multipath**: `Descriptor(string, NetworkKind.TEST)` accepts the miniscript. A
  BIP389 `<0;1>/*` descriptor works for a **watch-only** wallet
  (`Wallet.createFromTwoPathDescriptor` / `loadFromTwoPathDescriptor`). Multipath with private
  keys is rejected by rust-miniscript, so wallets holding keys use one descriptor per keychain
  (`Wallet(external, internal, ...)`).
- **Policy path**: `Wallet.policies(keychain)` exposes `or_d` as a 1-of-2 `SatisfiableItem.Thresh`
  (index 0 = 2-of-2, index 1 = heir). `TxBuilder.policyPath(mapOf(policy.id() to listOf(i)),
  keychain)` must be set for **both** keychains, or `finish` fails with
  `CreateTxException.SpendingPolicyRequired`.
- **2-of-2**: the phone wallet builds the PSBT and signs; `Wallet.sign` returns `false` (not final).
  The recovery wallet signs the same PSBT and `sign` returns `true`; `Psbt.extractTx()` succeeds.
- **External signers**: a watch-only wallet can sign with
  `Wallet.signWithSigners(psbt, SignersContainer.fromDescriptor(...))`. Private keys can then stay
  out of the persisted wallet and live only during signing.
- **Heir**: with the heir branch selected, `TxBuilder` sets nSequence = N and the heir wallet alone
  finalizes.
- **Offline funding**: `Update` has no public constructor. Confirmed coins are injected by loading a
  wallet (`Wallet.load`) from a hand-built `ChangeSet` (`ChangeSet.fromAggregate` with a local chain
  and an `Anchor`) through `Persister.custom`.

Required changes to the plan:

1. **Never finalize spends of unconfirmed Vault coins.** `finalize_psbt` panics with "Overflowing
   addition" for an unconfirmed UTXO of a descriptor with `older()`
   ([bdk_wallet#557](https://github.com/bitcoindevkit/bdk_wallet/issues/557)). The bindings are
   built with `panic = "abort"` ([bdk-ffi#1064](https://github.com/bitcoindevkit/bdk-ffi/issues/1064)),
   so this kills the process instead of throwing. Vault spends use `TxBuilder.excludeUnconfirmed()`
   and a pre-check, until upstream fixes it.
2. **The app gates heir maturity itself.** BDK's finalizer accepts `older(N)` when the PSBT's
   nSequence >= N, whatever the coin's age, so only consensus rejects premature heir transactions.
3. **Always select the branch explicitly, then verify the result.** If the heir PSBT is built with
   the 2-of-2 branch past maturity, BDK finalizes through the heir branch without nSequence = N,
   producing a transaction that fails `OP_CHECKSEQUENCEVERIFY`. Before broadcast, check that
   nSequence matches the chosen branch.

## Gate review

The `manager` accepted **GO with changes** and assigned the guards to planned work:

- M1 `feat/send-build`: a `SpendGuard` in `:core` for every spend (Spending and Vault) that
  excludes unconfirmed coins and checks inputs before finalizing.
- M3 `feat/vault-policy`: keys at BIP48 `m/48'/1'/0'/2'`; one descriptor per keychain for signing,
  multipath only for watch-only sync and backup export; the phone signs through
  `signWithSigners`, so the BDK wallet stays watch-only.
- M3 `feat/vault-wallet`: explicit `policyPath` for both keychains and a pre-broadcast
  verification of branch, nSequence and signature count.
- M4 heartbeat and heir claim: the heir path stays blocked until every input has at least N
  confirmations.
- The open upstream issues above are rechecked on every BDK upgrade.

## Consequences

- The Vault design stays as planned, with the three guards above in the Vault signing code.
- Use a watch-only multipath wallet for sync and balance, and per-device key material (or
  `SignersContainer`) for signing.
- The heir timelock is relative (per coin): any 2-of-2 spend (for example a periodic
  "heartbeat" self-transfer) restarts it.
- Revisit this ADR on BDK upgrades: the spike tests fail loudly if these behaviors change.
