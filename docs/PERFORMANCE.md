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
requires an emulator or physical device. No Android device is connected in the current workspace.
