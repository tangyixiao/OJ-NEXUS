# Phase 129A — Linux guarded SQLite startup

Status: **LOCAL ACCEPTANCE PASSED; WHOLE-BRANCH REVIEW PENDING** (2026-10-24).
This is a startup prerequisite of Phase 129. Full Phase 129 remains open.
[Machine-readable summary](evidence/phase-129a-summary.json).

## Source and scope

- Integration base: `820db1f519e74c3be75d09aff07eb5b13562273b`, combining Linux
  `bf2dbea4d36884d52ed506466b44cc166dbbb023` and shared recovery-contract `036b333`.
- Feature: `codex/phase-129-linux-startup-safety`.
- Product/test source: `52c485c4714860f9b3513bd3d22023acc7b77962`.
- Linux tree: `48c6b1649cd9264e0cfebe14b6acec3ba228a6cf`.
- Design and plan: [spec](../superpowers/specs/2026-10-24-phase-129a-linux-storage-startup.md),
  [implementation plan](../superpowers/plans/2026-10-24-phase-129a-linux-storage-startup.md).

The change keeps SqliteStore as the public authority. Existing files receive a
read-only SQLite integrity/version/shape probe before any writable migration or
stale-operation recovery; the write connection revalidates. First use requires an
exclusive absent-file reservation, and metadata plus all initial schema objects
commit together. Failure retains Storage and schemaVersion zero. CLI and Qt retain
their existing storage-error paths. No JSON configuration writer was added:
`config/config.json` is currently an informational path, while SQLite holds settings.

Supported v1 payload migration and live/stale operation policy remain intact.
Validation checks full signed 64-bit versions before narrowing, actual SQLite
affinity precedence, generated operation-id semantics, and the nullability/default
requirements of existing inserts. Compatible owner columns without defaults remain
accepted because operation inserts explicitly supply those values.

## Observed failure and regression

The independent baseline used the real compiled `bf2dbea` CLI with eight isolated
data roots and two invocations per case. Existing zero-byte, missing-metadata and
empty-metadata stores incorrectly returned 0 and changed their main file. A new
zero-byte Qt test also failed behaviorally before the implementation.

The committed tests cover zero/non-SQLite/truncated files; absent, empty, duplicate,
NULL/text/real/future and large signed version values; missing tables/columns/PKs;
wrong affinities; non-alias BIGINT/DESC/WITHOUT ROWID operation keys; omitted-column
nullability/defaults; extra required columns with NULL defaults; and leading-zero
handle preservation. Rejected closed main files are hashed across two attempts.
Real operation lifecycle tests cover both supported generated-id forms and
owner-default-free compatible schemas. A deliberate middle-DDL collision proves
transaction rollback preserves the preexisting fixture and removes partial new DDL.

The competitor fixture proves an already reserved path is not overwritten. It does
not deterministically interrupt the exists()/NewOnly interval; the implementation
uses the OS exclusive-create primitive for that interval. A WAL fixture with another
live connection passed and observed both WAL and SHM sidecars. Sidecar immutability
is not claimed.

## Review and fresh build evidence

Independent task review found five Important validator gaps. Round 1 addressed all
five; its scoped review found one new false rejection of compatible owner columns.
Round 2 fixed that compatibility regression. Scoped rereview reported no new
Critical, Important or Minor issue. Whole-branch review is a separate final gate.

- Implementer round 1: full CLI/GUI build and CTest 8/8 passed at `2c6339d`.
- Round 2: focused store build and QtTest 57 reported passes, zero failed at
  `52c485c`. Qt totals include framework init/cleanup entries.
- Final independent build at `52c485c`: CLI/GUI and tests compiled, all eight CTest
  targets passed (150 Qt-reported passes including framework hooks), zero failed/skipped.
  Actual compiled CLI: eight cases, two invocations each; absent-file creation returned 0,
  all seven rejected existing-file cases returned 6 with unchanged main-file hashes.
- Android integration regression: `gradlew.bat test assembleDebug --rerun-tasks
  --offline --console=plain --no-daemon`, BUILD SUCCESSFUL, exit 0, 46/46 tasks
  executed; 173 suites / 794 tests, zero failures/errors/skips. The app tree is
  `e6eb8ef274a6c16718b01f0e3a2cc21c956d3dcf`, unchanged by this Linux slice.
  APK SHA-256 `8291BF201E4C181C8853B961CB585F85DC0565D9C3C540B2B2D30BD9C3A5FD40`.

The independent runner preserves explicit isolated XDG/runtime/data variables,
source hashes before/after, Windows Git commit/tree identity, exact commands, raw
configure/build/CTest output, JUnit, actual CLI output, binary hashes and a complete
build-tree copy before the WSL session exits. Evidence root:
`%TEMP%/ojnexus-phase129a-independent-3ed55f45b2fa401ea7b7693e4dd278a3`.
Baseline and earlier CLI observations remain under
`%TEMP%/ojnexus-phase129a-root-cli-15fbb5a6eb5c4c0e90030e5433264975`.
Android raw evidence is under
`%TEMP%/ojnexus-android-gate-4f78cd40192e4f1f8f1d2bc9bab05cf7`.
Earlier missing-ephemeral-/tmp execution (exit 127), successful archived replay and
all review/fix evidence are retained; they are not erased by later success.

## Host and claim boundaries

Execution uses existing tools in WSL Ubuntu-24.04, Qt 6.4.2 and offscreen/software
Qt. There is no visible native GUI, physical Linux device, distro-installation,
published 129A package, or live OJ synchronization claim. Native CLI status runs
make no OJ requests. Tests use dedicated temporary data rather than normal user XDG
directories. The preserved build tree is evidence, not a relocatable distribution.

Linux's existing workflow has Linux-path filters for push and pull_request, without
a main-only branch filter. Hosted results must be read from the exact pushed head;
local results are not hosted CI. No source PR is published at this checkpoint.
The integration dependencies remain unmerged. CMake reported missing optional XKB while configuration and build succeeded. Android has known nonfatal compiler,
native-strip and JDK/dependency warnings; BUILD SUCCESSFUL does not mean warning-free.

## Remaining Phase 129

129B still needs explicit read failures and truthful final storage outcomes in
CLI/GUI, including the existing JSON preflight contract. Later slices need
SQLite-consistent backup, all-connection drain, validated restore, a durable
recovery marker, interrupted replacement convergence, permissions and cancellation
faults. An absent path after loss of an established store cannot yet be distinguished
from genuine first use. Read-only WAL access may update sidecars. External namespace
replacement, power loss, native disk-full/ACL behavior and full recovery are not
established by this startup slice.
