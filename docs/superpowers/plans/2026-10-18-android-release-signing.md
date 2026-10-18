# Android release signing pipeline — Plan

Date: 2026-10-18
Phase: 109 (docs/LONG_RANGE_PLAN.md:284-287)
Spec: docs/superpowers/specs/2026-10-18-android-release-signing.md

## Steps

1. Env-gated signing config in `app/build.gradle.kts` (all four `OJ_NEXUS_KEYSTORE_*` /
   `OJ_NEXUS_KEY_*` variables present → apply; else unsigned release exactly as before).
2. CI `release` job in `.github/workflows/android.yml`: decode base64 keystore to RUNNER_TEMP,
   build signed (or unsigned fallback), verify with apksigner, generate
   `SHA256SUMS-v<version>.txt`, upload both as the `release-apk` artifact.
3. Verify the unsigned path locally (no secrets on this host): `assembleRelease` still succeeds.
4. Docs: spec + ROADMAP section; note that committing the sums file into `docs/releases/` is a
   maintainer download-and-commit step (CI never pushes).

## Result

- `app/build.gradle.kts` release buildType gained the env-gated signing config; unsigned path
  verified locally with `assembleRelease` BUILD SUCCESSFUL → `app-release-unsigned.apk`.
- `.github/workflows/android.yml` gained the `release` job (decode → build → apksigner verify →
  sha256sum → upload). Unsigned fallback keeps CI green before secrets exist.
- Sums format matches the existing `docs/releases/SHA256SUMS-v*.txt` convention
  (`sha256sum` two-space digest + filename).

### Lessons

- Inside `signingConfigs.create { }` the receiver's `keyAlias`/`keyPassword` properties clash
  with the captured local vals of the same name — disambiguate with `this.keyAlias = keyAlias`.
- Gradle's unsigned release output is `app-release-unsigned.apk`; the CI steps glob
  `app-release*.apk` so both signed and unsigned names are handled.
