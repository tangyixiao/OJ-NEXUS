# OJ NEXUS — Codeforces Integration

Phase 2 uses only Codeforces' anonymous public API at `https://codeforces.com/api/`.
The adapter never asks for or stores a password, cookie, API key, or signed request.

## Adapter boundary

`CodeforcesApi` contains Retrofit DTO responses; `CodeforcesClient` maps API envelopes and
HTTP/network failures to `CodeforcesApiError`; `RetrofitCodeforcesAdapter` is the only
implementation of `CodeforcesAdapter`. DTOs do not cross into core models or Compose.

Supported public calls are `user.info`, `user.rating`, `user.status`, `contest.list`, and
`problemset.problems`. Every call passes through the process-wide `CodeforcesRequestGate` with
a 2.1-second minimum interval and bounded retry for retryable failures.

## Stored data

`JudgeAccountEntity` stores the canonical public handle. Profile and rating history are
normalized into `JudgeProfileEntity` and `RatingChangeEntity`. Submissions are imported into
the existing attempts table with `sourceJudge=CODEFORCES` and the stable remote submission ID;
the raw verdict is retained alongside the unified verdict. Problemset data is cached separately
in `remote_problems`, so a large catalog does not flood the local training library.

Remote submission materialization merges by `ProblemKey`. Existing local title, difficulty and
official URL metadata may be refreshed, while favorite, tags, notes, reviews, failures and
session history remain untouched. A custom non-Codeforces URL wins over the official URL.

## UI behavior

Settings verifies a public handle first, then starts unique manual and periodic sync work.
Dashboard/Profile/Analytics/Contest Center read cached data and remain usable offline. Problems
can search and filter the cached Codeforces catalog and add a result to the local library;
opening a problem or contest uses Android Custom Tabs rather than a WebView shell.

Unrated users show `UNRATED`, empty histories show `NO RATED CONTESTS`/`NO SUBMISSIONS`, and
partial sync keeps successful modules and imported pages available.
