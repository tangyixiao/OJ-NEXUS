# Dashboard Command Deck Design

**Date:** 2026-09-02  
**Target phase:** v0.3.49 / Phase 54  
**Status:** Approved direction; implementation pending

## Goal

Turn the existing Dashboard into a useful local-first command deck: show the most actionable
training and synchronization information at a glance, provide direct routes to existing work
surfaces, and make the screen feel more intentional without introducing fake data, new network
capabilities, or a rounded-card-heavy visual language.

## Context

The current Dashboard already observes local training, review, analytics, judge connections, and
contests through `DashboardViewModel`. It renders those sources as a long sequence of sections and
currently exposes only Settings and Contests callbacks to the application shell. The next phase
should build on that real state instead of adding another data source.

The current product constraints remain binding:

- OJ NEXUS is a native Android command center, not a social or AI product.
- The app is dark-first with one NEXUS BLUE accent and telemetry-style English UI, localized in
  `values-zh-rCN`.
- All Dashboard data remains local-first and honest. Empty, offline, queued, active, partial,
  and error states must remain distinguishable.
- No passwords, cookies, sessions, CSRF state, cloud service, cross-device sync, local compiler,
  custom-input runner, background submission, or automatic POST retry is added.
- Feature code uses design tokens and named screen constants; it does not introduce raw colors,
  arbitrary dimensions, or new shape literals.

## Chosen approach

Use a presentation-only Dashboard enhancement over the current repositories and routes.

1. Add a small pure summary model/helper for values the screen can derive from existing state:
   due-review count, connected-judge count, and a next-contest countdown from the current clock
   snapshot.
2. Add four explicit Dashboard actions that navigate to existing destinations: Training,
   Review, Problems, and Submission Center. The shell owns navigation; Dashboard only emits
   semantic callbacks.
3. Recompose the top of the screen into a compact command-deck readout, then retain the existing
   detailed sections below it. The detailed sections remain the source of truth for full lists.
4. Add one restrained motion treatment: numeric readouts and training bars transition when their
   local values change. When `NexusTheme.reduceMotion` is true, values change immediately.

This is preferred over a Training-only rewrite because it improves the first screen for every
user, reuses already-observed data, and can be delivered without schema or API risk. A contest-only
Arena rewrite would have less benefit for users without synchronized contest data.

## Information architecture

The new order is:

1. Existing top bar.
2. **Command readout:** due reviews, solved this week, next contest countdown, connected OJs.
3. **Command deck:** four routes with visible labels and touch semantics.
4. Existing **System Status** section, including the Luogu setup action when applicable.
5. Existing **Week**, **Today**, **Next Review**, **Next Contest**, **Recent Activity**, and
   **Training Load** sections.

The command readout is a compact summary, not a second source of truth. It must not show a metric
when its source is unavailable. For example, no contest means a localized pending label rather
than `00:00`, and no review items means `0` with the existing empty queue message still shown in
the detailed section.

## Data and interfaces

### Dashboard summary

Add a judge-agnostic immutable value object in the Dashboard feature:

```kotlin
data class DashboardSummary(
    val dueReviews: Int,
    val connectedJudges: Int,
    val solvedThisWeek: Int,
    val nextContestRemainingSeconds: Long?,
)
```

Expose it from `DashboardUiState` as a derived field populated by the ViewModel. The count of due
reviews uses the same `dueDayIndex <= todayEpochDay` predicate already used for `nextReview`; the
connected count uses enabled accounts already exposed in `judgeConnections`. The contest value is
`max(0, startTimeSeconds - nowSeconds)` for the earliest contest with a real future start time,
or `null` when there is no such contest.

Keep countdown formatting pure and localized at the UI boundary:

```kotlin
fun formatDashboardCountdown(remainingSeconds: Long?): String
```

It returns a stable pending label for `null`, otherwise a compact days/hours/minutes string using
resource-backed units. It never renders negative values. Tests cover null, zero, under-one-hour,
multi-hour, and multi-day values.

The ViewModel may emit a clock tick while the Dashboard is subscribed so a countdown does not stay
stale. The tick is presentation support only, is cancelled with `viewModelScope`, and must not
start network or WorkManager work. Existing Room flows remain the durable data source.

### Navigation callbacks

Extend `DashboardScreen` and `DashboardContent` with:

```kotlin
onOpenTraining: () -> Unit
onOpenReview: () -> Unit
onOpenProblems: () -> Unit
onOpenSubmissions: () -> Unit
```

Wire these in `NexusApp` to `NexusDestination.TRAINING` for both Training and Review (the current
Training screen is the existing review surface and only its problem-specific callback opens
`NexusRoutes.REVIEW_SESSION`), `NexusDestination.PROBLEMS`, and `NexusRoutes.SUBMISSIONS`. No new
route is invented for this phase.

## Visual design

The signature element is a four-cell **command deck** directly below the compact readout. Each cell
uses a hairline border, a small uppercase label, and a short supporting status. The grid encodes
four concrete actions rather than decorative numbered cards.

```text
[ DUE 07 ] [ AC WEEK 03 ] [ NEXT 1D 04H ] [ OJ 02 ]

[ TRAINING ] [ REVIEW ]
[ PROBLEMS  ] [ SUBMISSIONS ]
```

Use existing tokens only:

- `background` for the screen, `surface` for readout/deck fills, and `borderStrong` for the
  command cells.
- `accent` only for actionable labels, live countdown data, and the selected status treatment.
- `textPrimary`, `textSecondary`, and `textTertiary` for hierarchy.
- Existing `NexusRadius.sm` for cells; no new radius or gradient.
- Existing `NexusTypography.dataLarge` for readout values, `dataSmall` for values/statuses, and
  `sectionLabel` for labels.

The composition should remain legible on narrow phones: four readouts may wrap into two rows, and
the command deck is always two columns. Every cell has a minimum touch target supplied by the
existing size tokens and a content description that states the action.

## Motion and accessibility

- Use `AnimatedContent` or token-backed value animation for changed summary values and an
  `animateDpAsState`-style transition for training bars only when `NexusTheme.reduceMotion` is
  false.
- Do not add looping animation, shimmer, glow, particles, gradients, or decorative parallax.
- Preserve explicit text labels for all status tones; no state may be conveyed by color alone.
- Give every command cell a role and content description. Keep visible keyboard/focus semantics
  through the existing Compose clickable behavior.
- All new strings are added to both English and Simplified Chinese resources.

## Error and empty states

The outer `Loadable` handling remains unchanged. The new summary uses safe zero/null values only
for genuinely empty local sources. A failed outer flow continues to show the existing failure
surface. A queued or failed sync is still rendered using its explicit state in System Status; the
summary's connected count means enabled local accounts, not successful network freshness.

## Testing strategy

Write tests before production changes:

1. Pure summary derivation tests prove due-review filtering, enabled-account counting, future
   contest selection, and non-negative remaining seconds.
2. Countdown formatting tests prove stable localized resource keys are selected for all boundary
   buckets.
3. Navigation tests prove each command-deck action reaches the existing destination and does not
   create duplicate top-level destinations.
4. Existing Dashboard setup tests remain green; the full unit suite and debug compilation cover
   integration with the existing repositories and theme tokens.

## Acceptance criteria

- Dashboard has a compact summary and four working command-deck actions.
- Summary values are derived from current local Room/Flow state and never fabricated.
- A real future contest shows a non-negative countdown; no contest shows the localized pending
  label.
- Existing detailed Dashboard sections and Luogu setup behavior remain available.
- New copy is localized; new UI uses existing design tokens and explicit accessibility semantics.
- Reduced motion removes the new transitions.
- `git diff --check`, targeted unit tests, full `test`, `assembleDebug`, and `lintDebug` pass.
- No database schema, network contract, credential, or submission security boundary changes.
