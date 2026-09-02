# Phase 56 — Problem Library 2.0 Design

## Goal

Make the local problem library easier to scan and recover from while preserving its existing
search, status, judge, favorite, tag, sort, delete, add, remote-catalog, and detail-navigation
behavior.

## Current context

`ProblemsViewModel` already combines the complete local library, tags, `ProblemFilter`, and
`ProblemSort` into `ProblemsUiState`. `ProblemsScreen` already exposes the filters in two
horizontal chip rows and renders the filtered list through `ProblemRow`. The missing layer is a
compact summary of the library and a clear, visible way to return from an active filter state.

## User flow

1. The user opens the Library scope and immediately sees the current library pulse.
2. The pulse reports the complete local library totals: total problems, solved problems, active
   reviews, and favorites. A separate visible count reports how many rows match the current
   filter.
3. The user searches or chooses any existing filter. The visible count updates and a localized
   `CLEAR FILTERS` action appears only while `ProblemFilter.isDefault` is false.
4. Tapping `CLEAR FILTERS` calls the existing ViewModel reset path and returns the list to the
   default updated-order view without touching stored problems.
5. The list keeps opening native problem details, toggling favorites, and deleting through the
   existing callbacks. The remote catalog remains unchanged.

## UI design

The Library scope keeps the dark-first NEXUS telemetry language:

- Add a `LIBRARY PULSE` section directly below the scope switcher and search field.
- Use four named `NexusMetric` readouts: `TOTAL`, `VISIBLE`, `SOLVED`, and `REVIEW`.
- Place a small `CLEAR FILTERS` action in the pulse trailing area when a filter is active. It is
  a real button with a localized content description, not a color-only indicator.
- Keep the existing filter chips and tag row below the pulse, but give the results header the
  filtered count so the user can see the effect of a selection.
- Preserve the existing flat rows and separators. Add a narrow status rail to each row using the
  existing status tone, and add explicit accessibility text to the favorite and delete targets.
- Animate changing metric values and result-list height over 200ms. With reduce motion enabled,
  values and layout changes update immediately.
- Use `NexusTheme`, `NexusSpacing`, `NexusRadius`, `NexusSize`, and named screen dimensions;
  feature code adds no raw colors or inline arbitrary dimensions.

## Data and architecture

Add a pure `ProblemLibrarySummary` value and `summarizeProblemLibrary` function in the problems
feature. The function receives the complete local `List<Problem>` and the filtered visible count,
then returns total, visible, solved, review, and favorite counts. It derives status from
`Problem.status`, so a problem in review is counted as review even when it is also solved.

Extend `ProblemsUiState` with the summary. The existing repository Flow remains the sole source
of truth; the ViewModel computes the summary in the same `combine` block before emitting state.
No Room schema, repository interface, network DTO, or navigation route changes are needed.

## Empty, error, and accessibility behavior

- Loading and failure states remain exactly available through `Loadable`.
- An empty library still shows `EMPTY LIBRARY` and its existing hint.
- A non-empty library with zero filtered rows still shows `NO PROBLEMS MATCH THE FILTER` and the
  clear action remains available.
- Metric labels and filter actions are visible text and have localized content descriptions.
- Favorite and delete controls keep independent touch targets and do not rely on shape or color
  alone to convey state.
- No network operation is started by the pulse or by clearing filters.

## Testing strategy

- Add unit tests for summary counts, derived review status, visible count, and empty input.
- Keep the existing `ProblemFilterTest` suite as the regression coverage for all filter and sort
  combinations.
- Run the localization resource test after adding all English and Simplified Chinese strings.
- Run full unit tests, `assembleDebug`, and `lintDebug` serially.
- Install the generated APK on the available emulator and inspect the Library scope, active
  filter/reset interaction, row layout, and native detail navigation; confirm no app fatal crash.

## Scope boundary and release identity

This phase is local presentation and state-reset behavior only. It adds no credentials, passwords,
Cookie, Session, CSRF state, network fields, database migration, cloud service, cross-device sync,
local compiler, custom-input runner, background submission, or automatic submission retry.

The phase release identity is `versionName=0.3.52` and `versionCode=52`.
