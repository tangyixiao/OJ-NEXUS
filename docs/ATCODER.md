# OJ NEXUS — AtCoder Integration

AtCoder support is an isolated Phase 3 adapter. The app does not use AtCoder passwords,
cookies, sessions, HTML scraping, or an unofficial login flow.

## Source boundary

The adapter uses HTTPS AtCoder Problems resources:

- `resources/contests.json`
- `resources/merged-problems.json`
- `resources/problem-models.json`
- `atcoder-api/v3/user/submissions?user=&from_second=`

The source is community-maintained, so the adapter reports `COMMUNITY` reliability and keeps
source failures separate from cached data. It exposes account binding, submissions, problem
catalog, estimated difficulty, contests, background sync, and incremental sync. Profile,
rating, and rating history are intentionally unavailable; the UI never renders a missing
rating as zero.

## Account binding

Handles are trimmed and validated as 1–20 ASCII letters, digits, or underscores. The entered
case is preserved. A matching submission verifies the handle; an empty result or unavailable
source connects the valid handle as `UNVERIFIED` instead of claiming that the user does not
exist.

## Sync and mapping

Submission IDs are stable external IDs and problem IDs are stable problem keys. Verdicts map
AC/WA/TLE/MLE/RE/CE to the unified verdict enum; unknown raw results map to `OTHER` while the
raw result remains available. A local problem is materialized only when a submission is
observed or the user explicitly adds it from the cached catalog.

The submission endpoint is timestamp-paginated with a maximum of 500 rows. Initial sync starts
at zero; incremental sync starts at `max(0, latest_timestamp - 120)`. A full page advances to
the maximum timestamp itself. Repeated boundary rows are idempotent, and repeated full-page
signatures without a new ID stop as a typed partial `PAGINATION_STALLED` result. Cursor
advancement is committed with the page rows in one transaction.

AtCoder Problems difficulty is stored as `ESTIMATED` and is not normalized with Codeforces
ratings. Missing, invalid, AHC, and Marathon models remain `UNKNOWN`. Historical remote rows
are retained if a refresh fails; user-owned notes, tags, failures, reviews, and training data
are never overwritten by remote sync.
