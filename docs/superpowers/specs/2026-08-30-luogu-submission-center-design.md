# Luogu Local Submission Center

## Goal

Expose the locally persisted Luogu Open Platform jobs as a small, restart-safe
submission center. Users can inspect recent RUN and SUBMIT requests and manually
query pending or failed requests without adding main-site authentication,
background polling, or cloud storage.

## Scope

- Add a dedicated `submissions` navigation route.
- Make the route reachable from Profile and the command palette.
- Read recent `submission_jobs` rows from Room in descending update order.
- Show request kind, problem ID when available, language, request ID, local time,
  and the persisted status/evaluation metadata.
- Allow a foreground query action for PENDING and FAILED jobs through the existing
  `LuoguSubmissionRepository.fetchResult` boundary.
- Allow opening the existing code workspace for a job with a problem ID.
- Provide loading, empty, database-error, and per-request-action error states.
- Keep source code, standard input, OpenApp credentials, cookies, and cloud data
  outside this feature and its database query.

## Out of scope

- Main-site password, Cookie, Session, or CSRF login.
- Automatic/background result polling or automatic submissions.
- Local compiler, code persistence, or cloud/cross-device synchronization.
- Reconstructing anonymous historical Luogu submissions.

## Architecture and data flow

`SubmissionCenterScreen` depends on `SubmissionCenterViewModel`, which depends on
the judge-neutral `LuoguSubmissionCenter` interface. The concrete
`LuoguSubmissionRepository` implements that interface by observing
`SubmissionJobDao.observeRecent` and delegating a manual query to its existing
Open Platform gateway. A successful result updates the Room row in the same
repository path already used by the workspace; terminal PROBLEM results remain
idempotently materialized as local attempts.

The ViewModel combines the Room flow with a set of request IDs currently being
queried. The list remains usable while one request is busy. A query error is
shown for that request action and does not discard cached rows. Room stream
failures use the app's existing `Loadable.Failed` state.

## UI behavior

- The screen uses the existing dark NEXUS design tokens and flat top bar.
- Empty state explains that explicit Open Platform actions will appear here.
- PENDING and FAILED rows expose `CHECK RESULT`; READY rows show status and score
  metadata without making another request automatically.
- A row with a Luogu problem ID exposes `OPEN WORKSPACE`.
- Request IDs are rendered as opaque metadata only; code and input are never
  rendered by this screen.

## Verification

- ViewModel tests cover loading/ready rows, empty rows, manual query delegation,
  duplicate-query suppression, and action-error recovery.
- Repository/DAO coverage verifies recent ordering and the existing persisted
  result update path.
- Resource-key validation covers English and Simplified Chinese strings.
- The final gate remains `clean test assembleDebug lintDebug`.
