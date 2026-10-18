# Memory Audit — Spec

Date: 2026-10-18
Phase: 108 (docs/LONG_RANGE_PLAN.md:277-280)

## Goal

Leak check (LeakCanary in debug only) + bitmap/Compose recomposition pass on dashboard/library.
DoD: zero leaks on the main navigation loop.

## What shipped

### LeakCanary (debug only)

- `gradle/libs.versions.toml`: `leakcanary = "3.0-alpha-8"`, `leakcanary-android` library.
- `app/build.gradle.kts`: `debugImplementation(libs.leakcanary.android)` — release APK carries
  zero LeakCanary classes (verified by assembleDebug resolving the debug-only graph).

### Static audit (host-verifiable; no emulator attached)

1. **Flow collection**: every screen collects through `collectAsStateWithLifecycle()` —
   `0` bare `collectAsState()` call sites in feature/core-ui/app packages. Collection stops on
   ON_STOP, so background screens neither recompose nor hold their producers.
2. **Bitmaps**: exactly two bitmap surfaces. `PlayerCardShare.renderToCache` (1080×1350 ARGB_8888,
   ~5.8 MB) is transient — created, compressed into a cache file, then unreachable; no field or
   cache holds it. `AnalyticsScreen` rating chart draws via Compose `Canvas` (no bitmap at all).
   No `Coil`-held bitmaps exist in the library path (text-only rows).
3. **Application scope**: `applicationScope` = `SupervisorJob() + Dispatchers.IO`. A failed
   bootstrap child cannot cancel siblings (supervisor semantics pinned by
   `MemoryAuditInvariantsTest`); the scope holds only short reconcile jobs that end by
   themselves — nothing loops forever inside it.
4. **Tickers**: session elapsed tickers are `flow { while(true) … }` consumed via
   `collectAsStateWithLifecycle` — producer lifetime is bound to the collector (pinned by test).
5. **Recomposition**: the library renders through the Phase-104 60-row window; rows are keyed by
   stable problem id, so a favorite toggle recomposes one row, not the list. Dashboard reads
   `state` once via one `collectAsStateWithLifecycle` — no per-field derived churn.
6. **No global statics**: no `GlobalScope`, no `object` singletons holding views or contexts;
   `GlobalContext.init(this)` stores the application context (never an Activity).

### Device loop (honest status)

The 20-minute main-navigation loop (Dashboard → Problems → Problem detail → back, ×N) requires a
running emulator/device. None is attached to this host. LeakCanary is now active in every debug
build, so the loop produces a leak report the moment hardware runs. The static audit above plus
the supervisor/ticker invariants are the host-side proof; the device-loop result is recorded as
**PENDING** in docs/PERFORMANCE.md — never invented.

## Out of scope

- Macrobenchmark memory metrics (needs device + benchmark module).
- Changing `PlayerCardShare` bitmap size (one-off, transient, well under a frame budget).
