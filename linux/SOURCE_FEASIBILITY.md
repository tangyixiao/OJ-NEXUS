# Linux source feasibility gate

Probe date: 2026-09-08 (Ubuntu 24.04 / curl, no account credentials or cookies).

| Judge | Source | Observed result | Release state |
| --- | --- | --- | --- |
| Codeforces | `https://codeforces.com/api/user.info?handles=tourist` | HTTP 200 JSON; official API envelope | Ready for smoke |
| AtCoder profile/rating | `https://atcoder.jp/users/tourist` | HTTP 200 HTML; `rank_history` is embedded in the official page | Experimental parser; smoke required |
| AtCoder submissions | `https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=tourist&from_second=0` | HTTP 200 JSON | Community source; smoke required |
| AtCoder contests/problems/difficulty | `https://kenkoooo.com/atcoder/resources/{contests,problems,problem-models}.json` | HTTP 200 JSON | Community source; smoke required |
| AtCoder Problems profile/rated_rank | `https://kenkoooo.com/atcoder/atcoder-api/v3/user/{show,rated_rank}` | HTTP 404 in this probe | Not used; official profile page fallback is explicit |
| Luogu public profile | `https://www.luogu.com.cn/api/user/info/1000` | HTTP 200 JSON; public profile and counters | Ready for profile smoke |
| Luogu rating history | `https://www.luogu.com.cn/api/rating/elo?user=1000&page=1&limit=20` | HTTP 404 JSON in this probe | Release blocked for rating/history |
| Luogu records | `https://www.luogu.com.cn/record/list?user=1000&page=1&_contentOnly=1` | HTTP 302 to `/auth/login` without credentials | Not available in the public-data-only client |
| Luogu contests/problems | `/contest/list?page=1&_contentOnly=1`, `/problem/list?page=1&_contentOnly=1` | HTTP 200 HTML with `lentille-context` JSON payload | Parser implemented; smoke required |

The adapters and fixture tests preserve these boundaries: unsupported or experimental data is
reported as a typed stage failure and never replaced by sample data. Release requires fresh
source probes plus a real public-account smoke for all three judges, and remains blocked until
the Luogu rating-history source is independently verified.

## Fresh live smoke (2026-09-13)

- Codeforces `tourist`: all five stages succeeded (profile 1, rating 307, submissions 1000,
  contests 2146, problemset 11394); exit code 0.
- AtCoder `tourist`: all five stages succeeded (profile 1, rating 247, submissions 500,
  contests 6431, problemset 9520); exit code 0.
- Luogu `uid:2`: profile, contests, and problemset succeeded; anonymous submissions returned
  typed `AUTHENTICATION`, so the aggregate status was partial and exit code 1. The client has no
  credential-import path by design.
