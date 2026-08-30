# Achievements and Player Card

Achievements are derived from local evidence every time Profile state is observed. There is no
remote unlock endpoint and no mutable badge flag that can drift from the underlying history.

Current deterministic milestones:

| Achievement | Evidence |
| --- | --- |
| FIRST BLOOD | at least 1 solved problem |
| TEN SOLVED | at least 10 solved problems |
| IRON WILL | at least 7 active days and a 7-day current streak |
| RED LINE | solved difficulty reaches 1800 |
| CONTESTANT | at least 1 rated contest in the local rating history |

The existing Profile Player Card combines the app identity, public OJ handles/rating, and global
local statistics. Unlocked milestones now appear below the global metrics. Image export remains
the next isolated delivery step so the renderer can be verified against the same tokens without
creating a second source of truth.
