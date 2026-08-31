# Performance Audit

The current UI keeps potentially long collections bounded or lazy:

- The local and remote problem catalogs use `LazyColumn`.
- Training candidates are capped by the Room query and repository default at 20 rows.
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
