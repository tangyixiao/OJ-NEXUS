# Phase 135 — Apple Document Exchange — Implementation Plan

Status: ACTIVE. Binds the design
(`specs/2026-11-20-phase-135-apple-document-exchange-design.md`) to ordered,
verifiable tasks. Single integration branch
`codex/phase-135-apple-document-exchange`; every task ends in a PR to it; the
arc ends with a delivery PR to `main`.

Baseline main: `ce743f0`.

## Task waves

### Wave 1 (controller-authored; this host has no Swift toolchain — CI is the verification vehicle, per `apple.yml` on macos-14)

- **T1 — portable models + strict JSON codec**
  (`apple/Sources/OJNexusCore/Portable/PortableModels.swift`,
  `PortableJson.swift`): value tree with verbatim number literals; strict
  decode (BOM/trailing data/duplicate keys/NaN/Infinity/lone surrogates,
  depth ≤ 200); canonical writer (code-point-sorted keys, minimal form).
  No use of JSONSerialization/JSONDecoder. No banned-audit substrings.
- **T2 — portable codec + file-based archive store**
  (`PortableCodec.swift`, `PortableArchiveStore.swift`): schema walk with the
  frozen precedence (required → closed → props → size/extra/credentials/path
  rules), `$`→`\z` translations; `actor PortableArchiveStore` over one
  `portable-archive.json` (formatVersion 1, tiered arrays) written via
  internal `WorkspaceFileIO` (flock + staged install/rename); guarded
  read-only startup; typed failures extending `WorkspaceStorageFailure` (new
  cases mirror the non-raw display-label shape; exhaustive switches updated).

### Wave 2

- **T3 — repository orchestration** (`PortableRepository.swift`): preview →
  explicit apply → cancel (later intent supersedes an in-flight one; cancel
  is a no-op with none), lossless export; counts per tier reported for
  records that have no editing UI yet.

### Wave 3

- **T4 — UI surface** (`NexusDashboardModel.swift` portable section,
  `NexusRootView.swift` new `NexusPage.portable` case + detail): `.fileImporter`
  with security-scoped lifecycle (cancel/invalid URL → typed status lines),
  `.fileExporter` with write-failure status; no color-only states; audit-clean.
- **T5 — tests + simulator gate**
  (`apple/Tests/OJNexusCoreTests/PortableExchangeTests.swift` inline-literal
  fixtures; `apple/scripts/test-ios-recovery.sh` + `validate_ios_recovery.py`
  extended additively): golden round trip with pinned identity
  `81bb13c1…d44`, rejection matrix, authorization/write-failure states,
  wrong-kind snapshot rejection; macOS evidence via `swift test`, iOS
  evidence via the extended simulator run (second summary file).

### Wave 4

- **T6 — independent review + closeout**: review record
  (`docs/verification/phase-135-task-review.md`), verification record
  (`docs/verification/phase-135-apple-document-exchange.md`), plan ledger
  (`docs/verification/phase-135-plan-ledger.md`), roadmap tick, delivery PR
  to `main`.

## File ownership (exclusive)

- T1-T3: `apple/Sources/OJNexusCore/Portable/**`.
- T4: `apple/Sources/OJNexusUI/**` (+ the two failure-enum touchpoints in
  Core that T2 introduces).
- T5: `apple/Tests/**`, `apple/scripts/**`.
- T6: `docs/verification/phase-135-*`, `docs/LONG_RANGE_PLAN_121_220.md`.

## Acceptance (G-P, measured)

- CI `apple.yml` on the delivery head: boundary audit clean, `swift test`
  green (macOS golden round trip incl. pinned identity), both product builds
  green, extended simulator run green with the portable summary in the
  evidence artifact (iOS round trip).
- Every roadmap acceptance clause mapped to at least one test (mixed golden
  lossless round trip macOS+iOS; no-edit records archived and reported;
  authorization cancel / invalid URL / write failure states; out-of-schema
  fields and unsupported versions rejected).
- Local-safety: no local snapshot behavior change — existing recovery tests
  untouched and green.
