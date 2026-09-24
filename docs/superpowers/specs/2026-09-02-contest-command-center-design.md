# Phase 57 — Contest Command Center Design

## Goal

Turn the existing contest list into a local command surface that makes live contests and the
next upcoming contest immediately actionable without changing contest data acquisition.

## Current context

`ContestCenterViewModel` already converts synced `ContestEntity` rows into live, upcoming, and
recent `ContestRow` values, computes countdown seconds from an injected clock, and filters by
judge. `ContestCenterScreen` renders those groups and opens the existing contest-focus route.
The missing layer is a compact summary and a way to temporarily isolate one contest phase.

## User flow

1. The user opens Contests and sees `CONTEST PULSE` below the judge filter.
2. The pulse shows live, upcoming, and recent counts plus the next upcoming contest countdown.
3. `OPEN NEXT` opens the existing focus screen for the earliest upcoming contest. It is disabled
   and labeled `NO UPCOMING` when there is no actionable upcoming row.
4. The user can choose `ALL`, `LIVE`, `UPCOMING`, or `RECENT` locally. The selected filter changes
   only the visible groups; judge selection remains owned by the ViewModel.
5. Every contest row still opens the existing focus route, and the countdown continues to update
   from the existing local clock.

## UI design

- Add a `CONTEST PULSE` `NexusSection` with four compact readouts: `LIVE`, `UPCOMING`, `RECENT`,
  and `NEXT`.
- `NEXT` displays the existing formatted countdown for the earliest upcoming row, or the
  localized no-upcoming label. `OPEN NEXT` is a bordered accessible action below the readouts.
- Add four labeled phase filter controls below the pulse. Selected state uses the existing NEXUS
  accent container and visible text; controls remain accessible buttons.
- Render only the selected groups with a single content tree and a 200ms size transition. Reduce
  motion uses immediate size changes.
- Keep dark-first NEXUS colors, thin separators, restrained radii, named dimensions, and the
  existing `NexusTag` phase labels. No gradients, glow, emoji, or looping animation.

## Data and architecture

Add a pure `ContestCenterSummary` and `ContestPhaseFilter` module. The summary receives
`ContestCenterUiState`, counts each existing group, and selects the earliest upcoming row by
`startTimeSeconds` then `judge.ordinal` then `contestId`. Filtering returns a new
`ContestCenterUiState` containing only the selected phase and preserving source lists.

The screen derives the summary and filtered rows from the existing `rows` result. No Room schema,
repository method, network DTO, sync behavior, or navigation route changes are needed.

## Empty, error, and accessibility behavior

- Existing loading and sync-derived empty states remain intact.
- An empty phase filter shows a localized `NO CONTESTS IN THIS VIEW` message while other phase
  data remains available through the filters.
- `OPEN NEXT`, all phase filters, the back action, and contest rows expose meaningful button
  semantics and visible labels.
- No fake contest, countdown, judge, or focus route is created when the upcoming list is empty.

## Testing strategy

- Add pure unit tests for summary counts, earliest-upcoming tie breaking, no-upcoming behavior,
  and phase filters that do not mutate the source state.
- Run localization tests, the full unit test suite, `assembleDebug`, and `lintDebug` serially.
- Install the APK on the available emulator and inspect the pulse, phase filters, disabled/no-next
  state, countdown, and existing focus navigation; verify no app fatal crash.

## Scope boundary and release identity

This phase is local presentation, filtering, and navigation only. It adds no credentials,
passwords, Cookie, Session, CSRF state, network fields, database migration, cloud service,
cross-device sync, local compiler, custom-input runner, background submission, or automatic
submission retry.

The phase release identity is `versionName=0.3.53` and `versionCode=53`.
