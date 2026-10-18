# Memory Audit — Plan

Date: 2026-10-18
Phase: 108 (docs/LONG_RANGE_PLAN.md:277-280)
Spec: docs/superpowers/specs/2026-10-18-memory-audit.md

## Steps

1. Add LeakCanary debug-only (version catalog + debugImplementation).
2. Static audit: flow-collection lifecycle, bitmap surfaces, long-lived scopes, tickers,
   recomposition keys, global statics.
3. Pin supervisor-isolation + ticker-lifetime invariants with unit tests.
4. Record findings in docs/PERFORMANCE.md with the honest PENDING device-loop row.

## Result

- `gradle/libs.versions.toml`: leakcanary 3.0-alpha-8 + leakcanary-android library entry.
- `app/build.gradle.kts`: `debugImplementation(libs.leakcanary.android)` (assembleDebug verified).
- Static audit (recorded in spec + docs/PERFORMANCE.md): 0 bare `collectAsState()`; single
  transient bitmap (PlayerCardShare 1080×1350, unreachable after compress); Canvas-drawn rating
  chart; SupervisorJob application scope; collector-bound tickers; stable library row keys;
  no GlobalScope/static view references.
- `MemoryAuditInvariantsTest` (4 tests, green): supervisor parentless by construction, sibling
  survives child failure, cancelled scope rejects launches, collector cancel stops producer.
- Full gate: `test assembleDebug` BUILD SUCCESSFUL, 751 tests, 0 failures.
- versionCode 100 / versionName 0.3.100; ROADMAP bilingual section prepended;
  docs/LONG_RANGE_PLAN.md PHASE 108 ✅.

### Lessons

- kotlinx.coroutines extension functions (`launch`, `cancel`, `cancelAndJoin`) need explicit
  imports even when the receiver types are imported — four compile errors on first run.
- `runBlocking` is the honest harness for collector-lifetime assertions in JVM tests
  (`runTest` is not on the test classpath in this project).
