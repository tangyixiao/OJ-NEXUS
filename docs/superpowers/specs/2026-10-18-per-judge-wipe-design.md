# SPEC — Phase 88: Per-Judge Data Wipe (2026-10-18)

## Goal

Give the user a destructive-but-scoped maintenance action: delete **one judge's entire local
payload** (training library problems, imported attempts, notes, knowledge, reviews, cached
remote catalog, drafts, submission jobs, sync ledger rows) while keeping every other judge's
data and the account binding itself intact.

Spec source: `docs/LONG_RANGE_PLAN.md` — PHASE 88 "Per-judge data wipe".

## Constraints

- Transactional: all deletions plus the audit ledger write happen inside one
  `database.withTransaction` block.
- Writes a `WIPE` entry into the sync operations ledger for audit. `WIPE` is a stored status
  string, not a new `SyncOperationStatus` enum value — the history UI renders the status text
  directly and only offers retry for PARTIAL/ERROR/CANCELLED, so unknown statuses degrade
  gracefully.
- The account row (`judge_accounts`) survives — the public handle stays connected; the wipe is
  data-only. `sync_states` cursor is deleted (it belongs to the wiped payload).
- Typed-confirm dialog: the confirm button stays disabled until the user types the judge's
  display name (case-insensitive).
- No network request. No Room schema migration (deletes only; no new columns/tables). No
  credential flow — wipe never touches the Luogu Open Platform keystore credential, which lives
  outside the database by design.

## Design

- `ProblemDao.deleteByJudge(judge)` — Room FK CASCADE removes attempts, failure entries,
  notes, knowledge, tags cross-refs, reviews, review log, training tasks/sessions rows that
  hang off the wiped problems.
- New `deleteByJudge` queries on `RemoteProblemDetailDao`, `SubmissionJobDao`,
  `WorkspaceDraftDao`, `ContestProblemMarkerDao`, `SyncOperationDao` (the other judge-scoped
  DAOs already had them).
- `JudgeAccountRepository.wipeJudgeData(judge, accountId)`: verifies the account is the judge's
  account, deletes judge-scoped rows across all 11 tables, then opens the WIPE ledger row with
  `dataGeneration` from an injected provider (`AppContainer.currentDataGeneration`), keeping
  existing secondary constructors source-compatible via a default empty provider.
- `SettingsViewModel.wipeJudgeData(account)`: per-judge in-flight + failure flags; failure is
  surfaced inline, never crashes.
- `SettingsScreen`: WIPE DATA action in `JudgeConnectionPanel` (connected accounts only) +
  typed-confirm `AlertDialog`.
- Cross-judge isolation is the DoD: seed two judges, wipe one, assert the other is untouched.
