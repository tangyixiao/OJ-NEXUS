# Phase 130 — Apple active workspace safety

Status: implementation design under the authorized balanced 121–220 roadmap. The controller
selects these implementation details; they are not additional user-approved requirements.
Scope: the active `LocalWorkspaceStore`, its dashboard model and SwiftUI callers, plus native
macOS/iOS recovery execution. Legacy `LocalLedgerStore` remains test-only and is not promoted
to a second authority. Baseline: `036b333c48c860061fd460fa049717e3aee49aaa`.

## Problem and outcome

The current store treats `fileExists == false` as first use, ignores unknown JSON version
fields, and permits saves without a successful load. Dashboard startup immediately rewrites
the loaded state. After a failed load, account actions can overwrite the unreadable file with
an empty workspace. Atomic replacement alone does not protect against these paths, stale
writers, or loss of the previous valid state.

Successful startup will read without rewriting. Failed startup will keep the last available
in-memory cache visible while blocking every mutation and network sync. Supported old files
upgrade only on a subsequent deliberate successful save. A validated predecessor snapshot and
an explicit quarantine/restore action provide recovery without silently replacing bad data.

## File format and authority

`state.json` remains the sole authority. `state.json.bak` is a recovery snapshot, not a live
ledger. New files are flat JSON with `formatVersion: 1` and the existing accounts, ledger,
profiles, ratingChanges and submissions fields. Accounts and ledger remain required; the
three existing optional collections retain their legacy empty defaults. Unversioned files
containing only these known keys are supported. Unknown root keys, future versions, invalid
version types (including boolean/fractional values), and unsupported old numbered versions
are rejected before ordinary Codable decoding can discard them. Malformed known content is
corrupt. No automatic downgrade or lossy rewrite is permitted.

Keep `LocalWorkspaceState` as the domain Codable type. A focused internal `WorkspaceFileCodec`
validates the root and controls the disk version. Preserve exact predecessor raw bytes,
including formatting, in the snapshot. `load()` creates neither state nor snapshot files.
Resolve Application Support explicitly; inability to resolve it is unavailable, with no
temporary-directory fallback. The explicit URL initializer remains available for tests.

## Storage state machine

Expose typed `WorkspaceStorageFailure` and `WorkspaceStorageStatus` values to the model.
Failures include notLoaded, corrupt, unsupportedVersion, unavailable, busy, conflict,
backupUnavailable, recoveryUnavailable, uncertainOutcome and missingEstablished. Status is
unloaded, ready, blocked(failure), or recoverable(failure). Public actor operations are
`load() throws -> LocalWorkspaceState`, `save(_:) throws`, `storageStatus()`, and
`restorePreviousSnapshot() throws -> LocalWorkspaceState`. A nonthrowing default initializer
captures location-resolution failure; `defaultURL(fileManager:) throws -> URL` never invents
a temporary store. Tests may use internal per-instance configuration/fault seams.

Every save requires this actor's prior successful explicit load. A successful load remembers
the exact raw bytes, or absence for genuine first use. Failed load blocks save. Directly read
regular files; only proven ENOENT with valid/missing directory ancestry is absence. Access
failure, non-directory parents, directories/special entries and ambiguous errors are failures.
If main is missing but a snapshot exists, or this actor previously observed an established
main, report missingEstablished rather than starting empty. A supported snapshot makes that
state recoverable; an invalid snapshot blocks it. If both main and snapshot were removed
before a fresh actor starts, historical existence cannot be reconstructed in this slice.

## Write protocol and concurrency

Use a small internal Darwin I/O implementation, not undocumented combinations of Foundation
atomic/no-overwrite options. Stage complete bytes under a unique same-directory name opened
with O_CREAT|O_EXCL, restrictive permissions and robust partial-write/EINTR handling. Close
the stage before install. Use `link` for absent-target exclusive installation and `rename`
for replacing an established target. Clean only stages this operation owns.

All save and restore operations open the persistent `state.json.lock` independently and hold
a nonblocking exclusive flock throughout current-byte validation, baseline comparison,
snapshot and main installation. Busy means a typed failure. Never unlink the lock file;
different actors/processes must contend on its stable inode. Cooperating writers cannot
silently overwrite each other: require current bytes to equal the actor's loaded/saved
baseline. Revalidate current bytes before replacing them. A changed/missing/created file is
a conflict requiring reload. First-create admission also requires the snapshot still absent.

Before replacing main, atomically install its validated current raw bytes as the snapshot.
On the first save, install the validated intended new bytes as snapshot before exclusively
creating main; this makes interruption before first main install recoverable. Snapshot failure
aborts before main replacement. Never refresh a snapshot from corrupt/future main bytes.
There is one rotating snapshot slot; existing unique quarantines are retained.

A fault before main installation preserves the old main. An error after installation must
be reconciled while still locked: if current main equals intended bytes, report committed
success and advance baseline; otherwise report uncertainOutcome and require reload. The UI
must not roll back memory after a proven committed save. Do not add cancellation checks that
prevent the existing sync cancellation path from saving its final ledger state.

These guarantees cover cooperating processes and logical file replacement on one filesystem.
They do not claim hostile namespace protection, noncooperating writers, or full power-loss
durability. Advisory lock and atomic rename are not such guarantees.

## Explicit recovery

Only corrupt or missing-established main with a valid supported snapshot is eligible.
Future/unknown formats remain blocked; recovery must not downgrade them. On failed inspection,
remember observed main and snapshot raw bytes. Restore reacquires the same lock, rereads and
validates both, and rejects any change since inspection. For corrupt main, first preserve all
raw bytes under a unique no-overwrite `state.json.quarantine-<UUID>.json`, then atomically
install snapshot bytes. Missing main is exclusively installed without inventing quarantine
bytes. Snapshot is unchanged. Any validation/quarantine/pre-install failure preserves main.
Reconcile post-install faults as for save. Restore returns loaded state and establishes ready.

## Model and native UI

`NexusDashboardModel` publishes storageStatus, isSaving and isRecovering, with shared computed
mutation/readiness guards. Loading begins read-only and never calls save. Recover interrupted
ledger operations in memory; persist them only with a later admitted write. Keep existing
cache on load failure and prioritize storage diagnostics over generic action errors.

Guard account create/enable/remove, every direct and task-based sync, retries and batches
before memory mutation, ledger append or network work. Account operations serialize across
awaits. Reload/restore cannot race active saving/sync/recovery. A storage failure blocks later
modules in a batch. Sync cancellation remains available and existing cancellation semantics
and retained failure history remain covered. The idle `refresh` cannot rewrite an active
RUNNING operation. Apply actor storage status after failures; rollback memory only for known
uncommitted saves, while uncertain results remain blocked until reload.

All mutating SwiftUI controls use the same readiness guards. Show clear uppercase local-state
status on each page. Provide `RELOAD LOCAL STATE` and, only when recoverable, an explicit
`RESTORE VALID SNAPSHOT` confirmation describing quarantine and snapshot replacement. Keep
navigation/read-only cached views usable. Follow existing native SwiftUI palette/layout and
English copy; Android resources are not an Apple string system. No placeholder success.

## Verification and delivery

Before implementation, add current-public-API behavioral tests for corrupt startup overwrite,
ignored future formatVersion, startup rewrite, and direct save without prior load. Run them
on macOS CI and record actual assertion failures; compile errors do not count as RED.
Migrate existing fixtures to load before save/action without weakening their assertions.

New focused XCTest files cover format/legacy upgrade, exact bytes, absent vs unreadable paths,
snapshot and quarantine validation, missing established state, stale actors, independently
held flock, overlapping actor/model actions, pre/post-install failures, post-install uncertain
outcome and storage failure stopping sync/network. Use temporary per-test URLs, no user data
or global fault switches. Preserve all existing adapter and cancellation tests.

Run full `swift test`, build OJNexusMacOS, and build OJNexusIOS for generic iOS Simulator.
Additionally discover actual testable package scheme and installed simulator on the macOS
runner, and execute a nonzero `WorkspaceRecoveryTests` suite on an iOS 16+ simulator. Save
discovery logs, test output and xcresult, with structured nonzero/pass checks. Missing runtime,
zero tests, unsupported statistics tool, compilation or test failure fails this gate. Do not
silently install a large runtime. Shared XCTest execution does not establish interactive app
UI or physical-device acceptance; record those separately as pending when not executed.

Root independently reviews source, task and whole-branch findings, and native evidence. Use a
scoped PR against `codex/phase-130-apple-base`; do not merge main. A draft during RED is honest
and reviewable. No Apple binary release without a validated distribution artifact. Existing
Windows release and other platform PRs retain their own source/evidence boundaries.

## Alternatives considered and controller decisions

Only adding model guards leaves direct store callers and other windows unsafe. Only adding
an atomic write leaves corrupt/future decode and missing-first-use errors unsafe. A second
database, transport, journal subsystem or generic transaction framework is unnecessary for
this one active workspace file. Choose typed admission plus exact-byte locking/CAS, one
snapshot and explicit recovery. Keep low-level I/O and format validation separate and small.
This fulfills the active-file portion of Phase 130; interactive macOS/iOS recovery remains an
explicit acceptance gap until a native app session is actually exercised.

API research: [Apple fileExists](https://developer.apple.com/documentation/foundation/filemanager/fileexists%28atpath%3A%29),
[link](https://developer.apple.com/library/archive/documentation/System/Conceptual/ManPages_iPhoneOS/man2/link.2.html),
[rename](https://developer.apple.com/library/archive/documentation/System/Conceptual/ManPages_iPhoneOS/man2/rename.2.html),
[flock](https://developer.apple.com/library/archive/documentation/System/Conceptual/ManPages_iPhoneOS/man2/flock.2.html),
and [Xcode test execution](https://developer.apple.com/documentation/xcode/running-tests-and-interpreting-results).
