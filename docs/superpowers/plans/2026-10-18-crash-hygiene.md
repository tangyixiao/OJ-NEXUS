# Crash & ANR hygiene — Plan

Date: 2026-10-18
Phase: 114 (docs/LONG_RANGE_PLAN.md:305-307)
Spec: docs/superpowers/specs/2026-10-18-crash-hygiene.md

## Steps

1. `core/crash/CrashLog.kt` — local-only capture object (write/newest/crashFiles/clear/prune,
   10-file retention, body carries thread/exception/message/stacktrace + LOCAL ONLY footer).
2. Opt-in preference `crash_log_enabled` (default OFF) in UserPreferencesRepository +
   `setCrashLogEnabled`. Handler installed from `OjNexusApplication.onCreate` only when the
   preference is ON, chaining to the previous default handler. Takes effect next launch
   (documented in the settings hint).
3. Settings data section gains a `CrashLogSection`: toggle, count + newest file line, VIEW LAST
   CRASH dialog (8 KiB display cap with truncation note), CLEAR. Strings EN + zh.
4. Tests: write/prune/newest/clear round trips + a source-level assertion that CrashLog carries
   no network machinery (okhttp/retrofit/HttpURL/java.net.URL/Socket).

## Result

- `CrashLogTest` 5 tests green. `test assembleDebug` BUILD SUCCESSFUL — 756 tests, 0 failures.
- Localization lint re-run after the 9 new keys: LOCALIZATION OK (EN=1019 ZH=1019, 0 hardcoded).
- ANR scope decision: no jank/ANR watchdog added — ANR traces stay a system surface; the honest
  deliverable is the capture pipeline + settings surface. "Fix top findings" applies to real
  files the device produces later (none exist on this host — documented as pending-with-
  infrastructure, same posture as device-loop measurements).

### Lessons

- First `CrashLogTest` run failed 3 tests: two crashes within the same second produced the same
  `crash-yyyyMMdd-HHmmss.txt` name and overwrote each other. Real product defect caught by the
  test — stamp now carries milliseconds (`yyyyMMdd-HHmmss-SSS`), and tests use distinct
  epoch-millisecond inputs instead of `Thread.sleep`.
- `DialogText` requires an explicit `color` parameter (no default) — the crash-preview CLOSE
  button needed `NexusTheme.colors.accent`.
- `androidx.compose.material3.AlertDialog` must be fully qualified in this file (three earlier
  dialogs already use the qualified form; the bare name is not imported).
