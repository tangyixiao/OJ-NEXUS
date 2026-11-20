# PHASE_135_HANDOFF — Apple Document Exchange

Status: **VERIFIED** (PR #83 `8175fdd` + review dispositions PR #85 `1a2ab03`).
Authoritative record: `docs/verification/phase-135-apple-document-exchange.md`;
per-task evidence: `docs/verification/phase-135-plan-ledger.md`; review:
`docs/verification/phase-135-task6-review.md`.

## 1. What shipped

The phase-131 portable contract now has a fourth native consumer, under
`apple/Sources/OJNexusCore/Portable/` and one new PORTABLE page:

- **Strict JSON codec** (`PortableJson.swift`): hand-written byte-level
  scanner (rejects BOM, duplicate keys, surrogate halves, invalid UTF-8;
  numbers carried verbatim) and a canonical writer whose key ordering is
  UTF-8 byte order — deliberately NOT `String` `<` (Swift's canonical
  equivalence would order `é` differently from Python's code-point sort).
  No `JSONSerialization`/`JSONDecoder` anywhere in the portable path.
- **Contract walk** (`PortableCodec.swift`): schema precedence mirrors
  `check.py` line-for-line; patterns are hand-validated (`$` kept raw for
  PATH_UNSAFE, translated to `\z` for schema rules; `\d`→`[0-9]`); counts
  overflow skips comparison instead of trapping (recorded deviation, F4).
- **Archive store** (`PortableArchiveStore.swift`): one versioned document
  (`portable-archive.json`, formatVersion 1) on the 130A atomic machinery
  (flock + raw-byte CAS + staged install + snapshot-first backup). Guarded
  startup performs zero writes; recovery quarantines a corrupt main under a
  unique no-clobber name and installs the backup.
- **Repository** (`PortableRepository.swift`): actor; preview/apply/export/
  cancel with cooperative generation checkpoints; re-import of exported
  bytes is an identical no-op.
- **UI** (`NexusRootView` PORTABLE page): the package's first
  `.fileImporter`/`.fileExporter` with security-scoped lifecycle; every
  authorization-cancel / unreadable-file / write-failure is a typed state
  line, never a silent partial import.

## 2. Gates (all CI-measured, macos-15)

- Boundary audit clean on every head; `swift test` green (133 tests incl.
  30 PortableExchangeTests) with the mixed golden `pw-p01` round trip
  reproducing the pinned identity `81bb13c1…d44`.
- Both product builds green; iOS simulator dual suites (recovery + portable)
  green with per-suite JSON summaries on an installed simulator.
- Independent review PASS-WITH-OBSERVATIONS; F1–F3/F5–F7 fixed in PR #85,
  F4 accepted as the recorded overflow deviation.

## 3. Gotchas for the next agent

- **GitHub retired the `macos-14` runner label** mid-arc; `apple.yml` now
  pins `macos-15` (`19d4e72`). Future label retirements cancel runs in ~1s —
  check run conclusions, not just check-rollup lag.
- **New Xcode splits xcresulttool**: `test-results summary` is aggregate-only;
  per-suite trees come from `test-results tests` read by a generic walker
  (`b49c72a`). If Apple bumps Xcode again and the gate reports "suite not
  present" while the suite passed, suspect the shape again — the walker's
  self-test (`selftest` in the script) pins the current one.
- **`String` `<` is not a code-point sort in Swift** (canonical equivalence).
  Any new key ordering must go through UTF-8 byte comparison like
  `PortableJson.canonicalBytes`.
- **Swift actors**: a method that hops to another actor must be `async`;
  non-`async` actor methods cannot `await` (cost one CI iteration).
- Test-fixture parity: `ProblemEntity`/`applyRemoteMetadata` gained
  `titleLower` — the compiler forces every construction site (24 test sites
  updated in PR #84's branch, see phase 137 for the same pattern).

## 4. Open work / next phases

- Phase 136 (problem identity contract) is merged and VERIFIED; phase 137
  (Android bounded library query) is in flight on PR #84.
- Phases 138–140 (Windows/Linux/Apple problem library) consume 136's
  contract vectors; Apple's library work (140) should reuse
  `PortableJson`'s byte-order comparator where search/sort parity matters.
- Interactive macOS/iOS device acceptance remains a recorded debt of phases
  125/130 (no host hardware); CI remains the verification carrier.
