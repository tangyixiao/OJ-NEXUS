# Phase 138 — Windows Native Problem Search and Virtualization — Design

Status: ACTIVE. Synthesized by the controller from the phase-138 recon
(read-only report over the Windows problem surfaces, 2026-11-21, file:line
anchors on file) and the roadmap entry.

Baseline main: `9bfdf21` (phases 131-137 delivered; PR #89 merge).

## Inputs this design synthesizes

- **Roadmap** (`docs/LONG_RANGE_PLAN_121_220.md`, PHASE 138): dependencies
  133 (delivered) + 136 (delivered); W's ProblemModels/store, CLI problems,
  WPF PROBLEMS. Deliverable: search, tag/status filtering, stable pagination
  and a detail entry on the existing library, using WPF native collection
  virtualization. Acceptance G-W: filters match the 136 vectors; large record
  sets do not create all visual elements; filters survive restart and back
  navigation; empty results have a clear-filters entry.
- **Recon findings (all verified, anchors at main `9bfdf21`)**:
  - Domain gap: `ProblemStatus { Attempted, Solved }` only two values
    (ProblemModels.cs:6-10); `ProblemEntry` carries
    (Judge, ExternalId, Title, Url, Solved, FirstSeenAt, LastSeenAt) — no
    difficulty, favorite, tags, attemptCount or updatedAt (ProblemModels.cs:39-46).
    The contract's 4-status derived model, difficulty null-semantics and tags
    are not represented anywhere on Windows.
  - Storage gap: schema v8; `problems` table exists (SchemaMigrator.cs:518-538)
    with PK (judge, external_id) but **no indexes**, no tags/favorite/difficulty/
    attempt_count/updated_at/title_lower columns; the read path
    `GetProblemsAsync` filters on the stored `solved` flag (not derived status)
    and orders `last_seen_at DESC, judge, external_id LIMIT` (SqliteSyncStore.cs:737-789)
    — not the contract tie-break, no pagination. The reviews table
    (SchemaMigrator.cs:556-570, PK (judge, external_id)) can supply the derived
    REVIEW status via EXISTS, mirroring Android.
  - CLI: a `problems list` verb already exists (CliParser.cs:198-233,
    `ProblemsListCommand(JudgeId?, ProblemStatus?, Limit, Json)` at :15) with
    `--judge/--status/--limit/--json`; execution dispatch at Program.cs:111-116;
    renderer overload pairs at ConsoleRenderer.cs:23-39; exit codes 0-7 frozen
    (CliExitCode.cs:14-39).
  - Desktop: a PROBLEMS page exists (DesktopPage.Problems, DesktopViewModel.cs:15-22)
    but navigation is a flat enum swap with **no detail state and no back**;
    filters are plain VM fields, never persisted (`_selectedProblemJudge/Status`
    DesktopViewModel.cs:170-171); the single load path caps at 100 rows with no
    search/tag/sort (DesktopViewModel.cs:365-413); rows render through a plain
    `ItemsControl` inside the shared ScrollViewer/StackPanel (MainWindow.xaml:191-212)
    — **no virtualization anywhere** (grep `virtualiz` over windows/src is empty);
    the empty state is `NO PROBLEMS RECORDED` (MainWindow.xaml:213) with no
    clear-filters entry.
  - Ingest reality: `CodeforcesSubmission` (SyncPayloadModels.cs:24-32) carries
    ContestId/ProblemIndex/ProblemName/Verdict — enough to derive per-problem
    attempt counts and solved; **no per-problem difficulty or tags exist in any
    Windows payload** (only user rating, SyncPayloadModels.cs:67).
  - ui-smoke.ps1 asserts **exactly 2 problem-filter ComboBoxes** (L487-491) and
    the empty state text (L492-495); windows.yml runs `dotnet test` Release as
    the only test gate and skips UI smoke in CI (windows.yml:32-36, 41-45).
  - Version bump blast radius: two constants (SchemaMigrator.cs:9,
    WindowsStorage.cs:82) + ~10 hardcoded `"8"` asserts across
    SqliteSyncStoreTests/CodeforcesPayloadPersistenceTests/PortableArchiveStoreTests/
    WindowsStorageTests/StorageMigrationTests + the legacy-schema drop ladder
    (StorageMigrationTests.cs:107-123) and the version-0-6 InlineData ladder
    (:9-16).
  - Test house patterns: Core tests use `TemporaryDatabase`/`StorageTestScope`
    (real SQLite); VM tests use `StorageOperationScope(InMemorySyncStore)` with
    no WPF/DB (DesktopViewModelTests.cs:14-25); parser tests are pure
    `Assert.Throws<CliParseException>`; `InMemorySyncStore` already keys problems
    by `"$judge/$externalId"` with Ordinal (InMemorySyncStore.cs:17).
- **Contract** (`contracts/problems/v1/`, 28 vectors, frozen): identity
  `(judge, externalId)` exact byte equality; derived status
  `inReview → REVIEW else solved → SOLVED else attemptCount > 0 → ATTEMPTED
  else UNSOLVED`; search = ASCII-trim (U+0020, U+0009-U+000D) + simple
  lowercase + substring over lowered title OR lowered externalId (NBSP is a
  real term, vector 026); AND-combined filters (status/judge/favoriteOnly/
  tag-exact-case-sensitive/difficulty inclusive bounds with unknown excluded
  when any bound set); sorts UPDATED(updatedAt DESC)/DIFFICULTY(nulls last,
  0 valid)/TITLE(lowered, code-point)/ATTEMPTS(desc); every sort ends with the
  code-point tie-break on `"judge/externalId"`; summary counts never derive
  from the visible page.

## Decisions

1. **Full contract-shape domain upgrade (schema v9).** The two-status model
   cannot satisfy vectors 018-021 (REVIEW/UNSOLVED) or 003/005 (difficulty)
   or 014 (tags); a narrow "add search to the existing model" would fail the
   acceptance by construction. v9 adds to `problems`:
   `difficulty INTEGER NULL`, `favorite INTEGER NOT NULL DEFAULT 0`,
   `attempt_count INTEGER NOT NULL DEFAULT 0`, `updated_at TEXT NULL`
   (backfilled to `last_seen_at`), `title_lower TEXT NULL` (backfilled C#-side
   with `ToLowerInvariant`, which matches the frozen simple-lowercase on every
   pinned vector — the empirical İ divergence is recorded as a deviation
   below), plus indexes
   `idx_problems_updated_at`, `idx_problems_title_lower`, `idx_problems_difficulty`,
   and a new `problem_tags (judge, external_id, name, PRIMARY KEY(judge,
   external_id, name))` table. `in_review` stays derived via
   `EXISTS(SELECT 1 FROM reviews rv WHERE rv.judge = problems.judge AND
   rv.external_id = problems.external_id)` — same predicate shape as Android
   (LibraryQuery.kt DERIVED_STATUS), no column.
   **Recorded deviation (independent review F2, verified empirically on
   net8.0):** `ToLowerInvariant(U+0130 İ)` leaves the character unchanged
   while the oracle's `str.lower()` maps it to `i` + U+0307, so titles
   containing İ diverge in TITLE sort keys and "i" search matching, and
   `lower(external_id)` (SQLite ASCII-only `lower()`) diverges for non-ASCII
   external ids. None of the 28 pinned vectors exercise İ or non-ASCII
   external-id search; closing that gap needs a new vector plus a
   contract-level decision (full-mapping lowercase table or pinned-culture
   lowering) and is deferred rather than silently claimed.
2. **The filter becomes SQL mirroring Android's `LibraryQueryBuilder`.** Same
   derived-status CASE; search `title_lower LIKE ? ESCAPE '\' OR lower(external_id)
   LIKE ? ESCAPE '\'` with `\`/`%`/`_` escaped and bind args trimmed with the
   frozen ASCII set then lowered with `ToLowerInvariant` on the C# side; tag
   via EXISTS on problem_tags; difficulty `IS NOT NULL` + inclusive bounds when
   either bound set; ORDER BY sort key + stable tie-break
   `judge ASC, external_id ASC` (SQLite BINARY on UTF-8 = code-point order).
   `ProblemStatus` grows to the 4 contract values with parser wire names
   `UNSOLVED/ATTEMPTED/SOLVED/REVIEW`.
3. **OFFSET pagination over the total deterministic order.** The Windows store
   is an explicit-query API (no invalidation stream to anchor), and the
   contract tie-break makes the order total, so `LIMIT/OFFSET` windows are
   stable by construction — no page shuffles without a write. `QueryProblemsAsync`
   returns rows + filtered `COUNT(*)` + summary aggregates (filtered match
   count and unfiltered totals from SQL COUNT/SUM, never from the visible
   page — same visible-vs-total rule as phase 137).
4. **CLI: extend `problems list`, add `problems show`.** `list` gains
   `--search/--tag/--favorite/--difficulty-min/--difficulty-max/--sort/--offset`
   (sort values `UPDATED|DIFFICULTY|TITLE|ATTEMPTS`, default UPDATED; LIMIT
   default stays 50); `show judge/externalId` prints the full record or
   `PROBLEM NOT FOUND` → exit 7 stays GeneralError per the frozen table (a
   missing record is not a storage failure). JSON shape:
   `{query, totalMatches, offset, rows[]}` and the detail object for `show`.
   Human output keeps ALL-CAPS telemetry tone. Exit codes unchanged (0/2/6/7).
5. **WPF: virtualized list, detail sub-state, persisted filters.**
   - The Problems panel's list region becomes a fixed-height `ListBox` with
     `VirtualizingStackPanel` (Recycling) owning its own scrolling, replacing
     the `ItemsControl`-in-ScrollViewer (the shared page ScrollViewer cannot
     virtualize an unbounded-height StackPanel child).
   - Controls: search `TextBox` (contract ASCII trim in the VM), sort
     `ComboBox`, tag `TextBox`, favorite `CheckBox`, min/max difficulty
     `TextBox` pair, next-page `LOAD MORE` button driven by `totalMatches`
     and offset. `ClearFilters` resets all and reloads.
   - Detail: row activation opens a detail sub-state on the Problems page
     (full record + OPEN URL + BACK); BACK returns to the list with filters
     and current page intact (VM state, not a reset). `Detail` is a VM
     property, not a new DesktopPage — the flat-nav model stays.
   - Persistence: v9 adds `app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)`
     and `ISyncStore.GetSettingAsync/SetSettingAsync`; the VM loads filters on
     construction and saves on every committed change (JSON blob under one
     key, `problems.filters.v1`), giving restart persistence without new VM
     plumbing. `InMemorySyncStore` implements the same surface (ConcurrentDictionary).
   - Virtualization gate: an STA WPF unit test in Desktop.Tests binds 10,000
     rows to the real ListBox style inside a fixed viewport and asserts the
     realized container count stays bounded (≪10k); plus ui-smoke.ps1 asserts
     the new controls and, via UIA, that the problems list exposes far fewer
     realized children than seeded rows (bounded visual evidence outside CI).
6. **Derivation enrichment is honest and minimal.** `DeriveProblemsFromCodeforcesAsync`
   / `DeriveProblemsFromAtCoderAsync` additionally maintain `attempt_count`
   (submission count per (judge, externalId) recomputed from the payload on
   each persist) and keep the existing sticky solved/title semantics.
   **Recorded deviation:** difficulty and tags have no Windows ingest source
   (payloads carry neither); the columns and query layer are contract-complete
   and vector-tested via test seeding; real synced rows carry null difficulty
   and no tags until a later phase adds ingest. `favorite` is user-owned
   (default false; no toggle ships in this phase — the filter is exercised by
   the store API and vectors).
7. **Parity gate = the real contract corpus.** Core.Tests links
   `contracts/problems/v1/golden` via csproj `Content` (the Android phase-137
   pattern) and runs all 28 vectors through the real SQLite query path
   (seed rows from each vector's dataset + query, compare key order to
   `expect`). A divergence is a platform bug or a contract bug — never bent.
8. **Version-bump sweep is mechanical and exhaustive.** Both version constants
   move to 9 in lockstep; every hardcoded `"8"` assert flips; the
   `StorageMigrationTests` InlineData ladder gains `(7, false|true)` and the
   `BuildLegacySchema` drop ladder gains the v9 artifacts arms; `InspectExisting`
   gains `case 9`; the fresh path appends the new apply/validate calls.

## Non-goals

- No ingest of difficulty/tags (no payload source — recorded deviation above).
- No favorite-toggle UI (filter + column only).
- No changes to portable archive schema/semantics (phase 133 surface).
- No Android/Linux/Apple code. No new CI workflow; windows.yml unchanged
  except nothing (tests run through the existing `dotnet test` gate).

## Risks / gotchas carried forward

- `ui-smoke.ps1` hard-asserts 2 problem ComboBoxes and the empty-state text —
  both move with this phase (script updated in T4, verified locally; CI skips
  smoke, so the local run is the only gate for it).
- The shared ScrollViewer restructure must keep the other four pages intact —
  the Problems panel gets its own viewport; dashboard/connectors/history/portable
  markup is untouched.
- Xunit has no `[STAThread]`; the virtualization test drives WPF on a dedicated
  STA thread with `Join()` (established .NET pattern; no house precedent — the
  first such test, reviewed at T6).
- `ToLowerInvariant` vs Python `str.lower()`: both apply unconditional Unicode
  casing (ß unchanged, İ → i + combining dot); the 28-vector parity test is
  the arbiter — any divergence stops for a contract-versions discussion.
- cmd.exe shell in this environment mangles quoting/encoding for non-ASCII
  edits; file writes go through editor tools, never shell heredocs.

## Acceptance → gates

| Acceptance (roadmap) | Gate |
|---|---|
| 筛选与 136 向量一致 | 28-vector parity test over real SQLite (T2), all green |
| 大量记录不创建全部可视元素 | STA virtualization unit test + ui-smoke UIA realized-children check (T4) |
| 重启和返回保留有效筛选 | VM persistence tests (save-on-change/load-on-create) + detail-back filter retention test (T4) |
| 空结果有清除条件入口 | VM ClearFilters tests + empty-state button asserted in ui-smoke (T4) |
| G-W | `dotnet test` Release green + build + package locally; windows.yml green on the PR |
