# Reproducible build check — Plan

Date: 2026-10-18
Phase: 111 (docs/LONG_RANGE_PLAN.md:292-294)
Spec: docs/superpowers/specs/2026-10-18-reproducible-build.md

## Steps

1. Write `tools/check-reproducible-build.ps1` — two `clean` + `:app:assembleRelease` passes,
   copies each APK out, compares SHA256, prints `REPRODUCIBLE: YES/NO` with a diff-source hint
   table on mismatch. `.reproducible-check/` scratch dir (NOT inside `app/build` — gradle clean
   wipes it mid-run; first run hit DirectoryNotFoundException for exactly that reason).
2. Run it on this host and record the verdict.

## Result

- Verdict on this host: **REPRODUCIBLE: YES** —
  `D411D62DDDF6CF2A8BA6FDA46FBBFD97D05AE05FCAB930760097E3593843A6D6` for both passes of
  `app-release-unsigned.apk` (unsigned output carries no signature block, so no machine keystore
  enters the hash).
- `assembleDebug` is excluded on purpose: the debug APK is signed with the machine-local debug
  keystore, so its hash is host-specific by design.
- On mismatch the script prints the documented diff sources (embedded timestamps, zip entry
  ordering / MANIFEST.MF, dependency cache drift) instead of leaving a bare failure.

### Lessons

- Scratch dirs for build comparisons must live outside `app/build` — any `gradle clean` between
  passes deletes them (DirectoryNotFoundException at the Copy-Item step was the tell).
