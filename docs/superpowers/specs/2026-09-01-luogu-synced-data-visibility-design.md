# Luogu Synced Data Visibility / 洛谷同步数据可见性

## Goal / 目标

Ensure that public Luogu Rating data is visible in the app even when the user has not yet
created local attempts or problems.

确保用户尚未创建本地做题记录时，公开同步得到的洛谷 Rating 数据仍能在应用中显示。

## Evidence / 证据

`AnalyticsUiState.isEmpty` previously considered only local attempts and local problems. A
Rating-only account therefore saw the generic empty state and never reached its per-judge
Rating sections. Profile also used an obsolete Phase 2 message whenever the total rated-contest
count was zero, and only showed the count when a Codeforces account existed.

旧的 `AnalyticsUiState.isEmpty` 只判断本地提交和本地题目；只有 Rating 数据的账号会错误进入通用空态，
无法看到按评测平台展示的 Rating 区域。Profile 还会在 Rated 竞赛数为零时显示过期的 Phase 2 文案，且只有
存在 Codeforces 账号时才显示竞赛数量。

## Design / 设计

Treat any non-empty synchronized Rating history as analytics content. Keep the existing local
activity sections unchanged, so an account with neither local activity nor Rating history still
gets the current empty state. Profile shows the aggregate rated-contest count whenever it is
positive, independent of which judge supplied it; otherwise it shows a neutral no-history label.

将非空的同步 Rating 历史视为 Analytics 内容。保持现有本地活动区域不变，因此没有本地活动且没有 Rating 历史
的账号仍显示原有空态。Profile 在 Rated 竞赛总数大于零时显示数量，不依赖具体评测平台；否则显示中性的“暂无
同步 Rated 竞赛”文案。

No database, network, authentication, or cloud behavior changes. All UI copy remains localized.

不改变数据库、网络、登录或云端行为，所有 UI 文案继续走本地化资源。

## Verification / 验证

- Unit tests cover Rating-only analytics content and the unchanged fully empty state.
- Full project tests, debug build, and lint remain green.
- The final APK is installed on the existing emulator without stopping it.
