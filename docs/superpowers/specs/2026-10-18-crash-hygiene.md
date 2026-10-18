# Crash & ANR hygiene — Spec

Date: 2026-10-18
Phase: 114 (docs/LONG_RANGE_PLAN.md:305-307)

## Goal

Optional opt-in local crash log (no upload) viewable in settings; fix top findings.
Constraints: nothing leaves the device — ever.

## Design

### Capture (all threads, no upload path)

`core/crash/CrashLog.kt` — a single object installed from `OjNexusApplication.onCreate` as the
process-wide `Thread.setDefaultUncaughtExceptionHandler`. Contract:

- Writes one file per crash into `context.filesDir/crash/` (NOT external storage, NOT cache —
  cache can be purged but so what; filesDir is private to the app).
- File name: `crash-yyyyMMdd-HHmmss.txt` (lexical sort = chronological).
- Body: thread name, exception class + message, full stack trace, and the last
  `OJNEXUS-STARTUP` breadcrumb lines (Phase 105 spans) if any were recorded this process.
- **Retention cap**: keeps the newest 10 files, deletes older ones at write time.
- **No upload path exists** — the class contains no network imports at all (asserted by a test
  scanning the source for `Http`/`okhttp`/`retrofit`/`URL(`).
- **Opt-in**: `UserPreferences` gains `crashLogEnabled: Boolean = false` (key `crash_log_enabled`,
  default OFF). The handler is installed only when the preference is ON at process start; the
  settings screen writes the preference and the change takes effect next launch (documented in
  the UI hint). Default handler chaining: after writing the file, the previous default handler
  still runs (system kill flow unchanged).
- ANR hygiene: the same preference gates a `CrashLog.noteAnrWatchdog()` marker written on
  main-thread jank detection? — CUT. No watchdog thread on the main path; ANR traces stay a
  system surface. Scope keeps to crash capture (honest, small, tested).

### Viewing (settings)

Data section, under the maintenance panel: a CRASH LOG row —

- OFF state: "CRASH LOG OFF — ENABLE TO CAPTURE LOCAL CRASHES" + toggle action.
- ON state: count + newest file timestamp; "VIEW LAST CRASH" opens a dialog with the newest
  crash file content in a monospace scrollable column (cap display at 8 KiB, with a note when
  truncated); "CLEAR" deletes all crash files.
- All strings EN + zh; telemetry tone.

### Top findings

No crash reports exist yet (nothing has crashed on this host; no device loop). The honest
deliverable is the capture pipeline + settings surface; "fix top findings" applies to whatever
real files the user's device produces. Documented as PENDING-with-infrastructure, same posture
as device-loop perf numbers.

## Out of scope

- Any upload/sync of crash files (permanent).
- StrictMode penalty death / ANR watchdogs (system surfaces exist).
- Changing the default uncaught-exception behavior when the feature is off.
