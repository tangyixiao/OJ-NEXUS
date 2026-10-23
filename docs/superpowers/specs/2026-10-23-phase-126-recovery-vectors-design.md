# Phase 126 — Shared recovery fault vectors

Status: implementation design under the authorized 121–220 roadmap. Scope is the shared
contract and honest consumer evidence; platform recovery implementations remain 127–130.

## Intent and constraints

Users must retain a complete old or complete new logical dataset after an interrupted import,
write or migration. When neither can be established, preserve the remaining bytes/artifacts
and expose a blocked state; never silently replace a damaged existing store with an empty one.
The contract must state retry prerequisites and generation rules, not merely name failures.

Keep Kotlin/Room, Windows SQLite, Linux Qt/SQLite and Apple Foundation stores independent.
Fixtures are synthetic symbolic images, never copied user databases or fake judge responses.
No credentials, source code, custom input, absolute host paths or platform schema versions
belong in this corpus. This is not Portable Workspace v1 and is not a new backup format.

## Approach

Use one versioned JSON corpus plus a Markdown protocol. An optional Android projection drives
the existing production `decideRestoreRecovery` and `decideRestoreWorkGeneration` functions.
Tests validate the corpus and call those functions directly; do not implement a second recovery
engine or infer IO correctness from a fault label. Native consumer adoption is recorded separately.

A prose-only checklist would not prevent future consumers from silently changing expectations.
A shared executable storage engine would impose Android file semantics on other native clients.
The selected corpus approach gives common assertions without changing platform ownership.

## Protocol

Logical phases are PRECHECK, STAGING, STAGED, COMMITTING, COMMITTED, RECOVERING, ABORTED and
BLOCKED. They describe obligations, not required on-disk enum spellings. Precheck/staging
must not write active data. Staged import requires explicit confirmation before replacement.
Once commit begins, a late cancel must report too-late/reconcile rather than pretend rollback.

The commit boundary includes validated active data **and its durable generation**. File rename
alone is not logical completion. Restart before that boundary must validate/finish or roll back;
restart after it may only clear redundant artifacts. A failed rollback preserves every remaining
recovery source and stays BLOCKED; it must not be labelled a completed rollback.

Errors are safe categories: INVALID_INPUT, UNSUPPORTED_SCHEMA, INTEGRITY_FAILURE, STORAGE_FULL,
STORAGE_IO, CANCELLED, CANCEL_TOO_LATE, STALE_GENERATION, GENERATION_UNKNOWN,
RECOVERY_SOURCE_MISSING and RECOVERY_BLOCKED. Platform enums may map several categories to
one existing safe error; the mapping cannot be presented as a tested native ENOSPC path.

The atomic unit is explicit: whole-store restore, single module, or single workspace file.
Valid empty REPLACE results commit an empty value; valid empty UPSERT results retain existing
rows. Missing/invalid/failed responses never masquerade as valid empty results. A payload
commit followed by missing ledger completion requires reconciliation, not a fabricated rollback.

## Corpus v1

Canonical path: `contracts/recovery/v1/vectors.json`. Root keys are `format_version: 1`,
`synthetic: true`, and `vectors`. Every vector has `id`, `operation`, `fault`, `checkpoint`,
`atomic_unit`, `write_semantics`, `expected`, `retry` and nullable `android` projection.

- IDs are `RV001` through `RV032`, unique and stable.
- Operations: IMPORT, EXPORT, WORK, MIGRATE, OPEN, WRITE, SYNC.
- Write semantics: REPLACE, UPSERT, NONE. Atomic units: STORE, MODULE, WORKSPACE_FILE, WORK_ITEM.
- `expected` has `phase`, `active_data` (OLD, NEW, EMPTY, NONE_BLOCKED), `generation`
  (OLD, NEW, UNCHANGED), `error` (safe category or null), `preserve_artifacts` (boolean),
  `active_writes_allowed` (boolean) and `next_action` (explicit string defined per vector).
- `retry` has `allowed` and `after` (a concrete prerequisite, or NONE if retry is not allowed).
- All generation/image names are logical symbols. Corpus validation checks required fields,
  enums, exact version, IDs and relational invariants; it is not a recovery implementation.

| IDs | Scenario / required result |
| --- | --- |
| RV001–003 | Truncated input, schema mismatch, failed integrity: OLD/OLD, ABORTED, no active writes; retry only with corrected input |
| RV004–006 | ENOSPC at CREATE_STAGING_TEMP, WRITE_STAGING, WRITE_JOURNAL: OLD/OLD, safe failure; retry after storage is available and precheck repeated |
| RV007–008 | Cancel preview / explicitly staged work before commit: OLD/OLD; no active writes, cancellation artifacts removed where safe |
| RV009 | Cancel after logical commit: NEW/NEW, CANCEL_TOO_LATE, reconcile already-committed result |
| RV010 | Restart with complete STAGED import: validate and apply NEW/NEW |
| RV011 | STAGED journal with missing staged input: OLD/OLD, reject and select input again |
| RV012 | Interrupted swap, target missing and valid rollback present: restore OLD/OLD |
| RV013 | Interrupted swap, valid new target and rollback present: validate then finish NEW/NEW |
| RV014 | Interrupted swap, invalid target and valid rollback: restore OLD/OLD |
| RV015 | APPLIED marker exists but generation still OLD: validate target and finish generation NEW before cleanup |
| RV016 | Commit and generation durable, cleanup interrupted: keep NEW/NEW, idempotent cleanup |
| RV017 | Rollback IO failure: NONE_BLOCKED/OLD, preserve all remaining recovery sources |
| RV018 | Malformed journal: preserve OLD/OLD and artifacts, BLOCKED pending inspection |
| RV019 | No valid target or rollback recovery source: NONE_BLOCKED/UNCHANGED, BLOCKED; never open a new empty store |
| RV020 | Old-generation worker completion: keep current NEW/NEW, STALE_GENERATION, no active writes |
| RV021 | Matching-generation worker: current NEW/NEW, writes allowed within its own atomic unit |
| RV022 | Generation absent: classify unknown, no new authorization to write; reconcile/requeue with explicit current generation |
| RV023 | Export with committed WAL data: active OLD/OLD unchanged; archive contains all committed rows at the snapshot point |
| RV024 | Export destination failure: active OLD/OLD unchanged; clean export temp, report STORAGE_IO |
| RV025 | Truncated existing store on open: preserve bytes, NONE_BLOCKED/UNCHANGED; no empty-store replacement |
| RV026 | Migration storage failure: rollback the migration atomic unit to OLD/OLD |
| RV027 | Workspace atomic write storage failure: OLD/OLD remains readable |
| RV028 | Restart after committed workspace replacement: NEW/NEW remains readable |
| RV029 | Valid empty REPLACE module: EMPTY/UNCHANGED, successful commit |
| RV030 | Valid empty UPSERT module: OLD/UNCHANGED, successful no-op/upsert |
| RV031 | Missing required payload field: OLD/UNCHANGED, INVALID_INPUT; no active write |
| RV032 | Module payload durable, ledger completion interrupted: NEW/UNCHANGED, reconcile ledger; preserve committed payload |

Retry `after` strings must describe the actual prerequisite, not the word RETRY alone. In
RV020–022 OLD/NEW refer to stale/current workspace generations, not an import candidate.
Export fixtures carry no candidate database bytes; OLD denotes the unchanged current store.

## Android projection and evidence limits

Only RV010–014, RV016 and RV020–022 carry projections in this phase (9 total).
Recovery projections contain `kind: RECOVERY_DECISION`, journal (stage/generation/detail),
four file-presence booleans and expected `RestoreRecoveryAction`. Generation projections
contain `kind: GENERATION_DECISION`, expected/current generation and expected decision.
Use the actual enum names from `RestoreProtocol.kt`; nullable journal remains representable.

The consumer checks routing for RV010–014/RV016 and classification for RV020–022 only.
It does **not** prove target validation, copy/rename/fsync, cancellation, worker write guards,
or end-state recovery. In particular, LEGACY is the current classifier result for RV022;
current workers allowing legacy work to proceed is an adoption gap, not a safe-write proof.

RV015 and RV017 must not bind unsafe current cleanup to normative success. Existing Android
APPLIED cleanup does not repair generation; failed rollback is written as ROLLED_BACK and
may then be cleaned. These are static risks to reproduce/fix in 127, not passed contract cases.
Likewise `prepareImport` creates its temp before its catch block; actual ENOSPC handling is
not established. Parent-directory fsync/power-loss durability is not proven on any host here.

## Adoption and acceptance

Publish a platform matrix with exact source owners, atomic units, consumer IDs and level:
CORPUS_VALIDATED, DECISION_ONLY, FILE_SIMULATION or DEVICE_FAULT. Only record levels actually
executed. Windows/Apple do not gain restore entries from this phase; Linux remains on PR16.
Windows/Apple replace empty responses while Linux currently upserts; retain that distinction.

Acceptance: strict corpus positive/negative tests, all 9 direct production-decision consumers,
fresh full Android JVM tests and assembleDebug, independent review, relative-link/diff checks,
and a scoped PR stacked on Phase121–125 evidence. No application version or binary release.
Every vector has old/new outcome, retry prerequisite and restart action, with unsupported
platform consumers explicitly assigned to 127/128/129/130. Do not claim full platform recovery.
