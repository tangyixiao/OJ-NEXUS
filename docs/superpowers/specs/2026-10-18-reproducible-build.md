# Reproducible build check — Spec

Date: 2026-10-18
Phase: 111 (docs/LONG_RANGE_PLAN.md:292-294)

## Goal

Document and script a build-reproducibility check for the APK.
DoD: two clean builds produce identical hashes or documented diff sources.

## Approach

`tools/check-reproducible-build.ps1`:

1. Runs `gradlew-local.bat clean` (or an out-of-tree build dir) then
   `assembleRelease` twice into two separate output directories via
   `-Pcm.buildDir`-style redirection — simplest honest mechanism: build once, copy the APK out,
   clean, build again, copy again.
2. Compares SHA256 of `app-release-unsigned.apk` (or the signed `app-release.apk` when the
   signing env vars are set) across the two runs.
3. Prints `REPRODUCIBLE: YES sha256=<hash>` or
   `REPRODUCIBLE: NO` + a per-entry hash diff table.

## Known diff sources on this toolchain (documented, honest)

- Gradle/AGP embed a build timestamp? — no: AGP output is deterministic for identical inputs
  since AGP 4.x, **but** ZIP entry order and `META-INF/MANIFEST.MF` content can differ when the
  *file system ordering* differs. Same machine, same source tree → stable.
- The debug keystore is machine-local; `assembleDebug` is NOT expected to be reproducible across
  machines and is excluded from the check (release unsigned output has no signature block).
- Kotlin/AGP caches (`~/.gradle/caches`) change between dependency upgrades only, not between
  clean builds of the same lockstate.
- `versionName`/`versionCode` come from `app/build.gradle.kts` — identical inputs ⇒ identical
  manifest.

Expected verdict on this host: identical hashes for two consecutive `clean assembleRelease`
runs. If a future toolchain upgrade breaks it, the script's diff table names the differing zip
entries so the source can be documented instead of guessed.
