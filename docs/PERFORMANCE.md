# Performance Audit

The current UI keeps potentially long collections bounded or lazy:

- The local and remote problem catalogs use `LazyColumn`.
- Training candidates use explicit Room bucket caps, a bounded 60-row merge policy, and a
  repository screen default of 20 rows.
- Contest and detail pages use bounded local snapshots; session and form pages only scroll their
  finite editor/state sections.
- ViewModels expose Room and preference flows with `WhileSubscribed(5_000)`, so screen-scoped
  work stops after the lifecycle subscription timeout.
- Navigation transition objects are remembered and rebuilt only when the reduce-motion preference
  changes.

The Room test suite includes a 40-row candidate query assertion to prevent an unbounded training
feed regression. This is a static and deterministic audit; device-specific frame timing still
requires an emulator or physical device. A Pixel_9 emulator has been used for startup and
navigation smoke checks; full frame-timing profiling remains a separate task. / 这是静态且确定性
的审计；设备帧耗时仍需通过模拟器或真机测量。本工作区已使用 Pixel_9 模拟器完成启动和导航
冒烟检查；完整帧耗时分析仍是单独任务。

## Training candidate pool — Phase 75

The JVM regression test uses a deterministic in-memory source set of 600 candidate rows. It
duplicates that source across the due, recent-unsolved, failure-heavy, and knowledge-linked
buckets, warms up the merge ten times, then measures 100 merges into the bounded 60-row pool.

- Baseline environment: Windows host `TANGYIXIAO`, Android Studio JBR configured by
  `tools/gradlew-local.bat`, local `testDebugUnitTest` JVM.
- Measured baseline: 9ms for 100 merges (2026-09-07).
- Regression threshold: under 500ms for the same workload, leaving CI and JVM warm-up headroom.
- Test: `TrainingCandidatePoolPerformanceTest`.

This benchmark isolates deterministic pool merge/dedup work; Room query correctness is covered by
`OjNexusDatabaseTest` and the repository pool tests.

## Sync operation history — Phase 76

The connector history query is bounded to the newest 10 completed operations per judge, while
active operations remain visible until they close. The UI projects at most five rows per connector,
and module outcomes are sorted deterministically before rendering. This keeps history work local,
bounded, and independent of remote response volume.

## Cold start budget — Phase 105

**Budget: 1500 ms from process start to the first interactive Dashboard frame.**

### Harness

`OjNexusApplication` opens a `StartupTracer` span at container construction (`app-create` covers
restore recovery, Room open, and repository wiring) and closes it before background bootstrap is
launched; `bootstrap-launch` closes after the async reconciliation coroutines are handed to the
application scope. The Dashboard composable closes the `first-frame` span on its first
composition and logs the whole chain once:

```
adb logcat -s OJNEXUS-STARTUP
OJNEXUS-STARTUP: cold-start total=...ms budget=1500ms verdict=WITHIN_BUDGET|OVER_BUDGET
app-create=...ms bootstrap-launch=...ms first-frame=...ms
```

The verdict comes from the pure `StartupBudget.verdict(total)` (unit-tested boundaries at
1500/1501 ms). No startup I/O and no tracing allocation before the first frame; background
bootstrap (periodic sync reconciliation, Luogu result work, backup schedule) launches after
`app-create` closes, so it never counts against the first frame.

### Measured numbers

| Measurement | Value | Source |
| --- | --- | --- |
| Span-chain accounting (injected clock) | deterministic | `StartupTracerTest` |
| Device cold start | **PENDING** — `adb shell am force-stop` + cold launch, read the OJNEXUS-STARTUP line, 5-run median on a mid-range device | no device attached to this host; per repo rules the 1.5 s DoD number is recorded only from hardware |

## Room index audit — Phase 106

Schema v13→v14 adds two composite indices, chosen by an `EXPLAIN QUERY PLAN` audit of the ten
hottest production queries (full table in
[docs/superpowers/specs/2026-10-18-room-index-audit.md](superpowers/specs/2026-10-18-room-index-audit.md)):

- `index_submission_jobs_judge_status_updated_at` on `submission_jobs (judge, status, updated_at)` —
  the background Luogu job scan previously SCANed the table plus a temp B-tree sort.
- `index_remote_problems_judge_external_id` on `remote_problems (judge, external_id)` —
  the delta-sync fingerprint read previously searched `judge` then sorted `external_id`.

`MIGRATION_13_14` is additive `CREATE INDEX IF NOT EXISTS` DDL only — non-destructive, tested by
`RoomIndexAuditTest` (fresh v14 + a hand-built v13 database upgraded in place, rows preserved,
plans assert `USING INDEX` text which is device-independent).

| Query | Before (plan) | After (plan) | Robolectric wall clock |
| --- | --- | --- | --- |
| Pending Luogu jobs (`judge='luogu' AND status='PENDING' ORDER BY updated_at`) | SCAN + temp B-tree sort | SEARCH via new composite index | ~9.0 ms → ~1.1 ms |
| Remote problems delta read (`judge=? ORDER BY external_id`) | SEARCH judge + sort | SEARCH via new composite index (sort eliminated) | ~7.5 ms → ~1.3 ms |

Robolectric in-memory SQLite proves the plan shape, not device storage latency; device numbers stay
**PENDING** until hardware runs (same policy as the cold-start budget).

## Library rendering — Phase 104

The problem library renders through manual windowing: the LazyColumn tracks at most a 60-row
window (grown 60 rows per SHOW MORE tap), so recomposition cost is flat in library size. The
10,000-row filter+sort+window pass is unit-tested to finish well under a 250 ms CPU budget
(`LibraryWindowTest`); scroll smoothness follows because Compose only tracks the window, not the
full list.
