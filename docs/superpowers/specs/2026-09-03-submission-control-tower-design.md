# Phase 58 — Submission Control Tower Design

## Goal

Turn the existing Submission Center into a local status command surface that makes pending and
failed requests easy to find while preserving every existing request action and navigation path.

## Current context

SubmissionCenterViewModel already exposes a lifecycle-safe Loadable<SubmissionCenterUiState>.
The state contains the most recent local SubmissionJobEntity rows, in-flight result checks,
queued background checks, and an action error. SubmissionCenterScreen already renders loading,
database-error, empty, action-error, request metadata, result details, retry/check actions, and
the existing problem workspace route.

The missing layer is a compact status summary and a temporary status filter. The screen currently
renders every recent request in one long section, so the user cannot quickly isolate pending or
failed work.

## User flow

1. The user opens Submission Center and sees SUBMISSION PULSE above the recent request list.
2. The pulse shows TOTAL, PENDING, READY, and FAILED counts derived from the current local jobs.
3. The user can choose ALL, PENDING, READY, or FAILED; only visible rows change.
4. When a non-ALL filter is active, CLEAR FILTER resets the local view to ALL without changing
   stored jobs or ViewModel action state.
5. Existing CHECK RESULT, QUEUE CHECK, QUEUE RETRY, and OPEN actions continue to behave exactly
   as before.
6. A status filter with no matching rows shows a localized empty-view message while the filters
   remain available.

## Architecture and data

Create a pure SubmissionControlTower.kt module in the submissions feature:

- enum class SubmissionStatusFilter { ALL, PENDING, READY, FAILED }
- data class SubmissionCenterSummary(val total: Int, val pending: Int, val ready: Int, val failed: Int, val other: Int)
- fun summarizeSubmissionCenter(jobs: List<SubmissionJobEntity>): SubmissionCenterSummary
- fun filterSubmissionJobs(jobs: List<SubmissionJobEntity>, filter: SubmissionStatusFilter): List<SubmissionJobEntity>

total includes every local job, including unknown future status strings. pending, ready, and
failed match the existing SubmissionJobStatus.*.name values; other is the remainder.
Filtering returns a new list in the source order: ALL returns all rows, each known status returns
only that status, and unknown status rows appear only in ALL. No Room query, repository method,
network DTO, migration, scheduler behavior, credential behavior, or route changes are needed.

## UI design

Add a SUBMISSION PULSE NexusSection after the action-error block and before recent requests.
Use four weighted NexusMetric values for TOTAL, PENDING, READY, and FAILED. Count changes use the
existing NEXUS motion duration and immediately snap when reduce motion is enabled.

Below the pulse add four full-width weighted NexusTag controls with visible localized labels.
Selected controls use the existing NEXUS accent; each exposes Role.Button, a localized click
label, and a content description. Add a trailing CLEAR FILTER action to the pulse section only
when the selected filter is not ALL.

Render the request list from the filtered list inside one animateContentSize tree using the
existing 200ms motion token. Keep the current request card metadata and action tags. Keep the
global empty message for zero local jobs; use a separate localized NO REQUESTS IN THIS VIEW
message only when a non-ALL filter has no matches. Do not remove or disable actions based on the
filter.

## Error and accessibility behavior

Loading and Loadable.Failed remain unchanged. Action errors remain visible above the pulse.
Unknown status values remain visible in ALL and use the existing UNKNOWN label/tone. Counts must
not be color-only: every metric has a text label and every row keeps its visible status tag.
The back action, clear action, filters, and existing request actions retain button semantics.
No fake request, fabricated status, automatic retry, or bulk side effect is introduced.

## Testing strategy

Add pure unit tests for all status counts, unknown-status accounting, empty input, each filter,
source-order preservation, and source-list immutability. Run the existing submission navigation
tests and the full unit suite. Then run assembleDebug and lintDebug serially.

Install the debug APK on the available emulator and inspect the pulse, status filters, clear
action, filtered empty state, existing action tags, and workspace navigation. Verify that no
FATAL EXCEPTION or Process: com.ojnexus appears in the interaction log.

## Scope boundary and release identity

This phase changes local presentation and local list filtering only. It does not add credentials,
passwords, cookies, sessions, network fields, database schema changes, cloud sync, compiler
execution, custom-input behavior, background submission, bulk retry, or automatic submission.
The phase release identity is versionName=0.3.54 and versionCode=54.
