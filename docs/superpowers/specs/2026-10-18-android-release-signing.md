# Android Release Signing Pipeline

Date: 2026-10-18
Phase: 109 (docs/LONG_RANGE_PLAN.md:284-287)

## Goal

Release build signed via CI secrets; SHA256SUMS per release automated.
DoD: `docs/releases/` gains sums for the next release automatically.

## Design

### Signing config (gradle, machine-independent)

`app/build.gradle.kts` reads four environment variables and only applies a signing config when
**all four** are present. Anything missing → the release build stays the standard unsigned Gradle
artifact, exactly as before. No machine paths, no keystore material, ever:

| Variable | Meaning |
| --- | --- |
| `OJ_NEXUS_KEYSTORE_PATH` | absolute path to the .jks/.pfx keystore file (outside the repo) |
| `OJ_NEXUS_KEYSTORE_PASSWORD` | keystore password |
| `OJ_NEXUS_KEY_ALIAS` | key alias inside the keystore |
| `OJ_NEXUS_KEY_PASSWORD` | key password |

### CI wiring (secrets only)

`.github/workflows/android.yml` gains a `release` job that:

1. Decodes `secrets.ANDROID_KEYSTORE_BASE64` to `$RUNNER_TEMP/oj-nexus-release.jks` (never
   inside the checkout — `RUNNER_TEMP` is wiped per job and never uploaded).
2. Exports the four variables + runs `./gradlew assembleRelease`.
3. Verifies the signature with `apksigner verify --verbose` (fails the job on an unsigned APK
   when secrets are configured).
4. Computes `SHA256SUMS-v<version>.txt` via `sha256sum` and uploads APK + sums as the
   `release-apk` artifact.

The local gate on this Windows host runs the **unsigned** path end-to-end (secrets absent →
`SIGNING: SKIPPED` behavior) because no release keystore is configured here; the signed path is
exercised by CI once the four secrets + base64 keystore are added. The debug keystore used for
the v0.3.74 direct-install artifact remains a machine-local helper outside the repo.

### Sums automation

The sums file is produced by the same `release` job (one line per artifact, `sha256sum` format,
matching the existing `docs/releases/SHA256SUMS-v*.txt` convention). Committing it back to the
repo stays a maintainer step — CI never pushes.

## Out of scope

- Play App Bundle (.aab) signing — the plan names the APK.
- Keystore generation/backup documentation beyond the secret names above (material never enters
  the repo).
