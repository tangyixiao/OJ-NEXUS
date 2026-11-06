# Phase 132 A · Android Portable Workspace Import/Export — Implementation Plan

> Executed with parallel sub-agents under controller coordination
> (user-directed). Each task owns a disjoint file set in its own worktree/branch;
> the controller merges, reviews, gates and publishes.

**Goal:** ship a Portable Workspace entry separate from whole-DB backup, with a
read-only preview (counts, sources, skips, conflicts), user-confirmed
transactional apply, cancellable SAF access, lossless round-trip of the mixed
golden package, idempotent reimport, out-of-contract rejection, synthetic test
source markers and saved state for stored-only records. Deliver onto `main`
through many merged PRs and an independent review.

**Spec:** `docs/superpowers/specs/2026-11-06-phase-132-portable-import-export-design.md`

**Baseline:** main `5a8134723680f9743d7e59f9a3244c4ced55fe8a` (phase 131).

## Global constraints

- AGENTS.md: AGP built-in Kotlin only (never apply `org.jetbrains.kotlin.android`),
  all UI strings in `res/values/strings.xml`, design-system tokens only (no
  raw `Color(0xFF...)`, arbitrary `.dp/.sp`, `RoundedCornerShape` in feature
  code), dark-first single accent, no forbidden visuals, no secrets, conventional
  commits, every stage ends with `BUILD SUCCESSFUL`.
- `BackupRepository.kt` and the whole-DB backup UI are out of scope. The new
  entry is separate in state, dialog, launchers and repository.
- Contract `contracts/portable/v1/` is READ-ONLY. The Android validator must
  agree with `check.py` on all 15 golden cases with exact wire categories.
- UI navigation: keep the existing `NexusRoutes.SETTINGS` composable. Phase 132
  adds a distinct `NexusRoutes.PORTABLE_WORKSPACE` composable hosting
  `PortableWorkspaceScreen`, reached by a settings action row. No changes to
  `DashboardCommand` required.
- The controller owns the frozen shared API files listed in the spec. Every task
  branch is created from the integration branch AFTER those files are merged, so
  each branch compiles against the same API. Disjoint file sets per task.
- `gh auth setup-git` is required on this Windows host: SSH `git ls-remote` hits
  the `198.18.x.x` MITM; HTTPS via `gh` works. Use `gh` for PR/merge/remote
  reads and `git fetch https://github.com/tangyixiao/OJ-NEXUS.git <branch>`.
- PR bodies include the linked dsh badge. No force push. No workflow-file edits.

## Task 1 — strict JSON codec + contract validator (parallel wave A)

**Files (exclusive):** `app/src/main/java/com/ojnexus/core/portable/PortableCodec.kt`
and `app/src/test/java/com/ojnexus/core/portable/PortableCodecTest.kt`.

Implement `PortableCodec` against the frozen `PortableModels`/`PortableValue`
API: strict UTF-8 decode, strict JSON parse with duplicate-key and
NaN/Infinity rejection, closed-schema validation (root + 5 collections), enum/
pattern/limit checks, credential-shaped keys, `extra` depth, PATH_UNSAFE,
DUPLICATE_ID, MISSING_REFERENCE, COUNTS_MISMATCH — in `check.py` precedence
with exact `PortableRejectCategory` wire values. Provide `validate`, `classify`,
`preview`, `identity` (SHA-256 of canonical bytes), `rebuild`. Test all 15
golden cases (load via `javaClass.getResourceAsStream("/portable/v1/golden/…")`,
the `contracts` test resources sourceSet already exists) plus canonical identity
of `pw-p01.json` equals `81bb13c1746381f2d7f95ae69e421503dcc898aae823222e8a3caf40d55b8d44`.
No Android/Room/Compose dependencies.

## Task 2 — Room bounded archive store + migration (parallel wave A)

**Files (exclusive):**
`app/src/main/java/com/ojnexus/core/portable/archive/PortableArchiveEntities.kt`,
`app/src/main/java/com/ojnexus/core/portable/archive/PortableArchiveDao.kt`,
`app/src/main/java/com/ojnexus/core/portable/archive/RoomPortableArchiveStore.kt`,
`app/src/main/java/com/ojnexus/core/database/Migration15To16.kt`,
`app/src/test/java/com/ojnexus/core/portable/PortableArchiveStoreTest.kt`
plus edits to `app/src/main/java/com/ojnexus/core/database/OjNexusDatabase.kt`
(version 15→16, register entities/getter/migration) and the exported schema
`app/schemas/com.ojnexus.core.database.OjNexusDatabase/16.json`.
The database file is shared only with Task 6's AppContainer wiring (no overlap:
Task 2 owns the @Database block, Task 6 owns `OjNexusApplication.kt`).

`@Entity` packages + records tables, `PortableArchiveDao` with `@Transaction
replaceAll` and read methods, `RoomPortableArchiveStore : PortableArchiveStore`.
`MIGRATION_15_16` creates the two tables. Robolectric in-memory test proves
load/replace/clear and idempotent upsert. Do not modify `BackupRepository`.

## Task 3 — repository, preview/apply/export orchestration (parallel wave A)

**Files (exclusive):**
`app/src/main/java/com/ojnexus/core/portable/PortableRepository.kt`,
`app/src/test/java/com/ojnexus/core/portable/PortableRepositoryTest.kt`.

`class PortableRepository(store: PortableArchiveStore, codec: PortableCodec =
PortableCodec()) : PortableWorkspaceRepository`. `preview(bytes)` is a pure read
(no DB writes); `apply(bytes)` is user-confirmed and transactional (reject whole
package on any contract error, no-op on identical identity, replaceAll in one
transaction otherwise); `export()` rebuilds canonical bytes from archived rows
and asserts the rebuilt identity; `state` is a `StateFlow<PortableWorkspaceState>`;
`cancel()` clears pending preview with no writes. Unit tests cover preview/cancel
DB immutability, idempotent reimport, whole-package rejection, synthetic marker,
stored-only saved state. Use a fake `PortableArchiveStore` (no Room dependency).

## Task 4 — Portable Workspace screen, ViewModel, SAF (parallel wave A)

**Files (exclusive):**
`app/src/main/java/com/ojnexus/feature/portable/PortableWorkspaceScreen.kt`,
`app/src/main/java/com/ojnexus/feature/portable/PortableWorkspaceViewModel.kt`,
`app/src/main/res/values/strings.xml` (append only — Task 4 is the sole owner),
`app/src/main/java/com/ojnexus/app/NexusRoutes.kt` (add `PORTABLE_WORKSPACE`
route constant; if that object lives in `NexusApp.kt`, edit the minimal route
block there instead), `app/src/main/java/com/ojnexus/app/NexusApp.kt` (add the
composable entry only), `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
(add ONE settings action row that navigates to the portable screen; leave all
backup code untouched).

SAF `CreateDocument("application/json")` export and `OpenDocument()` import
launchers with null-callback cancellation; `rememberSaveable` pending source;
preview→confirm→apply dialog showing counts, sources, skips, conflicts,
editable/storedOnly/rejected, synthetic TEST SOURCE label and SAVED state;
Loading/Empty/Error/Success handled; all strings via `stringResource`; design
tokens only. ViewModel takes `PortableWorkspaceRepository` via
`ContainerViewModelFactory(container) { … }`.

## Task 5 — independent golden round-trip + verification docs (parallel wave A)

**Files (exclusive):**
`app/src/test/java/com/ojnexus/core/portable/PortableGoldenRoundTripTest.kt`,
`docs/verification/phase-132-portable-import-export.md`,
`docs/PHASE_132_HANDOFF.md`, `docs/verification/phase-132-plan-ledger.md`.

The test loads the mixed golden, asserts import→apply→export identity equals the
Python reference, reimport is idempotent, preview/cancel do not modify the DB,
an out-of-contract field is rejected, synthetic records are marked, stored-only
records expose saved state. Docs adopt the phase-131 house style (VERIFICATION
record with measured results, HTML comment sign-off, G-A coverage table,
claims vs non-claims; handoff; plan ledger). Status starts `PENDING` and is
flipped to `VERIFIED` by the controller only after measured gate results.

## Task 6 — controller integration, gate, review, delivery

**Files (controller-owned):** frozen shared API
(`PortableModels.kt`, `PortableValue.kt`, `PortableCodec.kt` stub is Task 1,
`PortableArchiveStore.kt`, `PortableWorkspaceRepository.kt`),
`app/src/main/java/com/ojnexus/OjNexusApplication.kt` (AppContainer val),
`docs/LONG_RANGE_PLAN_121_220.md` (tick line 261 after verification),
`SHA256SUMS` housekeeping, and all PR/merge/review orchestration.

Branch/PR topology (integration branch = `codex/phase-132-portable-import-export`):
1. Controller commits the spec, plan, frozen shared API and PortableValue.kt to
   the integration branch as PR #41 (base main) — merged before task branches
   are cut.
2. Six task branches `codex/phase-132-task-N-<slug>` are cut from the merged
   integration branch; each makes conventional commits and opens its own PR
   (base = integration branch). The controller merges them one by one,
   resolving only integration conflicts, and keeps the tree green.
3. An independent review subagent audits the final diff; findings are fixed in
   a follow-up conventional commit PR if needed.
4. G-A gate `tools/gradlew-local.bat test assembleDebug` must print
   `BUILD SUCCESSFUL`; verification record + ledger + handoff receive measured
   results; roadmap line 261 is ticked; final delivery onto `main` via a PR
   from the integration branch (badge in body), merged via `gh`.

## Review focus

- Preview/cancel perform zero DB writes; apply is a single Room transaction.
- Closed schema rejects out-of-contract fields with the exact wire category;
  no silent row dropping and no "full success" on partial data.
- Mixed golden import→export identity is byte-identical to the Python checker;
  reimport is idempotent; `origin=unassigned` and `sourceScopeId` are preserved.
- Synthetic records carry `SYNTHETIC-TEST-FIXTURE`; stored-only records expose
  an explicit SAVED state; editable/storedOnly/rejected counts are all shown.
- The Portable Workspace entry is genuinely separate from whole-DB backup
  (state, dialog, launchers, repository); SAF cancellation is a no-op.
- AGENTS.md compliance: strings, tokens, no raw colors, AGP built-in Kotlin,
  no TODO stubs, CI-equivalent gate green.
