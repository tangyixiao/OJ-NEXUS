# Phase 138 — Windows Native Problem Search and Virtualization — Plan

Design: [specs/2026-11-21-phase-138-windows-problem-search-design.md](../specs/2026-11-21-phase-138-windows-problem-search-design.md)
Baseline main: `9bfdf21`. Ledger: [phase-138-plan-ledger](../../verification/phase-138-plan-ledger.md).

Waves: W1 = T1 → W2 = T2 ∥ T3 ∥ T4 (exclusive files) → W3 = T5 controller
gates + review + closeout. Controller implements directly with CI gates
(established arc pattern; recon + independent review are the delegated
subagent work).

## T1 — Core: contract-shape domain + schema v9 + query layer

Exclusive files:
- `windows/src/OjNexus.Windows.Core/Domain/ProblemModels.cs`
- `windows/src/OjNexus.Windows.Core/Contracts/ISyncStore.cs`
- `windows/src/OjNexus.Windows.Core/Storage/SchemaMigrator.cs`
- `windows/src/OjNexus.Windows.Core/Storage/SqliteSyncStore.cs`
- `windows/src/OjNexus.Windows.Core/Storage/WindowsStorage.cs`
- `windows/src/OjNexus.Windows.Core/Sync/InMemorySyncStore.cs`

Deliverables:
- `ProblemStatus` → 4 contract values (`Unsolved, Attempted, Solved, Review`)
  with wire names `UNSOLVED|ATTEMPTED|SOLVED|REVIEW` (parser + ToUpper both
  updated; CLI status filter values follow).
- `ProblemLedgerQuery(search, status, judge, favoriteOnly, tag, difficultyMin,
  difficultyMax, sort)` + `ProblemSort { Updated, Difficulty, Title, Attempts }`
  + `ProblemLedgerRow` (full contract shape + derived status string) +
  `ProblemLedgerPage(Rows, TotalMatches, Offset, Limit, Summary)` +
  `ProblemLedgerSummary(Total, Solved, Review, Favorites)` (filtered total +
  unfiltered aggregates) + `ProblemDetail` record.
- `ISyncStore`: `QueryProblemsAsync(ProblemLedgerQuery, offset, limit)`,
  `GetProblemDetailAsync(judge, externalId)`, `GetSettingAsync(key)`,
  `SetSettingAsync(key, value)`. Old `GetProblemsAsync` retired; call sites
  (Program.cs CLI, DesktopViewModel) migrate in T3/T4 respectively — T1 keeps
  the method temporarily `[Obsolete]`-free but unused, removed at the end of
  T1 since T3/T4 land in the same PR series? No — T1 removes it and fixes all
  in-repo call sites itself (Program.cs included) so every intermediate commit
  compiles; T3/T4 extend, not migrate.
- SchemaMigrator: constants → 9 (both files); `ApplyVersionNineMigration`
  (ADD COLUMN ×5 with backfills `updated_at = last_seen_at`, C#-side
  `title_lower = ToLowerInvariant(title)`; CREATE problem_tags; CREATE
  app_settings; CREATE 3 indexes); validator for the v9 shape; `InspectExisting`
  case 9; fresh path chain.
- SqliteSyncStore: `QueryProblemsAsync` (mirror of Android LibraryQueryBuilder:
  derived-status CASE, escaped LIKE with ESCAPE '\', tag EXISTS, difficulty
  guard, ORDER BY + tie-break, LIMIT/OFFSET; COUNT query; summary query),
  `GetProblemDetailAsync`, settings get/set, derivation enrichment
  (attempt_count recompute per persist; favorite=0 default).
- InMemorySyncStore: same surface over in-memory structures (Ordinal keys,
  derived status computed the same way, settings ConcurrentDictionary).
- Acceptance: `dotnet build` TWE clean; all existing Core tests green after
  the version-assert sweep (moved into T1 since the constants move here).

## T2 — Core.Tests: migration sweep + 28-vector parity + store tests

Exclusive files: `windows/tests/OjNexus.Windows.Core.Tests/**` (plus csproj
Content link for `contracts/problems/v1/golden`).

Deliverables:
- Version asserts flip to 9; `StorageMigrationTests` ladder gains v7 rows and
  drop-ladder arms; fresh-DB asserts updated.
- **Parity test**: reads all 28 golden vectors (System.Text.Json), seeds a
  `TemporaryDatabase` through the real v9 store (rows with difficulty/favorite/
  attempt_count/inReview-via-reviews/tags/updated_at), runs
  `QueryProblemsAsync` with each vector's query, asserts the returned key
  order equals `expect`. 28/28 or the phase stops.
- Store unit tests: LIKE escaping (`%`/`_`/`\`), ASCII-trim vs NBSP bind args,
  settings round-trip, summary visible-vs-total (10k seeds, page of 60,
  summary = 10k-scale), detail of a missing id → null.
- Acceptance: `dotnet test` Core project green locally.

## T3 — Cli: parser/renderer/program extension

Exclusive files: `windows/src/OjNexus.Windows.Cli/**` + Cli.Tests.

Deliverables:
- Parser: `--search/--tag/--favorite/--difficulty-min/--difficulty-max/--sort/
  --offset` on `problems list` (record grows; usage string updated; duplicate/
  unknown option errors keep ALL-CAPS actionable style); `problems show
  <judge>/<externalId>` (key parse = split on first '/', exact externalId —
  no trim/fold per identity freeze).
- Program dispatch: list → QueryProblemsAsync (renders page), show →
  GetProblemDetailAsync (null → `PROBLEM NOT FOUND` line, exit 7).
- Renderer: JSON `{query, totalMatches, offset, limit, rows[]}` + detail
  object; human lines ALL-CAPS with PAGE x/n header.
- Cli.Tests: parser option matrix, show key parsing, renderer snapshots for
  human+JSON, ProgramTests integration for one list + one show.
- Acceptance: `dotnet test` Cli project green locally.

## T4 — Desktop: VM + XAML + virtualization + smoke

Exclusive files: `windows/src/OjNexus.Windows.Desktop/**` +
`windows/tests/OjNexus.Windows.Desktop.Tests/**` + `windows/scripts/ui-smoke.ps1`.

Deliverables:
- VM: filter fields (search/sort/tag/favorite/min/max) + `ProblemFilterState`
  JSON persisted via settings on every committed change and loaded on
  construction; `LoadNextPage` (offset += limit when more available); detail
  sub-state (`SelectedProblemDetail`, `IsProblemDetailVisible`, `CloseProblemDetail`
  keeps filters/pages); `ClearFilters` command; summary properties from the
  page payload; status strings from derived status (4 values, text always
  accompanies color/state per a11y rule).
- XAML: Problems panel gains its own fixed-height list viewport; ListBox +
  `VirtualizingStackPanel.VirtualizationMode="Recycling"` +
  `ScrollViewer.CanContentScroll="True"`; search/sort/tag/favorite/difficulty
  controls wired to code-behind handlers (house pattern); empty state shows
  `NO PROBLEMS MATCH THE CURRENT FILTERS` + `CLEAR FILTERS` button when
  filters are active, plain `NO PROBLEMS RECORDED` otherwise; detail panel
  with BACK/OPEN URL; LOAD MORE button.
- Code-behind: control handlers, row activation → detail, ScrollChanged
  bottom-detection optional (button-driven Load More is the primary path).
- Desktop.Tests: filter persistence round-trip (save → new VM → load),
  clear-filters resets and reloads, detail-back retention, paging accumulation
  across LOAD MORE, summary from store not rows, and the **STA virtualization
  test** (10k rows → bounded realized containers).
- ui-smoke.ps1: update the Problems assertions (control count changes, new
  empty-state text, CLEAR FILTERS click restores rows, detail open/back keeps
  the list); screenshot names unchanged (5 distinct captures still required).
- Acceptance: `dotnet test` Desktop project green; `ui-smoke.ps1` green
  locally; `dotnet build` TWE clean.

## T5 — Controller: gates, review, closeout

- Full local G-W: `dotnet test` (whole sln, Release) + `dotnet build` +
  `package.ps1 -SkipUiSmoke:false` + `ui-smoke.ps1` + installer build +
  verify-installer (local machine can run all of these).
- Delivery PR → CI (windows.yml `dotnet test` + build + package + installer).
- Independent review subagent over the full diff (codec-adjacent: parity,
  migration safety, virtualization honesty, persistence correctness).
- Findings disposition → closeout PR: verification record (VERIFIED with all
  measured gates), review record, plan ledger rows flipped, roadmap tick,
  PHASE_138_HANDOFF.
