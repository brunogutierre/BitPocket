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
| Tests         | JUnit 6 (Jupiter), Kover (>= 80% line coverage)     |
| Bitcoin       | Bitcoin Dev Kit (bdk-android) — *planned in M0*     |
| Networks      | Signet (default) and Testnet4 only                  |

## Building

Requirements: JDK 17+ and the Android SDK (platform 37). Point Gradle to the SDK with
`local.properties` (`sdk.dir=/path/to/Android/Sdk`) or `ANDROID_HOME`.

```bash
./gradlew assembleDebug                  # build the debug APK
./gradlew testDebugUnitTest lint         # unit tests and lint
./gradlew spotlessApply                  # format Kotlin sources (ktlint)
./gradlew koverVerify koverHtmlReport    # coverage gate (>= 80% lines)
adb install app/build/outputs/apk/debug/app-debug.apk
```

CI (GitHub Actions) runs `spotlessCheck lint testDebugUnitTest koverVerify assembleDebug`
on every pull request and push to `main`.

## Roadmap

- [ ] M0 — Bootstrap: build, CI, coverage, `:core` module and BDK de-risking spikes
- [ ] M1 — Spending wallet: encrypted storage, PIN, onboarding, sync, receive, send with biometrics, history, auto-lock
- [ ] M2 — Safety UX: address fingerprint, guardrails, clipboard/poisoning warnings
- [ ] M3 — Vault: miniscript 2-of-2 with offline recovery key, encrypted descriptor backup
- [ ] M4 — Inheritance: heir timelock path and heartbeat refresh
- [ ] M5 — Duress PIN and decoy wallet
- [ ] M6 — Privacy: custom server, coin control, BIP329 labels
- [ ] M7 — Security health score and final documentation

## License

[MIT](LICENSE) © 2026 Bruno Gutierre
