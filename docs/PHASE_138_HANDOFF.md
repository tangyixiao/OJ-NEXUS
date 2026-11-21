# PHASE 138 HANDOFF — Windows Native Problem Search and Virtualization

Status: implementation delivered on `codex/phase-138-windows-problem-search`
(commits `51bcdaa` SDD, `c0618d9` T1 Core+v9, `89dea15` T2 parity gate,
`0e23d21` T3 CLI, `e2f5c33` T4 WPF, `a116bef` ledger flips); local gates
measured (see
[verification record](verification/phase-138-windows-problem-search.md));
delivery CI + independent review pending at handoff time.

## 1. What shipped

- **Contract-shape ledger domain (schema v9).** `problems` gained
  difficulty/favorite/attempt_count/updated_at/title_lower; new
  `problem_tags` (composite FK to problems) and `app_settings` (kv for
  persisted UI filters) tables; ledger indexes on updated_at/title_lower/
  difficulty. `ProblemStatus` is now the four-value contract enum with
  UNSOLVED/ATTEMPTED/SOLVED/REVIEW wire names; `ProblemEntry` carries the
  full contract shape with a derived `Status` and the frozen lowercase
  identity key (`codeforces/1234A`).
- **SQL query layer mirroring Android `LibraryQueryBuilder`.** Derived-status
  CASE (reviews EXISTS → REVIEW → SOLVED → attempt_count > 0 → ATTEMPTED);
  escaped LIKE substring search over `title_lower` + `lower(external_id)`
  with the frozen ASCII trim + invariant lowercase; tag EXISTS; difficulty
  bounds excluding unknown difficulty; every sort ends with the code-point
  tie-break (`lower(judge) ASC, external_id ASC` — BINARY on UTF-8 is
  code-point order; `lower()` normalizes the stored enum casing).
  `QueryProblemsAsync` returns rows + filtered count + unfiltered aggregates;
  OFFSET pages are stable because the contract order is total.
- **CLI:** `problems list` gained `--search/--tag/--favorite/--difficulty-min/
  --difficulty-max/--sort/--offset`; new `problems show judge/externalId`
  (externalId verbatim — no trim/fold). JSON page shape echoes the query.
- **WPF PROBLEMS page:** virtualized ListBox (recycling + CanContentScroll +
  fixed 520px viewport), contract filter bar, LOAD MORE OFFSET paging, detail
  sub-state with BACK (filters/pages retained), filter persistence
  (`problems.filters.v1` JSON in app_settings; save-on-commit, load-on-first-
  page), SQL-fed summary, filtered-empty state with CLEAR FILTERS.
- **Parity gate:** all 28 phase-136 golden vectors reproduce through the real
  SQLite path (`ProblemContractVectorTests`, 28/28).

## 2. Measured evidence at handoff

- Build: 0 errors / 0 warnings (TWE) on `e2f5c33`.
- Tests: Core 307/307 · Cli 99/99 · Desktop 49/49 — 455/455.
- ui-smoke: PASS (5/5 screenshots; FILTERS=3; clear-filters round trip via
  UIA).

## 3. Gotchas for the next agent

- **pragma table-valued functions take quoted STRING arguments** —
  `pragma_table_info([problems])` parses but fails at execute; use
  `pragma_table_info('problems')`. Cost: a full-suite red cascade.
- **The migration ladder must grow BOTH the branch and the fresh path** when
  bumping the version — the version-8 branch was missed on the first pass
  because the old ladder ended at `CurrentSchemaVersion`.
- **`Microsoft.Data.Sqlite` converts enum parameters to their underlying
  integer** — always `.ToString()` domain enums before binding.
- **`Microsoft.Data.Sqlite` allows interleaved reader/commands on one
  connection** (SQLite native), but the demote-fresh test pattern requires
  migrations to be re-runnable: every future ADD COLUMN goes through
  `AddColumnIfMissing`.
- **The concurrent-first-run contract is "only READY or BUSY"** — a database
  file can exist with no committed metadata while another process initializes
  it; classify that as STORAGE_BUSY (`MissingSchemaMetadata`), never Io.
- **`UseWPF` on the Desktop test project would remove System.IO implicit
  usings** — WPF types already flow transitively; do not enable it there.
- **ui-smoke needs pwsh 7** (`Add-Type` C# 7 `out var`); run
  `pwsh scripts/ui-smoke.ps1`, not powershell 5.1.
- **cmd.exe + non-ASCII in this harness corrupts console edits** — file
  writes go through editor tools or UTF-8-safe scripts; GBK console output is
  display noise only.

## 4. Phase 139 (Linux Qt problem model) binds next

- Dependencies 134 (delivered) + 136 (delivered) are ready.
- The Linux side mirrors this phase's shape: Qt model + CLI + QML over the
  schema-v3 portable archive store's host database; the same 28 vectors are
  its acceptance vehicle; the Linux host has no toolchain — CI
  (`linux.yml`, Ubuntu-24.04, Qt 6) is the verification carrier, and the
  Qt-install step regularly hangs ~25 minutes (cancel + rerun for a fresh
  runner is the established remedy).
