# Plan — Library Paging (Phase 104)

Date: 2026-10-18
Spec: docs/superpowers/specs/2026-10-18-library-paging.md

## Steps

1. CREATE app/src/main/java/com/ojnexus/feature/problems/LibraryWindow.kt — pure window math
   (LIBRARY_WINDOW_INITIAL/STEP, windowList, hasMoreLibrary).
2. ProblemsViewModel — visibleCount MutableStateFlow, ProblemsUiState gains visibleCount +
   hasMoreLibrary, window applied after applyFilterSort, summary stays full-list, loadMoreLibrary,
   window reset in every filter/sort mutator.
3. ProblemsScreen — SHOW MORE footer when hasMoreLibrary (strings problems_show_more EN+zh).
4. Tests — LibraryWindowTest (window math, 10k-row pass timing, cap); run existing
   ProblemFilterTest/ProblemLibrarySummaryTest to confirm no regression.
5. Gates: tools/gradlew-local.bat test assembleDebug.
6. Bookkeeping: versionCode 96 → 97 (0.3.97), ROADMAP bilingual section, LONG_RANGE_PLAN mark,
   commit feat:.

## Result

Shipped in this phase. Lessons:
- windowList uses take(); summary intentionally uses the FULL filtered list so LibraryPulse stays
  honest while the LazyColumn only tracks <= 60 rows at rest.
- The training rail keeps building ids from the full filtered list (not the window) so
  BUILD TRAINING keeps covering the whole scope.
- 10k-row filter+sort+window pass measured in low single-digit milliseconds on the host; the
  250ms test guard is a CPU budget, not a scroll-rate claim (no instrumentation on this host).
