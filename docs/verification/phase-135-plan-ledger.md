# Phase 135 — Plan Ledger

Working record of the phase-135 arc (Apple document exchange). Rows flip
only on observed, verifiable events; PENDING rows name the event that will
flip them.

Branch: `codex/phase-135-apple-document-exchange` (SDD + implementation,
merged to main via PR #82 `d327baf` and PR #83 `8175fdd`); closeout on
`codex/phase-135-closeout`. Baseline main: `ce743f0`.

| # | Task | Evidence | Status |
|---|------|----------|--------|
| 1 | SDD + implementation plan merged | commit `698dcc3` (design from the read-only apple/ recon); PR #82 CI all checks success; merged as `d327baf` | merged (PR #82) |
| 2 | T1 portable models + strict JSON codec (hand-written scanner/canonical writer) | commits `babe952`/`ab10b0a`; CI `swift test` green incl. compile of the new sources; audit replica clean | merged (PR #83) |
| 3 | T2 codec schema walk + T3 file-based archive store + repository orchestration | commit `77e7611`; actor store over `portable-archive.json` on the 130A atomic machinery; CI green | merged (PR #83) |
| 4 | T4 UI (PORTABLE page, first .fileImporter/.fileExporter with security-scoped lifecycle) + T5 tests (16) + simulator dual-suite extension | commits `a247ed3`/`08579cc`; swift test green with the new tests on macOS (pinned golden identity `81bb13c1…d44` reproduced); CI audit clean | merged (PR #83) |
| 5 | Platform repairs during CI (honest history) | `19d4e72` macos-14 runner retired by GitHub → macos-15; `1342879` dayKey validator position bug (dashes at 4/7) caught by CI tests; `b49c72a` xcresult `test-results tests` API switch (new Xcode summary is aggregate-only) + generic tree walker, self-test 10/10+5/5 | merged (PR #83) |
| 6 | G-P delivery gate | PR #83 all six checks success: boundary audit, `swift test` (macOS golden round trip = pinned identity), both product builds, iOS simulator dual suites (recovery 7/7 + PortableExchangeTests) with per-suite summaries; merged as `8175fdd` | merged (PR #83) |
| 7 | T6 independent review (fresh-context reviewer, read-only; codec parity vs check.py/PortableJson.cs; repository/store soundness; UI lifecycle; test reality) | [phase-135-task6-review.md](phase-135-task6-review.md): PASS-WITH-OBSERVATIONS, 12 hand-derived vectors MATCH; F1–F3/F5–F7 fixed in PR #85 (`720bc40`/`3d5b614`/`d5d7255`), F4 recorded overflow deviation; PR #85 CI all eight checks success and merged as `1a2ab03` | merged (PR #85) |
| 8 | T6 closeout: verification record, this ledger, roadmap tick, closeout PR to `main` | PENDING — flips on the closeout PR merge | pending |

## Deviations

- The plan sequenced per-task PRs to an integration branch; execution stacked
  tasks on the single integration branch with per-wave PRs to `main` (PR #82
  SDD, PR #83 implementation) — CI gates ran identically, file ownership was
  respected.
- No Swift toolchain exists on the Windows host; every compile/test/simulator
  gate ran in CI `apple.yml` (macos-15) per the design's verification shape.
- Two hosted-platform failures were repaired during the arc (runner label
  retirement; xcresult API split) — both are recorded in row 5 and in the
  verification record's honest history.
