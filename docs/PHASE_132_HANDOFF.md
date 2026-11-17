# Phase 132 Portable Workspace Import/Export (Android) — Handoff

Status: delivered on the integration branch; final delivery to `main` via PR (see Links).
Date: 2026-11-18 (Asia/Shanghai).
Baseline main: `5a81347` (phase 131 contract delivery).
Integration branch: `codex/phase-132-portable-import-export`.

> Current state at a glance
> Phase 132 shipped the Android consumer of the phase 131 Portable Workspace v1
> contract: a Portable Workspace entry in Settings that is fully separate from
> whole-database backup, with SAF document import/export, a read-only preview
> (counts, sources, skips, conflicts, capability tiers), a user-confirmed
> transactional apply, cancellable transfers, synthetic-test-source marking and
> saved-state surfacing. It is delivered through twelve merged PRs (controller
> wiring + task branches + hardening + findings disposition), an independent
> review record, the G-A gate (`test assembleDebug` → `BUILD SUCCESSFUL in
> 1m 55s`, 896 unit tests, 0 failures) and the verification record. Phases
> 133-135 bind the Windows/Linux/Apple consumers to the same contract.

## 1. What shipped

- Frozen shared API (controller-owned, PR #41): `PortableModels.kt`,
  `PortableValue.kt`, `PortableArchiveStore.kt`, `PortableWorkspaceRepository.kt`.
- Strict canonical JSON codec (PR #41): `PortableCodec.kt` — strict UTF-8,
  duplicate-key rejection, closed schema, enum/pattern/limit checks,
  credential-shaped keys, the eight wire categories in `check.py` precedence,
  validate/classify/preview/identity/rebuild.
- Room bounded archive store + migration 15→16 (PR #42): `archive/` entities,
  `PortableArchiveDao` (`@Transaction replaceAll`), `RoomPortableArchiveStore`,
  `Migration15To16`, exported schema `16.json`.
- Transactional repository (PR #44): `PortableRepository` — read-only preview
  with real conflict enrichment, confirmed single-transaction apply with
  idempotent identity no-op, identity-verified export, cancellable apply.
- Independent golden round trip (PR #43): `PortableGoldenRoundTripTest` — the
  mixed golden package survives import→apply→export byte-identically; the
  corpus loop covers all 15 golden cases.
- Portable workspace UI (PRs #50/#49/#51/#53): `PortableWorkspaceViewModel`
  (transport intents, superseded-preview cancellation, honest failure taxonomy,
  derived TEST SOURCE flag), `PortableWorkspaceScreen` (SAF `OpenDocument`/
  `CreateDocument` launchers with null-callback cancellation, read-bounding,
  confirmed-import dialog, text-carried status region), the Settings entry row,
  the `PORTABLE_WORKSPACE` destination, 43 strings + zh-rCN mirrors, and the
  findings disposition (KDoc limits, neutral SAVED wording, supersede test).
- Controller wiring (PR #46): `AppContainer.portableWorkspaceRepository`.
- Tests: 47 portable unit tests (`PortableGoldenRoundTripTest` 7,
  `PortableArchiveStoreTest` 7, `PortableRepositoryTest` 14,
  `PortableWorkspaceViewModelTest` 19), all green in the G-A gate.
- Documentation: plan ledger (PR #47), verification record scaffold→flip
  (PR #48 + delivery), independent review record (PR #52), this handoff.

## 2. Key decisions and rationale

- **Separate store, not the backup path.** The portable archive is its own
  Room tables; `BackupRepository` and the whole-DB backup UI are untouched.
  The Settings entry is a distinct action row navigating to a distinct route.
- **Capability negotiation lives below the UI.** The codec tiers records into
  editable / storedOnly / rejected; the repository enriches conflicts against
  the stored identity; the screen only renders what the frozen contract
  reports. The UI invents no counts.
- **Transport intents vs contract intents.** The ViewModel owns the SAF
  document phases the repository does not model (reading/rebuilding/writing)
  and delegates validation/preview/apply/cancel verbatim to the repository;
  every superseding intent cancels the in-flight preview job so a stale dialog
  cannot surface.
- **Derived TEST SOURCE.** The frozen preview carries no synthetic flag, so the
  UI derives it from the generator name (`synthetic-fixture-gen`, prefix match)
  or a `SYNTHETIC-TEST-FIXTURE` state message — documented label-only limits
  (conservative prefix false positive; per-record `extra.synthetic` invisible).
- **Honest empty/identical semantics.** `identical` applies report zero rows;
  export from an empty archive fails honestly; the archive-state line describes
  behavior instead of claiming presence (review finding F3).
- **zh-rCN parity is a test-enforced invariant.** `LocalizationResourceTest`
  requires exact key parity and placeholder parity between `values` and
  `values-zh-rCN`; every new string must land in both (PR #51 was the gate
  catching this).

## 3. How to verify

From the repo root on Windows (machine-local helper pins JAVA_HOME):

```
tools\gradlew-local.bat test assembleDebug
```

- Expected: `BUILD SUCCESSFUL`, JUnit XML aggregate 896 tests / 0 failures /
  0 errors across 189 classes (measured on the final head `f2f0517`; see the
  verification record).
- The portable corpus and the phase 131 contract gates still apply:
  `python3 check.py --cases golden/cases.json golden/` from
  `contracts/portable/v1/` (CI workflow `contracts.yml` unchanged).
- Review record: `docs/verification/phase-132-task4-review.md` (static audit,
  PASS-WITH-OBSERVATIONS, findings routed in PR #53).

## 4. Open work / next phases

- Phases 133-135 bind Windows/Linux/Apple consumers to the same contract; the
  mixed golden round trip is the shared acceptance vehicle.
- Review observations accepted without change (cosmetic, write-free): F4
  (no VALIDATING/APPLYING status lines during short in-flight windows) and F6
  (cancelled export picker resets a pending import offer — coherent but
  implicit coupling; split a lighter reset intent if the flows ever diverge).
- v2 keeps its own version stance; no automatic upgrade path from v1. No
  credentials, cookies, tokens or account configs are portable in any phase.

## 5. Gotchas for the next agent

- **`tools/gradlew-local.bat` is machine-local and gitignored.** Fresh
  worktrees do not have it — create `tools/` and copy the helper from the main
  checkout before building there; it `cd`s to its own parent, so per-worktree
  copies are the pattern.
- **Invoke the helper by absolute path** (`cmd /c "D:\...\tools\gradlew-local.bat ..."`).
  Relative invocation after a `cd` in the DSH shell can fail with "The system
  cannot find the path specified".
- **CI (`.github/workflows/android.yml`) triggers only on `main`** push/PR —
  PRs against the integration branch run no CI; the controller verifies
  locally and records measured results.
- **Strings are parity-paired**: every new `values/strings.xml` key needs its
  `values-zh-rCN` mirror or `LocalizationResourceTest` fails the gate.
- **The DSH shell is cmd-like**: no `for` loops, `;` chaining mangles
  arguments — use `&&` chains or separate calls; `git push origin` over SSH
  worked on this host throughout the phase (the phase 131 MITM note no longer
  reproduced).
- `sha256sums`-style housekeeping: the untracked user file `SHA256SUMS` at the
  repo root remains untouched, per the standing handoff note.

## 6. Links

- Spec: [docs/superpowers/specs/2026-11-06-phase-132-portable-import-export-design.md](superpowers/specs/2026-11-06-phase-132-portable-import-export-design.md)
- Plan: [docs/superpowers/plans/2026-11-06-phase-132-portable-import-export.md](superpowers/plans/2026-11-06-phase-132-portable-import-export.md)
- Plan ledger: [docs/verification/phase-132-plan-ledger.md](verification/phase-132-plan-ledger.md)
- Verification record: [docs/verification/phase-132-portable-import-export.md](verification/phase-132-portable-import-export.md)
- Review record: [docs/verification/phase-132-task4-review.md](verification/phase-132-task4-review.md)
- Developer guide: [docs/PORTABLE_WORKSPACE.md](PORTABLE_WORKSPACE.md)
- Contract: [contracts/portable/v1/README.md](../contracts/portable/v1/README.md)
- Merged PRs: #41 #42 #43 #44 (foundation/store/roundtrip/repository),
  #46 (wiring), #47 (ledger), #48 (record), #49/#50 (task 4b/4a),
  #51 (zh mirror), #52 (review), #53 (findings disposition),
  #54 (delivery to `main`).
- Phase 131 handoff: [docs/PHASE_131_HANDOFF.md](PHASE_131_HANDOFF.md)

Handoff status: this document is the current authority for the Phase 132
Android delivery. Sections 1-6 are authoritative; earlier phases keep their
own handoff documents.
