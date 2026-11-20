# Phase 135 — Apple Document Exchange

Status: **VERIFIED**. Every gate below is measured on the recorded commit or
CI run, including the independent review and its dispositioned fixes
(PR #85, merged as `1a2ab03`). Authoritative inputs:
[Design](../superpowers/specs/2026-11-20-phase-135-apple-document-exchange-design.md),
[Plan](../superpowers/plans/2026-11-20-phase-135-apple-document-exchange.md),
[Plan ledger](phase-135-plan-ledger.md),
[Review record](phase-135-task6-review.md).

Baseline main: `ce743f0`. Integration branch:
`codex/phase-135-apple-document-exchange` (PR #82 SDD `d327baf`; PR #83
implementation `8175fdd`; PR #85 review dispositions `1a2ab03`); closeout on
`codex/phase-135-closeout`.

## Scope and inputs

Binds the frozen phase-131 portable contract to the Apple clients: a
hand-written strict JSON codec (no JSONSerialization/JSONDecoder in the
portable path), contract schema walk, file-based tiered archive store on the
130A atomic machinery (flock + CAS + staged install/rename + snapshot-first
backup), repository orchestration with cooperative cancellation, and a
PORTABLE page on macOS/iOS using the platform document interfaces with the
security-scoped lifecycle. Out of scope: local snapshot changes, sync
transport, problem library, interactive device acceptance (recorded debts of
phases 125/130), packaging/signing.

## Gate history

| # | Gate | Requirement | Result |
|---|------|-------------|--------|
| 1 | Source boundary audit | no banned substrings in all Apple sources/tests | PASS — CI audit green; local replica clean on every pushed head |
| 2 | `swift test` (macOS) | full suite green incl. 16 PortableExchangeTests | PASS — mixed golden `pw-p01` round trip reproduces the pinned identity `81bb13c1…d44`; rejection matrix (unsupported version, unknown fields, duplicate ids, missing references, counts mismatch, path-unsafe extras, credential-shaped keys, fractional difficulty); archive integrity tamper detection; typed empty/cancelled states |
| 3 | Product builds | OJNexusMacOS + OJNexusIOS compile | PASS — CI builds green (4m24s windows-equivalent n/a; apple job step green on macos-15) |
| 4 | iOS simulator | recovery + portable suites execute with zero failures | PASS — extended script ran both suites with per-suite summaries (`ios-recovery-summary.json` + `ios-portable-summary.json`) on an installed iOS simulator |
| 5 | No local snapshot behavior change | recovery tests untouched and green | PASS — WorkspaceRecoveryTests 7/7 on the simulator; suite source untouched |
| 6 | Independent review | findings fixed or dispositioned | PASS — [phase-135-task6-review.md](phase-135-task6-review.md): PASS-WITH-OBSERVATIONS; 12 hand-derived parity vectors MATCH; F1–F3 and F5–F7 fixed (PR #85: `720bc40`, `3d5b614`, `d5d7255`), F4 accepted as the recorded overflow deviation; merged as `1a2ab03` |

## Honest history

- **GitHub retired the `macos-14` hosted runner** mid-arc (every Apple run
  cancelled in ~1s: "configuration macos-14-arm64-us-default is not
  supported"). Repaired by moving `apple.yml` to `macos-15` (`19d4e72`).
- **The new Xcode toolchain split xcresulttool**: `test-results summary` is
  aggregate-only (no suite names), which made the per-suite gate report
  "suite not present" while the suite itself passed 7/7. Repaired by reading
  the per-suite tree from `test-results tests` with a generic walker
  (`b49c72a`, self-test 10/10 + 5/5).
- Four Swift compile iterations were required before the first green test run
  (UInt32 ascii initializer, actor await hops, conditional binding of a
  non-optional, withLock argument label) — each caught by CI, each a one-line
  class of fix.
- One logic bug was caught by CI tests before any merge: the dayKey validator
  checked dash positions 2/5 instead of 4/7 (`1342879`), rejecting valid
  golden day keys.
- The review-disposition PR (PR #85) needed two CI-caught follow-ups: the
  F2 model tests required the export payload bytes to be visible to the test
  target (`3d5b614`), and the F1 recovery path missed the fresh-store
  missing-established case — the on-disk backup is the establishment
  evidence, exactly the house rule (`d5d7255`).

## Non-claims

No interactive macOS/iOS device acceptance (that debt remains recorded on
phases 125/130); no App Store packaging/signing; no local snapshot codec
changes; the problem library (137-140) is untouched. The pinned identity is
measured in CI, not on developer hardware.

<!-- CONTROLLER-SIGN-OFF: recorded by the controller on 2026-11-20 -->
VERIFIED. Phase 135 delivered to `main` via PR #83 (merge `8175fdd`) with
review dispositions via PR #85 (merge `1a2ab03`). Gates: boundary audit clean
on every head; `swift test` green on macOS with the mixed golden round trip
reproducing the pinned identity `81bb13c1…d44`; both product builds green on
macos-15; iOS simulator dual suites green with per-suite summaries;
independent review PASS-WITH-OBSERVATIONS with F1–F7 dispositioned (PR #85).
The pinned identity is CI-measured; interactive device acceptance remains a
recorded debt of phases 125/130.
