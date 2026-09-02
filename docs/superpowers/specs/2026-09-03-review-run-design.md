# Review Run — Design Spec

## Context

OJ NEXUS already has a local review queue, a `START NEXT` shortcut, and a single-problem review
screen. The shortcut currently opens one problem and requires the user to return to the queue
between every outcome. Phase 64 can also populate the queue directly from a finished session.
The next stage should make that queue practical to work through as a focused run.

## Goal

Add a continuous `REVIEW RUN` route launched from Training's `START NEXT` action. At launch it
captures the currently due review IDs in stable due-time/problem-ID order. The user reviews one
problem at a time, records an existing review outcome, sees the scheduler's next interval, and
advances to the next captured item without returning to the queue.

## Scope and invariants

- Only reviews due now or overdue at run start are captured (`dueDayIndex <= todayEpochDay`).
- The captured set is stable for the run; newly due or newly scheduled items do not appear midway.
- Each problem is completed through the existing `ReviewRepository.completeReview` transaction.
- Existing `ReviewSessionScreen` remains available from individual queue rows and debrief rows.
- No database schema, navigation argument containing a problem list, network request, background
  task, credential, or new review result is introduced.
- The run does not mutate a review row until the user explicitly records PASS, HARD, FAIL, or
  SKIP. A failed repository mutation keeps the current problem active and shows an error.

## Architecture

1. Add `ReviewRunViewModel` with `ReviewRepository`, `ProblemRepository`, and `Clock`.
2. On the first queue emission, derive and retain the due ID order. Observe the queue and problem
   details reactively so labels stay local and current while the run is open.
3. Keep screen-local run state: captured IDs, completed IDs, current ID, last completed item, last
   outcome, and mutation error. The repository remains the only writer.
4. Add a no-argument `review-run` route. Training launches it through a new callback; the existing
   single-problem route is unchanged.

## UI behavior

- Header: `REVIEW RUN` and a compact progress rail with `DONE`, `LEFT`, and `TOTAL` values.
- Active state: judge/external ID, title, stage, difficulty, and the four existing outcome actions.
- Result state: explicit verdict tag, next stage/interval, and `NEXT ITEM`; the final item uses
  `CLOSE RUN` and shows `RUN COMPLETE` after advancing.
- Empty state: `NO REVIEWS DUE` plus `CLOSE RUN` when the run opens with no due items.
- Failure state: localized error text with the current item preserved.
- Use 120–300ms content/progress motion only when reduce-motion is disabled; no glow, gradient,
  emoji, or looping animation.

## Verification

- Pure tests cover due filtering, deterministic ordering, stable capture, and progress math.
- ViewModel/repository integration tests cover PASS advancement, final completion, and a failed
  mutation retaining the current item.
- Source-layout tests prove Training launches the run route and the run exposes all outcome
  controls plus the reduced-motion progress treatment.
- Run `test assembleDebug lintDebug`, install on `emulator-5554`, use existing due local reviews,
  record at least two outcomes, verify progress and completion, and check for fatal exceptions.

