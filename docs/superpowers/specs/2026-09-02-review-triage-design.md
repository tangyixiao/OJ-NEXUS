# Review Triage Design

**Date:** 2026-09-02
**Target phase:** v0.3.50 / Phase 55
**Status:** Approved direction; implementation pending

## Goal

Make the Training screen's review queue actionable at a glance. Users should see how much work
is overdue, due today, or scheduled later; switch the visible queue locally; and start the next
real review without hunting through the list.

## Context

Training already observes a real Room-backed review queue and buckets it into `overdue`, `dueToday`,
and `upcoming`. The existing screen renders all groups in a static sequence. It already supports
opening a specific `ReviewSessionScreen` through `onOpenReview(problemId)`, so this phase can add
triage controls without changing persistence or navigation.

## Chosen approach

Add a small pure review-triage module and a presentation layer over the existing `ReviewBuckets`:

1. Derive `ReviewQueueSummary` and filtered buckets from existing lists.
2. Add a compact `TRAINING PULSE` section above the queue with three counts and a `START NEXT`
   action.
3. Add three local filter controls: `ALL`, `DUE NOW`, and `UPCOMING`. The selected filter is
   screen state only and resets naturally with the screen's saved state lifecycle.
4. Keep rows and all other Training sections intact. Make the row's due accent use the ViewModel's
   `todayEpochDay`, avoiding a second system-clock source.
5. Animate count changes and filter content transitions for 200ms, with immediate changes under
   `reduceMotion`.

This is preferred over a session-only redesign because it closes an existing daily workflow with
the data and callback already present. It is also isolated enough to test without mocks or schema
work.

## Data contracts

Create `TrainingReviewTriage.kt` in the Training feature:

```kotlin
enum class ReviewQueueFilter { ALL, DUE_NOW, UPCOMING }

data class ReviewQueueSummary(
    val overdue: Int,
    val dueToday: Int,
    val upcoming: Int,
    val total: Int,
    val nextDueProblemId: Long?,
)

fun reviewQueueSummary(buckets: ReviewBuckets): ReviewQueueSummary
fun filterReviewBuckets(buckets: ReviewBuckets, filter: ReviewQueueFilter): ReviewBuckets
```

`nextDueProblemId` is the problem ID of the earliest item in `overdue + dueToday`, ordered by
`dueDayIndex`, then `dueAt`, then `problemId`; it is null when no review is due now. Filter
behavior is exact: `ALL` returns all three lists, `DUE_NOW` returns overdue and dueToday with
upcoming empty, and `UPCOMING` returns only upcoming.

The ViewModel state shape does not need a new persisted field. The composable derives the summary
from `uiState.reviews`; the review queue itself remains the single source of truth.

## UI and interaction

Place the new pulse between the active Session section and Review Queue:

```text
SESSION
  ...

TRAINING PULSE                         START NEXT >
  OVERDUE  02       TODAY  03       LATER  08

REVIEW QUEUE                           05
  [ ALL ] [ DUE NOW ] [ UPCOMING ]
  OVERDUE · 2
    LUOGU  P1001 ...                         DUE 09-02
  TODAY · 3
    CF     1234 ...                          DUE 09-02
```

- `START NEXT` calls `onOpenReview(nextDueProblemId)` only when that ID exists. When nothing is
  due it remains visibly disabled and uses a localized “NOTHING DUE” label.
- The count in Review Queue reflects the current triage filter; the pulse always shows all three
  buckets so filtering never hides the overall workload.
- Filter controls are bordered, compact, and role-aware. The active choice uses `NexusTone.Accent`
  and text, while inactive choices remain readable with explicit labels.
- Filtered content uses `AnimatedContent` or equivalent 200ms size/fade transition. No looping
  animation, gradient, glow, emoji, or extra dependency is added.
- The layout uses existing NEXUS BLUE tokens, `NexusRadius.sm`, typography tokens, spacing tokens,
  and named Training screen dimensions. No raw colors or arbitrary inline dimensions are added.

## Error, empty, and accessibility behavior

The existing outer `Loadable.Loading`, `Loadable.Failed`, and `Loadable.Ready` handling remains
unchanged. An empty queue shows the existing empty message in all filters; the pulse displays zero
counts and a disabled start action. The `START NEXT` control and each filter expose localized
content descriptions and `Role.Button` semantics. State is conveyed by both selected text and
accent styling, never by color alone.

## Testing strategy

Write tests before implementation:

1. Summary tests cover all counts, total, earliest due item tie-breaking, and null when there is
   no due review.
2. Filter tests cover `ALL`, `DUE_NOW`, and `UPCOMING` without mutating the source buckets.
3. Keep existing Training and review-session tests green; the final Gradle test/build/Lint gate
   covers the Compose integration and localized resource compilation.

## Acceptance criteria

- Training shows a three-bucket pulse and a real `START NEXT` action.
- `ALL`, `DUE NOW`, and `UPCOMING` filters change only local presentation and show correct rows.
- No-due state has a disabled start action and no fabricated problem ID.
- Existing session, recommendations, knowledge, tasks, and history sections remain available.
- Due highlighting uses the ViewModel's calendar snapshot.
- New strings are localized in English and Simplified Chinese; controls are accessible.
- Motion respects `reduceMotion` and uses only short, meaningful transitions.
- No database, network, credential, or submission-security boundary changes.
- `git diff --check`, focused tests, full `test`, `assembleDebug`, and `lintDebug` pass.
