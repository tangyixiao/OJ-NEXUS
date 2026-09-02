# Phase 63 — Session Debrief Design

## Goal

Turn a finished or cancelled training session into a useful local debrief. The user should be
able to identify solved, attempted-but-unsolved, and untouched problems, see the latest verdict
from the session window, and continue either into an existing review session or into problem
details.

## Scope and boundaries

- Applies only to terminal sessions (`FINISHED` and `CANCELLED`); the live session board from
  Phase 62 remains unchanged.
- Reads the existing `training_session_problems`, `training_sessions`, `problems`, `attempts`,
  and `reviews` tables.
- The session time window remains authoritative: attempts before `started_at` and after
  `finished_at` are excluded. A running session has no end bound.
- `OPEN REVIEW` is shown only when an existing review row is present. The debrief never schedules,
  completes, or deletes a review.
- No schema migration, network request, background work, credential flow, or new session state.
- Empty and missing data remain explicit: an empty attachment list shows a localized empty state;
  a missing problem still follows the existing repository error path.

## Data model and flow

Extend the existing session progress query with two read-only projections:

1. `latest_verdict`: the most recent attempt verdict in the session window, ordered by timestamp
   and attempt id.
2. `in_review`: an `EXISTS` projection over the existing `reviews` table.

Map both values through `SessionProblem`. `latest_verdict` becomes the existing domain `Verdict`
enum via `Verdict.fromRaw`; an absent or unknown value is represented as null or `OTHER` according
to the existing mapper convention. `in_review` defaults to false in positional test fixtures so
existing callers remain source-compatible.

Add a pure presentation helper that classifies each session problem into exactly one lane:

- `SOLVED`: `solved == true`
- `ATTENTION`: not solved and `attempts > 0`
- `PENDING`: no attempts and not solved

The pulse counts lanes from the same list, so visible counts cannot drift from the rows.

## UI design

The terminal session page keeps its existing summary metrics, then renders `SESSION DEBRIEF`:

```
SESSION DEBRIEF                         [ALL]
SOLVED  1       ATTENTION  2       PENDING  1
------------------------------------------------
CODEFORCES 1029E   AC          REVIEW
Tree with Small Distances              OPEN REVIEW
ATTEMPTS 2
------------------------------------------------
CODEFORCES 1980F   WA          ATTENTION
Field Division                         OPEN
ATTEMPTS 1
```

- Filter controls are local screen state: `ALL`, `SOLVED`, `ATTENTION`, `PENDING`.
- Each row uses the existing hairline/divider language, restrained status rail, `NexusTag`, and
  48dp action target. The row action is `OPEN REVIEW` when `inReview`, otherwise `OPEN`.
- Latest verdict is shown as a text tag with the existing verdict tone; color is never the sole
  state signal.
- Filtered transitions use the existing 120–300ms motion token and snap when reduce-motion is
  enabled. No looping, glow, particle, or decorative animation.
- All user-visible copy is added to English and Simplified Chinese resources.
- Active sessions continue using `SESSION PULSE` and `PROBLEM QUEUE`; the debrief is not duplicated
  into the live view.

## Navigation and error behavior

- `OPEN REVIEW` navigates to the existing `review/{problemId}` route.
- `OPEN` navigates to the existing local `problem/{problemId}` route.
- Room/Flow errors stay in the existing terminal session error state. No fabricated debrief rows
  are shown.

## Testing and acceptance

- Repository test proves latest verdict and review presence are restricted to the session window
  and that post-finish attempts do not alter terminal debrief data.
- Pure helper tests prove the three lanes and pulse counts, including empty input.
- Route/navigation tests prove both action targets use existing routes.
- Full `test`, `assembleDebug`, and `lintDebug` pass.
- Installed APK is manually exercised on the emulator through a real local terminal session; the
  debrief filters, review/details actions, and crash buffer are checked.
