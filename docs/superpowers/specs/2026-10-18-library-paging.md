# Library Paging

Date: 2026-10-18
Phase: 104 (docs/LONG_RANGE_PLAN.md:248-250)

## Goal

The problem library stays smooth at 10k+ rows. Compose recomposition cost is proportional to the
number of items the `LazyColumn` tracks, so the fix is manual windowing in the ViewModel: the
library keeps the full filtered+sorted list as the source of truth, but the UI state only ever
exposes a bounded window of rows that grows as the user scrolls.

## Constraints

- No Paging 3 dependency (the repo deliberately uses plain StateFlow + LazyColumn).
- Filtering, sorting, and summary stay pure functions over the full list (unit-tested already).
- No schema change; no network; no credential flow.
- Training and session ViewModels consume `observeLibrary()` internally for candidate selection —
  they are NOT the scroll bottleneck and stay untouched.

## Design

1. `LibraryWindow.kt` (pure): `LIBRARY_WINDOW_STEP = 60`, `LIBRARY_WINDOW_INITIAL = 60`;
   `fun <T> windowList(list: List<T>, count: Int): List<T> = list.take(count.coerceAtLeast(0))`.
   `visibleCount` is UI state owned by the ViewModel (reset to the initial window whenever the
   filter or sort changes, grown by one step from `loadMoreLibrary()`).
2. `ProblemsViewModel`:
   - `state` combine gains a `visibleCount: MutableStateFlow<Int>`; `ProblemsUiState` gains
     `visibleCount: Int` and `hasMoreLibrary: Boolean` (visible < filtered size).
   - The filtered list is computed once; `problems = windowList(visibleProblems, visibleCount)`;
     the summary still uses the FULL filtered list (counts stay honest).
   - `loadMoreLibrary()` grows the window; `setQuery/setStatus/setJudge/setTag/toggleFavoriteOnly/
     setDifficultyRange/clearFilter/cycleSort` reset the window to the initial size.
   - The training rail keeps using the full filtered list (build-training over the whole scope,
     not just the visible window) — `ProblemsUiState` carries `allFilteredProblemIds` implicitly
     through `buildTrainingProblemIds(visibleProblems)` computed from the full list.
3. `ProblemsScreen` LibraryContent: after the last row, when `uiState.hasMoreLibrary`, render a
   "SHOW MORE (N MORE)" footer item that calls `viewModel.loadMoreLibrary()`.
4. Benchmark test (DoD): a Robolectric-free pure test seeds 10_000 synthetic problems, applies
   `applyFilterSort`, and asserts the windowed list holds at most 60 items while summary counts
   reference all 10_000; plus a compose-runtime-free latency guard measuring that the
   filter+sort+window pass over 10k rows completes in milliseconds, not frames.

## Verification

- New `LibraryWindowTest`: window math (initial, grown, reset), 10k-row pass timing (asserts
  < 250ms — a full-frame budget, honest since it is a plain CPU pass, not a scroll test) and
  window cap correctness.
- Existing ProblemsViewModel/Screen behavior unchanged for small libraries (window >= list size
  renders everything; footer hidden).
