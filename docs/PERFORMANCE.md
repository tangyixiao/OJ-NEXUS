# Performance Audit

The current UI keeps potentially long collections bounded or lazy:

- The local and remote problem catalogs use `LazyColumn`.
- Training candidates use explicit Room bucket caps, a bounded 60-row merge policy, and a
  repository screen default of 20 rows.
- Contest and detail pages use bounded local snapshots; session and form pages only scroll their
  finite editor/state sections.
- ViewModels expose Room and preference flows with `WhileSubscribed(5_000)`, so screen-scoped
  work stops after the lifecycle subscription timeout.
- Navigation transition objects are remembered and rebuilt only when the reduce-motion preference
  changes.

The Room test suite includes a 40-row candidate query assertion to prevent an unbounded training
feed regression. This is a static and deterministic audit; device-specific frame timing still
requires an emulator or physical device. A Pixel_9 emulator has been used for startup and
navigation smoke checks; full frame-timing profiling remains a separate task. / 这是静态且确定性
的审计；设备帧耗时仍需通过模拟器或真机测量。本工作区已使用 Pixel_9 模拟器完成启动和导航
冒烟检查；完整帧耗时分析仍是单独任务。

## Training candidate pool — Phase 75

The JVM regression test uses a deterministic in-memory source set of 600 candidate rows. It
duplicates that source across the due, recent-unsolved, failure-heavy, and knowledge-linked
buckets, warms up the merge ten times, then measures 100 merges into the bounded 60-row pool.

- Baseline environment: Windows host `TANGYIXIAO`, Android Studio JBR configured by
  `tools/gradlew-local.bat`, local `testDebugUnitTest` JVM.
- Measured baseline: 9ms for 100 merges (2026-09-07).
- Regression threshold: under 500ms for the same workload, leaving CI and JVM warm-up headroom.
- Test: `TrainingCandidatePoolPerformanceTest`.

This benchmark isolates deterministic pool merge/dedup work; Room query correctness is covered by
`OjNexusDatabaseTest` and the repository pool tests.
