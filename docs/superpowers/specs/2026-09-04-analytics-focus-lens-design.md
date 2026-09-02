# Phase 59 — Analytics Focus Lens Design

## Goal

Make Analytics immediately actionable by adding a local activity pulse and explicit 14D, 30D, and
90D lenses for the activity trend and training-time charts.

## Current context

AnalyticsViewModel already observes 365 days of zero-filled local DayActivity values, all-time
totals, verdict/difficulty distributions, streaks, judge breakdowns, tag performance, and rating
histories. AnalyticsScreen renders a long scrollable report with heatmap, rating charts, totals,
a fixed 14-day solve trend, verdict/difficulty bars, performance, judge breakdown, and training
time.

The missing layer is a compact current-period readout and user control over the activity window.
The existing 365-day list is already sufficient; no repository query or database change is needed.

## User flow

1. The user opens Analytics and sees ANALYTICS PULSE before the detailed report.
2. The pulse shows SOLVED, ATTEMPTS, ACTIVE DAYS, and TRAINING for the selected local window.
3. The user chooses 14D, 30D, or 90D. The pulse, solve trend, and training-time chart update
   from the selected suffix of the existing 365-day snapshot.
4. The heatmap remains the full existing 365-day context, while all-time totals, distributions,
   rating charts, and judge breakdown remain unchanged and clearly separate from the activity lens.
5. Reduced motion snaps count and layout changes immediately; normal motion uses the existing
   200ms design-system transition.

## Architecture and data

Create a pure AnalyticsFocusLens.kt module in the analytics feature:

- enum class AnalyticsWindow { DAYS_14, DAYS_30, DAYS_90 }
- data class AnalyticsWindowSummary(val solved: Int, val attempts: Int, val activeDays: Int, val trainingMs: Long)
- fun analyticsWindowDays(days: List<DayActivity>, window: AnalyticsWindow): List<DayActivity>
- fun summarizeAnalyticsWindow(days: List<DayActivity>): AnalyticsWindowSummary

analyticsWindowDays returns a new list containing the last N entries for the selected window,
or every available entry when the source is shorter than N. The source order is preserved.
summarizeAnalyticsWindow sums solved, attempts, and trainingMs and counts active days through
the existing ActivityPolicy.isActive definition. Empty input returns all-zero fields.

AnalyticsViewModel, Room queries, network sync, chart data contracts, and navigation remain
unchanged. AnalyticsContent derives the selected window from state.heatmapDays and passes the
selected list to TrendSection and TrainingTimeSection.

## UI design

Add an ANALYTICS PULSE NexusSection before HeatmapSection. Render four weighted NexusMetric
values for SOLVED, ATTEMPTS, ACTIVE DAYS, and TRAINING. Format counts with formatCount and
duration with the existing formatDuration(summary.trainingMs / 60_000).

Add three weighted NexusTag controls labeled 14D, 30D, and 90D below the pulse. Selected state
uses the existing accent container and visible label; each control exposes Role.Button, a
localized click label, and a content description.

Animate pulse integer changes with animateIntAsState using NexusMotion.DURATION_NORMAL and snap()
when reduce motion is enabled. Wrap the activity chart area in animateContentSize with the same
motion policy. Keep the 365-day heatmap, all-time sections, rating history selection, and empty
state behavior intact.

## Empty, error, and accessibility behavior

Loading and Loadable.Failed remain unchanged. The existing Analytics empty screen remains the
global empty state. If analytics has only non-activity data, the pulse shows zero values while
the existing available sections remain visible. Every pulse metric has a visible text label;
window controls and the back/top-level navigation retain button semantics. No fake activity,
rating, training time, or period data is created.

## Testing strategy

Add pure unit tests for 14D/30D/90D suffix selection, source-order preservation, shorter and
empty inputs, summary sums, ActivityPolicy active-day counting, and training duration totals.
Run the full unit suite, assembleDebug, and lintDebug serially. Install the APK on the available
emulator and inspect the pulse, all three window controls, chart transition, empty behavior, and
absence of app fatal exceptions.

## Scope boundary and release identity

This phase changes local analytics presentation and selection only. It adds no credentials,
passwords, cookies, sessions, network fields, database migration, cloud sync, compiler behavior,
submission behavior, or new data acquisition. The phase release identity is versionName=0.3.55
and versionCode=55.
