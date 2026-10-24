# Phase 128A — Windows guarded storage startup and lifetime

Status: **CODE AND NATIVE GATES PASSED; FINAL BRANCH REVIEW PENDING** (2026-10-24).
This prerequisite slice does not complete Phase 128. No PR, package or release has been
published for this slice at this checkpoint. [Machine-readable evidence summary](evidence/phase-128a-summary.json).

## Scope and source

Integration base `20e3fa99d316eaa181b9b3dab24a1d16cefe2f02` is
`codex/phase-128-windows-base`. Feature branch: `codex/phase-128-storage-safety`.
The base combines the Windows lane with shared Phase 126 contracts; it does not import
Android 127 runtime changes. Tested product source: `85827f276f8ff2f2116c96a75d2fb2e31c461216`.
Documentation and pre-existing untracked TestResults were the only worktree differences
during the final run. Manifests record managed CLI/Core/Desktop DLL hashes, not only apphosts.

The [binding design](../superpowers/specs/2026-10-24-phase-128a-windows-storage-safety.md)
and [plan](../superpowers/plans/2026-10-24-phase-128a-windows-storage-safety.md) define
one Windows startup authority, explicit operation lifetime, full cleanup drain, actual CLI
storage exit 6 and a WPF blocked Retry/Exit shell. No schema, dependency or other-platform
runtime changes are included. Consistent backup and restore remain 128B–D work.

## Original failure and actual CLI evidence

At base `20e3fa9`, the compiled CLI opened an existing zero-byte `ojnexus.db`, returned
exit 0 / `ready`, and changed its length from 0 to 126976 bytes. This was an actual isolated
fixture. Before wiring, three new Program regressions failed with expected 6 / actual 0.

Root independently ran the compiled CLI at `85827f2` with four fresh fault roots. Every run
returned exit 6 and exactly one blocked JSON object:

| Existing state | Code | Preservation |
| --- | --- | --- |
| Zero-byte database | `INTEGRITY_FAILURE` | Length 0 and SHA-256 unchanged |
| Corrupt database | `INTEGRITY_FAILURE` | Length and SHA-256 unchanged |
| Schema version 99 | `UNSUPPORTED_SCHEMA` | Length and SHA-256 unchanged |
| Established marker, missing database | `RECOVERY_SOURCE_MISSING` | Database remained absent |

Archive: `%TEMP%/ojnexus-phase128a-native-cli-7f429cdd7af3468680bf772cf64fb445/manifest.json`.
Core helper blocked exit 20 is a test-host convention and is not the product CLI contract.

## Independent final gates

Root restored the Windows solution, built Release with `--no-restore -warnaserror`, and ran
each test project against the same source. Build exit 0, zero warnings/errors; three unique TRXs:

| Project | Passed | Failed / not executed |
| --- | ---: | ---: |
| Core | 242 | 0 / 0 |
| CLI | 68 | 0 / 0 |
| Desktop | 35 | 0 / 0 |
| Total | 345 | 0 / 0 |

Complete stdout, TRXs and digests are retained under
`%TEMP%/ojnexus-phase128a-root-final-37f6de87e32f457aadd3b57b83283f9f`.
The owned diagnostic-process timeout regression also passed independently: the parent timed
out its own sleeping child, killed and awaited it, and retained both output streams. Earlier
implementer transcripts did not capture all external console output; these root logs do.

An independent Android agent ran `gradlew.bat test assembleDebug --rerun-tasks` with only
process-local Android Studio JBR/SDK settings: `BUILD SUCCESSFUL`, 46 tasks executed,
173 suites / 794 tests, zero failures/errors/skips. Android sources were unchanged.
Nonfatal Kotlin daemon fallback and Gradle handshake diagnostics are retained. APK SHA-256:
`8291BF201E4C181C8853B961CB585F85DC0565D9C3C540B2B2D30BD9C3A5FD40`.
Archive: `%TEMP%/ojnexus-phase128a-android-0274a30b-6e79-4c3f-b677-f4ce9cc9f840`.
This is Android unit/build regression; no new emulator/device claim is made.

## Native Windows observations and automation limits

Root's final native manifest
`%TEMP%/ojnexus-storage-acceptance-d9efa717b325492593f8314f2a9e8942/manifest.json`
passed on the recorded managed assemblies:

- Existing damaged database: WPF displayed `LOCAL STORAGE UNAVAILABLE`, `LOCAL FILES PRESERVED`
  and `INTEGRITY_FAILURE`; its actual Exit closed naturally, preserving database bytes.
- A separate process acquired an actual exclusive lifecycle lease before WPF launched.
  WPF displayed `STORAGE_BUSY`; releasing that host and invoking Retry reached Ready.
- Real CLI status returned 0 / ready while the Ready WPF session remained open; WPF then
  closed naturally. Test-host barriers, PIDs and exits are recorded.
- Dashboard, Connectors, Sync History and Problems UI checks passed at logical `900x560`,
  including save/disable/enable without OJ network calls, history filter and two problem filters.

Root additionally ran a Temp-only adaptation of the existing UI smoke using actual OS screen
capture. `os-ui-confirmed.log` and its four PNGs are retained in the final archive and were
visually inspected. The adaptation explicitly returns to Connectors before that screenshot:
the stock script returns to Dashboard during its connector lifecycle checks before its OS
fallback capture. Stock in-process RenderTargetBitmap images also omitted some navigation/filter
visuals in this run. These image-path limitations must not be confused with absent controls in
the actual window, and distinct file hashes alone do not establish correct page content.

Earlier native runs intermittently returned a Win32 root with an empty UIAutomation tree for
matching live WPF PIDs/HWNDs. A failed healthy-page query coexisted with a rendered Ready
Dashboard (`30ef030167144e2cbd38634ca8e21bd7`); other failed roots are listed in the local
implementation report. They remain retained alongside successful runs. A fresh read-only
investigation copied/hash-verified 754 artifacts and tested three bounded targets with STA/MTA
clients; all exposed WPF trees, so it did not establish the intermittent cause or cover the
entire Busy/Retry batch sequence. Report:
`%TEMP%/ojnexus-uia-investigation-40100f3d17d147c99ba99f58f9eda1ed/report.md`.
The current successful native run proves its observations; it does not prove stable UIA
acquisition across runs or a cache/COM fix. Final branch review must assess this tooling limit.

## Reviews and storage contract

Independent Core review cleared two fix rounds. Independent consumer review cleared two fix
rounds, including batch wire-code preservation, unconditional delayed final-refresh drain,
synchronous close-failure retry and bounded diagnostic children. No scoped P1/P2 remains.
Broad final branch review and publication are still pending at this checkpoint.

Valid current workspaces are inspected without migration writes. Recognized legacy versions
0–6 migrate exclusively and must validate as version 7 before Ready. First use requires a
truly absent database and no established/incomplete evidence. Malformed/unsupported files
are not replaced. The permanent `ojnexus.lifecycle.lock` coordinates cooperating new clients:
normal sessions share ownership, initialization/migration requires exclusive ownership, and
shutdown rejects admission and waits for complete actions, non-cancellable ledger cleanup,
final refreshes and native connections before releasing ownership. Age/PID metadata does not
establish liveness. Public raw factory/store/open bypasses were removed; older noncooperating
binaries remain outside this guarantee.

Ordinary local fixed/removable paths are supported. Network/device/URI paths, alternate
streams, reparse points, controlled-file hard links and distinct case shadows are rejected.
Ordinary case-insensitive aliases are supported. Native case-sensitive mode enablement was
denied with Windows error 5, so that OS mode is not claimed as tested.

## Full Phase 128 boundaries

Core evidence includes separate-process sharing/exclusion, first-use race, stale-lock reuse,
killed helper handle release, held native leases and whole-operation drain. An actual hot
rollback journal from an uncommitted killed writer was preserved with its database. Process
kill does not prove power-loss durability. Caller cancellation plus ledger drain is one
combined scenario; the startup cancellation barrier followed a completed inspection rather
than interrupting an in-flight native SQLite command. Native permission/full-volume faults
were not executed; classifier fixtures prove projection only.

Interrupted first initialization can retain `.initialized.tmp` / `ojnexus.init.db` and block
with `RECOVERY_BLOCKED`; automatic recovery is deferred. Keep retained files intact.

- 128B: coherent SQLite snapshots, checksummed/versioned manifest, inspect/create APIs and
  native backup selection.
- 128C: validated staged install, rollback source, durable journal, persistent generation
  fence, interrupted-initialization recovery and staged legacy migration.
- 128D: actual permission/full-disk/interruption/concurrency faults and native recovery UX.

Any prerelease must identify its reviewed source, tested assets and signature status. An
unsigned self-contained ZIP does not establish trusted signing, clean-VM installation,
upgrade/uninstall or Store acceptance. No merge or achievement badge is inferred from this work.
