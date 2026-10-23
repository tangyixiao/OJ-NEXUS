# Phase 126 — Recovery Fault Vectors

Status: VERIFIED for the shared contract and direct Android decision-consumer scope. This phase does not certify physical
crash recovery on any platform. Source base: `becc0e3` on `codex/phase-121-platform-baseline`.

## Scope and inputs

- [Design](../superpowers/specs/2026-10-23-phase-126-recovery-vectors-design.md)
- [Implementation plan](../superpowers/plans/2026-10-23-phase-126-recovery-vectors.md)
- [Canonical JSON](../../contracts/recovery/v1/vectors.json)
- [Contract and field semantics](../../contracts/recovery/v1/README.md)

The corpus contains 32 synthetic normative cases, RV001–RV032. Nine cases have Android
projections: RV010–RV014, RV016 and RV020–RV022. A projection checks existing production
routing or generation classification. It does not execute file replacement, rollback, a worker
write, ENOSPC or a process crash. Future platform consumers need concrete setup, fault injection
and an independent end-state oracle for each adopted ID. Structural validation alone proves none
of those storage outcomes.

## Adoption and atomic units

| Platform/source audited | Current storage boundary | Phase 126 adoption | Follow-up |
| --- | --- | --- | --- |
| Android `becc0e3`; reviewed Phase 122 composition `19f0bbc` remains unmerged | Restore journal, database/WAL/SHM rollback set and separate generation file | Nine pure production decisions executed; no physical fault consumer | Phase 127: generation repair, rollback failure, startup blocking, staging ENOSPC and worker writes |
| Windows source at `becc0e3` | Schema migration transaction; each module's ledger/payload update is transactional; an entire multi-module sync is not | No new native consumer | Phase 128: interrupted ledger reconciliation and database fault tests |
| Linux PR #16 source `bf2dbea`, not main | Each payload stage is transactional; ledger update is separate; fresh schema bootstrap is not one transaction | No new native consumer | Phase 129: bootstrap and payload/ledger crash windows; use actual configuration authority |
| Apple active LocalWorkspaceStore/NexusDashboardModel at `becc0e3` | Whole-workspace JSON saved with `Data.write(.atomic)` per operation; a full sync is not one transaction | No new native consumer | Phase 130: damaged-file preservation and interrupted workspace writes; macOS/Xcode runtime prerequisites remain |

Linux currently reports a config path without an implemented independent JSON configuration
writer. Phase 129 must inspect the actual SQLite settings authority before adding recovery logic;
this contract does not introduce another configuration store.

RV029 describes valid empty replacement; RV030 describes valid empty upsert. These intentionally
preserve existing platform policies. RV031 is missing/invalid payload and cannot be treated as
valid empty data. RV032 retains the committed payload while reconciling an incomplete ledger.

## Android handoff: source inspection, not injected fault results

- RV015: the existing APPLIED restart branch clears artifacts without repairing generation.
  The normal apply/validate broad catch also treats later generation or cleanup failures as swap
  failures. A repair must separate choosing forward convergence from completing the logical
  commit: validated target and the exact durable generation must agree before normal access.
- RV017: rollback currently moves each source back separately. If main moves successfully and
  WAL restoration fails, the only old main may be at the live path while its WAL remains under
  rollback. Terminal cleanup can lose the WAL; a naive retry can delete the only old main.
  Preserve recovery sources and test repeated startup, not just one failure return value.
- Startup must distinguish a clean first install with no recovery evidence from an interrupted
  restore with no verified authoritative database. Returning an error is insufficient if Room
  and workers subsequently open or create a database anyway.
- RV004–RV006 require actual temporary-allocation/copy/journal failures. A missing file fixture
  is not an ENOSPC injection. Snapshot extraction and raw import need separate typed outcomes
  so storage failure cannot become a raw-format fallback.

Old-engine snapshot export remains fail-closed. An ordinary in-process raw file copy under an
SQLite lock is not an accepted fallback: POSIX file-descriptor close can invalidate that process's
SQLite locks. Phase 127 needs a separate compatibility design and genuine old-engine acceptance.
See [SQLite's documented POSIX close hazard](https://www.sqlite.org/howtocorrupt.html#posix_advisory_locks_canceled_by_a_separate_thread_doing_close_).

## Verification gates

Implementation source: `6a5e2f4` on `codex/phase-126-recovery-vectors`, based on `becc0e3`.
Task 1 strict loader/corpus and Task 2 decision-consumer reviews passed. Independent whole-branch
review of `becc0e3..1a74c46` passed with no actionable P1/P2 findings, including all 32 cases,
their preconditions, strict decoding, nine bindings and the platform evidence boundaries.

| Check | Result |
| --- | --- |
| Focused corpus/consumer/original protocol/generation run | 24 tests, 5 suites; 0 failures/errors/skips |
| Fresh `gradlew.bat testDebugUnitTest --rerun test assembleDebug` | Exit 0; BUILD SUCCESSFUL in 1m55s; 46 tasks, 4 executed, 42 up-to-date; unit task actually reran |
| Fresh debug unit XML | 794 tests, 173 suites; 0 failures, 0 errors, 0 skipped |
| New contract tests within the full run | 5 corpus tests, 9 parameterized decisions, 1 coverage guard; all pass |

Decision XML names contain RV010, RV011, RV012, RV013, RV014, RV016, RV020, RV021 and RV022,
each once. The other 23 cases, including RV015/RV017, remain explicitly unbound. XML source:
`app/build/test-results/testDebugUnitTest/TEST-*.xml`. The local full log is retained during review
in the ignored Phase 126 workspace. Existing Conscrypt native-access and DataStore/protobuf
Unsafe deprecation warnings occurred; no test failed because of them.

Built debug APK SHA256: `7949cd34b006e6ea4488f0a1bd0c32245b68f0ca5891794300978bccfdedcac9`.
This build has no application feature/version change and is not a new Android release.
No emulator, real-device fault, native desktop recovery, old SQLite engine or power-loss result
is claimed by this phase.
