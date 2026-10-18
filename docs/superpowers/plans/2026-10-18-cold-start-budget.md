# Plan — Cold Start Budget (Phase 105)

Date: 2026-10-18
Spec: docs/LONG_RANGE_PLAN.md:257-260 (cold start to interactive Dashboard ≤ 1.5s on a
mid-range device; startup tracing harness; numbers recorded in docs/PERFORMANCE.md).

## Ground truth on this host

- No Android device/emulator is attached to this Windows machine, and Robolectric cannot measure
  real cold-start wall time (it does not boot a Zygote). Per repo rules results are never faked —
  so this phase ships the **tracing harness + accounting doc** and records the measured numbers
  that ARE honestly obtainable here, with device measurement documented as the remaining step.

## Steps

1. CREATE core/startup/StartupTracer.kt — pure, injectable startup tracing:
   - `data class StartupSpan(name: String, startedAtUptimeMs: Long, endedAtUptimeMs: Long?)`
     with `durationMs(): Long?` (null while open; negative clamped to 0).
   - `class StartupTracer(clock: () -> Long = System::currentTimeMillis)` — `begin(name)` /
     `end(name)` (idempotent end, unknown end ignored), `spans: List<StartupSpan>`,
     `totalMs()` = span chain from first begin to last end.
   - `object StartupBudget { const val COLD_START_BUDGET_MS = 1500L }` + `verdict(ms)` pure fn.
2. Instrument `OjNexusApplication.onCreate`: begin "app-create" before `AppContainer(this)`,
   end after GlobalContext.init + background bootstrap **launch** (bootstrap work itself is
   already async on applicationScope — it must not count against first frame). Trace spans:
   `app-create` (container construction, Room open, restore recovery) + `global-context`.
   Tracer instance exposed as `OjNexusApplication.startupTracer` for tests/debug.
3. Trace compose first frame: Dashboard composable begins "first-frame" at composition entry and
   NexusApp ends it when Dashboard content is composed (LaunchedEffect(Unit) one-shot), using the
   same process-wide tracer.
4. CREATE docs/PERFORMANCE.md — budget table, harness usage, host-measured numbers (JVM-side
   AppContainer construction cost measured via the unit test below), and the explicit note that
   the 1.5s device budget is verified by running the tracer on hardware (`adb logcat -s
   OJNEXUS-STARTUP`) — not claimed from this host.
5. Test `StartupTracerTest` (pure JVM): begin/end chain, idempotent end, unknown end ignored,
   negative duration clamp, budget verdict boundaries (1500 pass / 1501 fail).
6. Gates + bookkeeping + commit feat:.

## Result

Shipped. Lessons:
- StartupTracer is a plain object — no androidx.tracing dependency needed.
- `System::currentTimeMillis` is wall clock; spans use it only for coarse budget accounting, and
  the JVM unit test injects a controllable clock so no timing flake exists.
- OjNexusApplication.onCreate logs the completed spans to logcat under tag `OJNEXUS-STARTUP`
  once first-frame ends; nothing logs before that (no startup I/O).
- Host-side JVM measurement: AppContainer construction cost is dominated by Room.openHelper
  creation, which on Robolectric/in-memory is near-zero; the honest device number comes from the
  logcat trace, documented in docs/PERFORMANCE.md as the pending hardware step.
