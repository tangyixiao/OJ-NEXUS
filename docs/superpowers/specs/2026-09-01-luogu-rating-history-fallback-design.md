# Luogu Rating History Fallback / 洛谷 Rating 历史回退

## Goal / 目标

Make public Luogu rating synchronization retain a user's rating history when the user page
contains `elo` data but the practice page returns an empty `elo` array.

让公开洛谷 Rating 同步在用户主页含有 `elo`、而 practice 页面返回空 `elo` 数组时，仍能保留用户的 Rating 历史。

## Evidence / 证据

The live public transport returned 33 profile `elo` entries for `chen_zhe` and 21 for
`jiangly`; the implementation previously selected practice `elo` whenever it was non-null,
including an empty list. This silently produced zero rating-history rows for affected users.

实测公开接口中，`chen_zhe` 的主页返回 33 条 `elo`，`jiangly` 返回 21 条；旧实现只要 practice 的
`elo` 非 null 就优先选用，即使它为空，因此会静默同步出 0 条 Rating 历史。

## Design / 设计

Keep both existing public requests and choose `practicePage.data.elo` only when it is non-empty;
otherwise choose `profilePage.data.elo`, defaulting to an empty list when both are absent. No
new endpoint, authentication, persistence schema, or UI contract is needed. Existing idempotent
Room upserts remain the storage boundary.

保持现有两个公开请求，仅在 practice 的 `elo` 非空时优先使用它；否则使用 user 主页的 `elo`，两者都缺失时
使用空列表。不增加接口、登录方式、数据库结构或 UI 协议；现有 Room 幂等 upsert 仍是存储边界。

## Verification / 验证

- Regression test: non-empty profile history plus empty practice history yields one persisted row.
- Existing Luogu repository tests remain green.
- Live read-only checks confirm current public response shapes for search, profile, practice,
  contests, and problems.
