# OJ NEXUS — Long-Range Executable Plan (Phase 78 → Phase 120)

> Purpose: a far-horizon plan that ANY coding model can pick up and execute without
> re-discovering context. Read this file first, then `AGENTS.md`, then the relevant doc in
> `docs/`. Every phase below is written to be executed independently: scope, exact touchpoints,
> constraints, and Definition of Done are stated inline.

Last updated: 2026-10-17. Current identity: Android `versionName=0.3.75` / `versionCode=75`
(Phase 77 Note Index). Current branch: `codex/android-visual-hierarchy`.

---

## 0. HOW TO EXECUTE A PHASE (read before touching code)

1. Pick the NEXT unclaimed phase in this file (they are ordered; do not skip ahead unless the
   phase says "independent").
2. Read `AGENTS.md` (repo rules — binding) and the phase's `Refs:` docs.
3. Write a short spec to `docs/superpowers/specs/<date>-<slug>-design.md` and a plan to
   `docs/superpowers/plans/<date>-<slug>.md` BEFORE coding, matching the existing style.
4. Implement. Stay inside the phase's declared write scope. Obey AGENTS.md: no new deps without
   cause, no `Color(0xFF...)` in feature code, strings via `res/values/strings.xml`, dark-first
   NEXUS BLUE design, no credentials/cookies/passwords ever, Local First.
5. Gate (all must pass before committing):
   - `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL
   - `.\gradlew.bat test` → green (add/extend unit tests for core logic)
   - No TODO stubs; Loading/Empty/Error/Success handled where the phase touches UI.
6. Bump version: `versionCode = 75 + (phase - 77)` pattern already used; set
   `versionName=0.3.<versionCode>` in `app/build.gradle.kts`.
7. Append a `## PHASE <n> — <title>` entry to the TOP of `docs/ROADMAP.md` in the established
   bilingual style (English paragraph + `/` + Chinese paragraph, ending with the explicit
   "no network request / migration / credential flow ..." boundary sentence).
8. Commit with `feat:` / `fix:` / `docs:` prefix, one logical change per commit. Check
   `git status` for secrets before pushing. Never force push.
9. Mark the phase in THIS file: change `⬜` to `✅ (v0.3.<code>, <date>)`.

Environment notes (Windows host): JDK = Android Studio JBR via user-level
`~/.gradle/gradle.properties`; SDK = `D:\Android`; gradle wrapper uses the Tencent mirror.
Use `tools/gradlew-local.bat` if JAVA_HOME is unset. Apple phases require macOS — mark them
blocked, never fake their results.

---

## 1. STRATEGIC ARCS (2026 Q4 → 2027)

| Arc | Phases | Goal |
| --- | --- | --- |
| A. Personal mastery loop | 78–85 | Close the loop: notes → review → training → analytics, all local |
| B. Data integrity & portability | 86–90 | Export/import 2.0, per-judge data wipe, backup scheduling |
| C. Multi-OJ depth | 91–97 | Codeforces/AtCoder depth parity with Luogu; unified submissions detail |
| D. Windows/Apple parity | 98–103 | Windows client reaches Android read parity; Apple slice advances on macOS |
| E. Performance & scale | 104–108 | 10k-problem library, fast cold start, paging, index tuning |
| F. Platform polish & release | 109–115 | Signing, installer, store metadata, F-Droid-style reproducible build |
| G. Optional connected layer | 116–120 | Opt-in end-to-end-encrypted cross-device sync (design-gated; may stay cut) |

Hard invariants for ALL phases (from AGENTS.md, restated so executors can't miss them):
- No OJ passwords, cookies, sessions, CSRF login — public handles + Luogu OpenApp only.
- No AI/LLM features, no chat, no marketing copy, no emoji, no gradients/glow.
- Composables never touch network; StateFlow UI state; Local First; judge logic behind
  `JudgeAdapter`.
- Every phase ends green on `assembleDebug` + `test`. Never skip compilation, never fake
  results, never mock data pretending to be real API output.

---

## 2. PHASE LIST

### ARC A — Personal mastery loop

#### ✅ PHASE 78 — Note field templates (v0.3.76, 2026-10-17)
- Goal: structured notes per problem (ROOT CAUSE / KEY INSIGHT / PITFALL) with a per-user
  editable template applied to new notes.
- Scope: `feature/library` note editor, `core/model` note fields, DataStore for the template.
  Reuse existing `problem_notes` table — prefer new COLUMNS only if unavoidable; otherwise
  store the field marker inline (documented format), NO migration if avoidable.
- Refs: `docs/ROADMAP.md` Phase 77, `docs/DATABASE.md`.
- Constraints: local-only; no network; no Room version bump unless justified in the spec.
- DoD: template applied on new note; existing notes untouched; unit test on template fill.

#### ✅ PHASE 79 — Review deck shuffle & cram mode (v0.3.77, 2026-10-18)
- Goal: Review Run gains deterministic shuffle (seeded by date) and a CRAM mode that ignores
  schedule and takes a user-picked judge+tag slice.
- Scope: `feature/training` review run screen + view model.
- Constraints: reuses existing review transaction; no schema change; reduced-motion safe.
- DoD: unit tests for ordering determinism; PASS/HARD/FAIL/SKIP unchanged.

#### ✅ PHASE 80 — Knowledge area auto-linking (v0.3.78, 2026-10-18)
- Goal: when a problem's tags contain a known knowledge-area name, auto-link it (user can
  unlink). Links feed Phase 75's weak-area ranking.
- Scope: `core/model` knowledge tree, problem detail screen, repository join.
- Constraints: deterministic matching only (exact/alias table in code); no inference, no ML.
- DoD: unit test on alias matching; unlink persists; no network.

#### ✅ PHASE 81 — Mastery decay v2 (v0.3.79, 2026-10-18)
- Goal: mastery score decays with time-since-last-AC per knowledge area; shown as
  `MASTERY 72 ▼ DECAY 8` with the exact formula in `docs/TRAINING_ENGINE.md`.
- Scope: `core/model` mastery computation, analytics focus lens.
- Constraints: formula documented and unit-tested; deterministic; no fake metrics.
- DoD: doc updated with formula; tests pin boundary values (0 days, 30 days, cap).

#### ✅ PHASE 82 — Session debrief export (Markdown) (v0.3.80, 2026-10-18)
- Goal: one-tap copy of a terminal session's debrief as Markdown to clipboard/share sheet.
- Scope: session debrief screen; share via Android `ACTION_SEND`.
- Constraints: generated text is English telemetry style; no new permissions.
- DoD: unit test on Markdown builder for empty/partial/full sessions.

#### ✅ PHASE 83 — Focus Sprint difficulty ladder (v0.3.81, 2026-10-18)
- Goal: sprint builder orders candidates by ascending difficulty fit and shows the reason code
  per row (`WEAK AREA`, `REVIEW GAP 21D`, …).
- Scope: Focus Sprint preview + ranking function from Phase 75.
- Constraints: deterministic reason codes only; never infer target from missing rating.
- DoD: tests for ordering and reason-code assignment.

#### ✅ PHASE 84 — Training calendar heat streaks (v0.3.82, 2026-10-18)
- Goal: analytics heatmap cells get accessible labels and the streak logic (current/longest)
  moves into a tested pure function.
- Scope: `feature/analytics`, `core/model` streaks.
- DoD: unit tests across timezone-free local dates; a11y descriptions present.

#### ✅ PHASE 85 — Command palette: review/training queries (v0.3.83, 2026-10-18)
- Goal: parser recognizes `review due`, `sprint 1800`, `@cf unsolved 1600-1900`.
- Scope: command palette parser + direct-jump handlers.
- Constraints: text parser only, no LLM; reuse Phase 67 mechanism.
- DoD: parser unit tests for all new forms + no regressions on old ones.

### ARC B — Data integrity & portability

#### ✅ PHASE 86 — Export 2.0: full local snapshot (v0.3.84, 2026-10-18)
- Goal: single-file export (SQLite db + metadata JSON, zipped) with a manifest of table counts
  and the data-generation stamp from Phase 73.
- Scope: settings data screen; reuse Phase 73 validation on import.
- Constraints: NO credential or OpenApp secret fields in the export — ever. Verify by test
  that the zip contains no keystore material.
- DoD: round-trip test: export → fresh install → import → counts match.

#### ✅ PHASE 87 — Scheduled local backup (v0.3.85, 2026-10-18)
- Goal: opt-in WorkManager daily/weekly backup to a user-picked folder (SAF), with retention
  of the last N (default 5).
- Scope: settings + WorkManager; reuses Phase 86 exporter.
- Constraints: no cloud upload; failures surface as a dismissible settings banner.
- DoD: worker unit-testable logic; respects battery constraints.

#### ⬜ PHASE 88 — Per-judge data wipe
- Goal: delete one judge's local payload (problems, submissions, sync ledger rows) while keeping
  other judges and the account handle itself, behind a typed-confirm dialog.
- Scope: connector center + repository cascade queries.
- Constraints: transactional; writes a `WIPE` entry into the sync operations ledger.
- DoD: tests verify cross-judge isolation of the wipe.

#### ⬜ PHASE 89 — Import conflict report
- Goal: after import validation, show per-table incoming vs current counts BEFORE replacing.
- Scope: Phase 73 restore flow UI.
- DoD: cancel leaves current db untouched (test).

#### ⬜ PHASE 90 — Database size & housekeeping panel
- Goal: settings shows db size, row counts per table, and a VACUUM action.
- Constraints: VACUUM runs off main thread with progress + cancel.
- DoD: no schema change; sizes reported honestly from `PRAGMA`.

### ARC C — Multi-OJ depth

#### ⬜ PHASE 91 — Codeforces problemset incremental sync
- Goal: delta sync of CF problemset via `problemset.recentStatus` + etag-style caching of
  `problemset.problems`, bounded pages, offline-merge into Room.
- Scope: `judge/codeforces` adapter + sync worker.
- Constraints: public API only; backoff on 503; no scraping.
- DoD: unit tests with recorded fixtures; sync ledger entries per stage.

#### ⬜ PHASE 92 — Codeforces submission detail deep-link
- Goal: submission rows open CF contest/submission URL via Custom Tab with exact URL builder.
- Constraints: open-in-browser only; no in-app WebView login.
- DoD: URL builder unit tests (contestId/index/protocol edge cases).

#### ⬜ PHASE 93 — AtCoder contest results & rating sync
- Goal: pull AtCoder rating history + per-contest results via public endpoints/kenkoooo API,
  cached locally.
- Scope: `judge/atcoder`.
- DoD: fixtures tests; offline rendering from cache.

#### ⬜ PHASE 94 — Unified submission detail sheet
- Goal: one bottom-sheet design for any judge's submission: verdict, time/memory, problem link,
  note shortcut.
- Scope: `core/ui` + submission center.
- DoD: same component renders CF/AC/Luogu rows; strings via resources.

#### ⬜ PHASE 95 — Luogu team/training public data (if API permits)
- Goal: read-only public training lists for the bound uid.
- Constraints: confirm endpoint availability first; if not public, CUT this phase and record why.
- DoD: capability probe documented in spec before any UI.

#### ⬜ PHASE 96 — Judge health dashboard
- Goal: connector center shows last HTTP status, latency of last sync, and quota (Luogu
  `quotaAvailable`) per judge.
- Constraints: read-only projection of existing ledger + one explicit user-triggered probe.
- DoD: no background probing; manual refresh only.

#### ⬜ PHASE 97 — Multi-handle per judge (design-gated)
- Goal: spec + prototype for a second public handle per judge with isolated datasets.
- Constraints: REQUIRES the spec to solve data-generation stamping per handle (Phase 73/88
  interactions). UI implementation split into a later phase if risky.
- DoD: spec accepted; migration test plan written.

### ARC D — Windows / Apple parity

#### ⬜ PHASE 98 — Windows problem library view
- Goal: WPF `PROBLEMS` view over the shared SQLite ledger: filter by judge/status, open external
  link.
- Refs: `docs/superpowers/specs/2026-09-07-windows-client-cli-design.md`.
- DoD: `dotnet test` green; CLI gains `problems list --judge`.

#### ⬜ PHASE 99 — Windows review queue view
- Goal: due-review list + PASS/HARD/FAIL recording on Windows writing to the shared ledger.
- DoD: CLI `review due` / `review mark` parity.

#### ⬜ PHASE 100 — Windows installer (Inno Setup or MSIX)
- Goal: signed-or-unsigned installer producing start-menu entry + uninstaller; packaging script
  hardened like `package.ps1` (use .NET file APIs, not `Remove-Item`).
- Constraints: `signtool` step optional behind cert presence flag.
- DoD: CI job builds installer; verify script installs/uninstalls on clean VM or sandbox.

#### ⬜ PHASE 101 — Windows code signing
- Goal: sign CLI + WPF + installer when a CA cert is available; document the exact
  `signtool` invocation and cert storage rules (never committed).
- DoD: unsigned build still produced when cert absent; signing verified with `signtool /v`.

#### ⬜ PHASE 102 — Apple macOS slice advance (macOS host required)
- Goal: `swift test --package-path apple` green; macOS app shows dashboard + connectors.
- Constraints: blocked on this Windows host — executor must be on macOS. Never fake results.

#### ⬜ PHASE 103 — Apple iOS slice advance (macOS host required)
- Goal: iOS app reads public CF profile + rating chart.
- Constraints: same as 102.

### ARC E — Performance & scale

#### ⬜ PHASE 104 — Library paging
- Goal: problem library switches to Paging 3 (or manual windowing) at 10k rows.
- DoD: benchmark test with generated 10k-row fixture; scroll stays 60fps on mid device.

#### ⬜ PHASE 105 — Cold start budget
- Goal: cold start to interactive Dashboard ≤ 1.5s on a mid-range device; add a startup
  tracing test harness (Macrobenchmark optional).
- DoD: numbers recorded in `docs/PERFORMANCE.md`.

#### ⬜ PHASE 106 — Room index audit
- Goal: `EXPLAIN QUERY PLAN` audit of the top 10 queries; add indices where proven.
- Constraints: migrations must be non-destructive and tested 12→13.
- DoD: before/after timings in the phase spec.

#### ⬜ PHASE 107 — Sync worker battery profile
- Goal: measure and cap periodic sync cost; document interval defaults.
- DoD: `docs/SYNC_ENGINE.md` updated with measurements.

#### ⬜ PHASE 108 — Memory audit
- Goal: leak check (LeakCanary in debug only) + bitmap/compose recomposition pass on
  dashboard/library.
- DoD: zero leaks on main navigation loop.

### ARC F — Platform polish & release

#### ⬜ PHASE 109 — Android release signing pipeline
- Goal: release build signed via CI secrets; SHA256SUMS per release automated.
- DoD: `docs/releases/` gains sums for the next release automatically.

#### ⬜ PHASE 110 — Store/F-Droid metadata
- Goal: fastlane-style metadata folder (descriptions EN/zh, screenshots) in repo.
- Constraints: no marketing copy; telemetry tone.

#### ⬜ PHASE 111 — Reproducible build check
- Goal: document and script a build-reproducibility check for the APK.
- DoD: two clean builds produce identical hashes or documented diff sources.

#### ⬜ PHASE 112 — Accessibility sweep
- Goal: full a11y audit (talkback order, contrast, font scale 200% at 320dp) with a checklist
  per screen, fixing all critical findings.
- Refs: `docs/verification/2026-10-01-android-shared-layout.md`.

#### ⬜ PHASE 113 — Localization pass
- Goal: audit every user-visible string is in `strings.xml` (EN+zh), no hardcoded UI text.
- DoD: lint-style check script committed under `tools/`.

#### ⬜ PHASE 114 — Crash & ANR hygiene
- Goal: optional opt-in local crash log (no upload) viewable in settings; fix top findings.
- Constraints: nothing leaves the device.

#### ⬜ PHASE 115 — v1.0.0 release candidate
- Goal: versionName 1.0.0, full regression checklist, all docs refreshed.
- DoD: ROADMAP top entry + release notes + tag `v1.0.0`.

### ARC G — Optional connected layer (design-gated; cut allowed)

#### ⬜ PHASE 116 — Sync architecture RFC
- Goal: RFC only: end-to-end-encrypted, server-agnostic (user-provided endpoint) sync of
  non-credential data. Threat model + merge rules.
- Constraints: credentials/OpenApp secrets NEVER sync. RFC must say so in its first section.

#### ⬜ PHASE 117 — Sync POC (behind flag)
- Constraints: compiled out of release builds unless explicitly enabled.

#### ⬜ PHASE 118–120 — Reserved
- Reserved for what Phases 116–117 prove or kill. Fill in after the RFC lands. If the RFC is
  rejected, repurpose 118–120 for ARC C/D leftovers.

---

## 3. STANDING DECISIONS (do not relitigate without a spec)

- No WebView login, no cookies, no passwords — permanent (AGENTS.md Security).
- Deterministic algorithms only for recommendations/mastery — show reason codes.
- One accent (NEXUS BLUE), dark first, hairlines over cards, restrained radii.
- New judge = new `judge/<name>/` adapter; domain models stay judge-agnostic.
- Every phase updates `docs/ROADMAP.md` top; every phase bumps versionCode.
- Windows packaging scripts must use .NET file APIs for deletion (host `Remove-Item` is guarded).
- CLI exit codes follow the shared severity table in `docs/ROADMAP.md` (0–7 contract).

## 4. OPEN QUESTIONS

- Whether Phase 95's Luogu public endpoints exist — probe first (Phase 95 DoD).
- Whether multi-handle (97) is worth its migration complexity — decide at its spec gate.
- Whether Arc G ships at all — the RFC (116) decides; cutting it is a valid outcome.
