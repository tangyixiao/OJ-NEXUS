# Phase 127C — Typed import storage errors

Status: implementation design under the authorized Phase 121–220 roadmap. One delivery
boundary within the still-ACTIVE Phase 127; it does not close old-engine export
compatibility or the full phase release gate.

## Baseline and purpose

Branch `codex/phase-127-recovery-safety` at the Phase127B delivery tip (`4c6ea33`), PR #24
open against the 127A branch. RV004 (ENOSPC_CREATE_STAGING_TEMP), RV005
(ENOSPC_WRITE_STAGING) and RV006 (ENOSPC_WRITE_JOURNAL) all require phase ABORTED, OLD data
preserved, typed `STORAGE_FULL`, no preserved artifacts, and retry after space is freed.
Current Android behavior contradicts all three: `DatabaseRestoreCoordinator.prepareImport`
funnels every non-validation failure into `Rejected(FILE_REPLACEMENT_FAILURE)`, the copy and
staging-temp creation are not on the injectable file boundary, and a failed journal write
leaves the staged pending file behind so the next start BLOCKS on orphan evidence instead of
allowing a retry. The typed outcome is then discarded: `BackupRepository.importFrom` returns
only a Boolean and the settings UI shows the export-failure copy for import failures.

This slice makes import storage failures typed, retryable and honestly reported.

## Contracts and invariants

- RV001–RV003 (INVALID_INPUT / UNSUPPORTED_SCHEMA / INTEGRITY_FAILURE) keep their existing
  precheck behavior and categories; this slice does not reinterpret validation.
- `RestoreFailure` gains `STORAGE_FULL` and `STORAGE_IO`, appended after the existing values.
  Journal `detail` round-trips by name; old journals never carry the new values.
- Classification is a pure decision: `classifyImportIoFailure(Throwable)` walks the cause
  chain; any IOException whose message carries an ENOSPC marker (no space left on device,
  ENOSPC, not enough space, disk full) maps to `STORAGE_FULL`; any other IOException maps to
  `STORAGE_IO`; non-IO throwables return null and keep the caller's existing fallback.
- `prepareImport` staging is fully injectable: staging-temp creation and the input copy join
  the `RestoreFileOperations` boundary with system defaults, so existing decorators compile
  unchanged.
- On any `prepareImport` failure the run cleans up only what it owns: the staging temp and
  journal temp always; the staged pending file only when no journal existed before the run.
  A pre-existing journal and its pending file are never destroyed by a failed later attempt.
  Cleanup is best effort; a cleanup failure still returns the typed outcome (the next start
  keeps the existing blocked-on-evidence protection).
- No schema change, no app version bump, no new dependency, no release claim.

## User-facing contract

`BackupRepository.importFrom` returns the `RestoreOutcome` instead of a Boolean.
`SettingsViewModel.BackupResult` carries the typed `RestoreFailure?` (null on success). The
settings screen maps import failures to stable uppercase copy with no raw errors or paths:

| Result | Resource | Copy (zh catalog mirrors meaning) |
| --- | --- | --- |
| import staged | existing `settings_backup_import_success` | unchanged |
| import failed, STORAGE_FULL | new `settings_import_storage_full` | IMPORT FAILED — FREE STORAGE AND RETRY |
| import failed, STORAGE_IO | new `settings_import_storage_io` | IMPORT FAILED — STORAGE ERROR |
| import failed, other | new `settings_import_rejected` | IMPORT REJECTED — TAP TO DISMISS |
| export failed | existing `settings_backup_failed` | unchanged (stops being reused for import) |

New keys are added to both `values/` and `values-zh-rCN/` with real Chinese translations
(these are settings strings, not the 127A recovery-screen copy ruling).

## Acceptance

1. Pure classifier tests: ENOSPC markers (message and cause chain), other IOException,
   non-IO null.
2. Fault-injected coordinator tests against real files: staging-temp creation failure and
   staged-copy failure return the typed category, leave no journal/pending evidence, keep
   the active store untouched, and a retry after the fault clears converges to Staged;
   journal-write failure additionally cleans the staged pending file so the next attempt is
   not blocked; a pre-existing journal survives a failed attempt; invalid input still
   rejects with INVALID_SQLITE.
3. ViewModel test: a rejected import surfaces the typed `RestoreFailure` in `BackupResult`.
4. Fresh full local gates (`testDebugUnitTest --rerun test assembleDebug assembleRelease`),
   connected suite on the dedicated Phase127 test AVD when available, scoped commits, an
   evidence record, and honest limits (no physical device, no real ENOSPC hardware fault —
   injected I/O exceptions at the boundary represent the OS-level failures).
