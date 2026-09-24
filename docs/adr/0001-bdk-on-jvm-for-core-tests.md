# 1. BDK on the JVM for `:core` tests

- Status: Accepted
- Date: 2026-09-24

## Context

Wallet logic (descriptors, policies, PSBT signing) should be tested quickly, without an emulator.
BDK ships Kotlin bindings (UniFFI) in two flavors with the same `org.bitcoindevkit` API:
`bdk-android` (AAR with Android `.so` files) and `bdk-jvm` (JAR with desktop natives for Linux,
macOS and Windows). UniFFI checks at load time that the bindings and the native library come from
the same build.

## Decision

- `:core` is a pure Kotlin/JVM module. It depends on BDK as `compileOnly`; its tests use
  `bdk-jvm`, and `:app` provides `bdk-android` at runtime.
- Both artifacts are pinned to one version catalog entry (`bdk`, currently 3.1.0).
- Tests run on JUnit Jupiter with `--enable-native-access=ALL-UNNAMED` (JNA on JDK 22+).
- UI-facing ports (`WalletPort`, `SyncPort`) use plain Kotlin types only: ViewModels never see BDK
  types, so they can be tested with fakes and BDK stays replaceable.

## Consequences

- `:core` tests exercise the real Rust code (BDK, rust-miniscript) on the build machine, including
  CI (Linux x86_64).
- Android-only behavior (JNA on ART, `.so` packaging) is not covered by these tests.
- Upgrading BDK means changing one line, but both flavors must be published for that version.
- `bdk-android` 3.1.0 adds `android.permission.INTERNET` through manifest merging and pulls JNA
  5.14.0, whose AAR also ships `.so` files for obsolete ABIs; `abiFilters` keeps only
  arm64-v8a and x86_64 (debug APK: about 63 MB, mostly `libbdkffi.so`).
- 16 KB page sizes (checked on the debug APK): the zip is 16 KB-aligned (`zipalign -c -P 16`).
  ELF LOAD alignment: `libbdkffi.so` is 16 KB on arm64-v8a and x86_64, and JNA's arm64
  `libjnidispatch.so` is 64 KB. JNA's x86_64 `libjnidispatch.so` is only 4 KB-aligned, which
  affects x86_64 devices/emulators with 16 KB pages until BDK moves to a newer JNA.
