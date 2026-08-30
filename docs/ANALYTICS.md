# OJ NEXUS — Analytics

Analytics is local-first. The screen observes Room aggregates and remains usable without a
network. Missing activity renders explicit empty states; a missing judge rating is never
represented as zero.

The activity heatmap covers the rolling local-day window and supports tap-through day detail.
Verdict and solved-difficulty distributions, solve/training trends, streaks, and totals are
computed from SQL aggregates. First-try AC counts the earliest timestamp/ID submission for
each attempted problem.

Tag performance joins normalized problem tags to attempts and reports attempts, AC count, and
AC rate. The UI ranks the lowest-rate tags as weak tags, capped to keep the page readable.
Judge breakdowns group attempts and solved difficulty by `JudgeId`; AtCoder estimated
difficulty remains source-labelled and is not combined with Codeforces ratings.

Knowledge distribution is intentionally deferred until Phase 6 introduces explicit
problem-knowledge relations. Analytics must not infer knowledge areas from free-form tags.
