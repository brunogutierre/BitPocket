# BitPocket

[![CI](https://github.com/brunogutierre/BitPocket/actions/workflows/ci.yml/badge.svg)](https://github.com/brunogutierre/BitPocket/actions/workflows/ci.yml)

A layered-security Bitcoin wallet for Android, built as a study project.

> [!WARNING]
> **Study project — runs on Signet/Testnet4 only; mainnet is disabled by design; not audited.**
> Never use it with real funds.

## Goals

- Explore how an Android wallet can protect users in layers: key storage, spending limits,
  coercion resistance, inheritance and privacy.
- Keep the architecture simple, modern and explicit, with every decision documented.

## Planned features

- **Two pockets**: *Spending* (hot, small amounts) and *Vault* (stricter rules, larger amounts).
- **Inheritance with heartbeat**: funds become recoverable by a beneficiary if the owner stops
  checking in.
- **Duress PIN + decoy wallet**: an alternate PIN opens a plausible decoy.
- **Guardrails**: spending limits, delays and confirmations for risky transactions.
- **Address fingerprint**: a visual/verbal fingerprint to help verify addresses.
- **Security health score**: a checklist that grades the wallet setup.
- **Privacy**: coin control, BIP329 label import/export, custom Electrum/Esplora server.

## Tech stack

| Area          | Choice                                              |
|---------------|-----------------------------------------------------|
| Language      | Kotlin 2.4 (AGP built-in Kotlin), JVM target 17     |
| UI            | Jetpack Compose + Material 3                        |
| Build         | Gradle 9.8 (Kotlin DSL, version catalog), AGP 9.4   |
| Android       | minSdk 30, target/compile SDK 37                    |
| Bitcoin       | BDK 3.1.0 (`bdk-android` in `:app`, `bdk-jvm` for `:core` tests) |
| Chain backend | Esplora (mempool.space Signet/Testnet4)             |
| Storage       | Encrypted slot files (Argon2id + AES-GCM + Keystore); watch-only wallets in SQLite |
| Tests         | JUnit 6 (Jupiter), Kover (>= 80% line coverage)     |
| Networks      | Signet (default) and Testnet4 only                  |

## Architecture

```
:app  (Android)   Compose screens -> ViewModels -> AppContainer (manual DI)
                  Keystore key wrapping, biometric seed cipher
   |
   v  plain Kotlin types only (PocketWallet, WalletAccess, ...)
:core (JVM)       slots + unlock (crypto), session, BIP39, descriptors,
                  BdkWallet (watch-only, SQLite) --ChainClient--> Esplora
```

- `:core` holds every rule that can run on the JVM and is tested there with the real BDK
  (`bdk-jvm`); `:app` only adds Android: UI, Keystore, biometrics and wiring.
- Secrets (seed, PIN-derived keys) live only in the encrypted slot files and, while unlocked, in
  memory. Wallet databases are watch-only.

## Decisions

| Decision | Why | Record |
|---|---|---|
| BDK as `compileOnly` in `:core`, `bdk-jvm` in tests | Fast JVM tests of real wallet logic | [ADR 0001](docs/adr/0001-bdk-on-jvm-for-core-tests.md) |
| Vault policy with explicit branch and guards | BDK pitfalls found in the M0 spike | [ADR 0002](docs/adr/0002-vault-policy-and-signing.md) |
| Mainnet impossible by types, CI and tests | Unaudited study wallet | [ADR 0003](docs/adr/0003-mainnet-disabled.md) |
| Watch-only wallets + short-lived signers, Esplora sync | Keys never persisted by BDK; no background access | [ADR 0004](docs/adr/0004-watch-only-wallets-and-esplora-sync.md) |
| Manual DI (`AppContainer`) | ~20 classes do not need a framework | — |

## Building

Requirements: JDK 17+ and the Android SDK (platform 37). Point Gradle to the SDK with
`local.properties` (`sdk.dir=/path/to/Android/Sdk`) or `ANDROID_HOME`.

```bash
./gradlew assembleDebug                  # build the debug APK
./gradlew :core:test testDebugUnitTest lint  # unit tests and lint
./gradlew spotlessApply                  # format Kotlin sources (ktlint)
./gradlew koverVerify koverHtmlReport    # coverage gate (>= 80% lines)
./gradlew :core:test -Plive              # opt-in tests against public Signet servers (not in CI)
adb install app/build/outputs/apk/debug/app-debug.apk
```

CI (GitHub Actions) runs `spotlessCheck lint testDebugUnitTest koverVerify assembleDebug`
on every pull request and push to `main`.

## Roadmap

- [x] M0 — Bootstrap: build, CI, coverage, `:core` module and BDK de-risking spikes
- [ ] M1 — Spending wallet: encrypted storage, PIN, onboarding, sync, receive, send with biometrics, history, auto-lock
- [ ] M2 — Safety UX: address fingerprint, guardrails, clipboard/poisoning warnings
- [ ] M3 — Vault: miniscript 2-of-2 with offline recovery key, encrypted descriptor backup
- [ ] M4 — Inheritance: heir timelock path and heartbeat refresh
- [ ] M5 — Duress PIN and decoy wallet
- [ ] M6 — Privacy: custom server, coin control, BIP329 labels
- [ ] M7 — Security health score and final documentation

## License

[MIT](LICENSE) © 2026 Bruno Gutierre
