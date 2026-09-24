# Luogu Public Catalog Action / 洛谷公开题库操作设计

## Context / 背景

Phase 24 imports Luogu's official problemset dump, but the only production trigger is the
account-bound background sync action in Settings. A first-time user who only wants the public
problem catalog can therefore open Problems and find an empty local catalog without an action
that starts the import. / 第 24 阶段已经实现洛谷官方题库导入，但生产入口只有设置页中绑定公开
用户名后的后台同步。只想使用公开题库的新用户进入题库页时，可能看到空目录，却没有直接开始
导入的操作。

## Goal / 目标

Add an explicit, foreground `SYNC CATALOG` action for Luogu in the remote Problems scope. The
action may run without an account because it imports only the public catalog export. It must keep
Room as the read source, preserve existing rows on failure, and expose a localized progress/result
state. / 在远端题库范围为洛谷增加前台显式“同步题库”操作。该操作不要求账号，因为它只导入
公开题库导出；仍以 Room 作为读取源，失败时保留已有行，并展示本地化的进行中/结果状态。

## Non-goals / 不包含

- No Luogu main-site password, cookie, session, CSRF, WebView, or scraping. / 不新增洛谷主站密码、
  Cookie、Session、CSRF、WebView 或抓取。
- No cloud account or cross-device sync. / 不新增云端账号或跨设备同步。
- No automatic background work from this button. It is an explicit foreground action only. /
  该按钮只触发前台显式操作，不新增后台任务。
- No schema change; existing `sync_states` and `remote_problems` tables are sufficient. /
  不改数据库版本，复用现有 `sync_states` 和 `remote_problems` 表。

## Design / 设计

### Contract / 契约

`LuoguPublicCatalogSync` exposes `sync(force: Boolean): StageOutcome`. `LuoguSyncRepository`
implements it by sharing the existing official-dump/paged-fallback catalog writer. The public
entry has no `JudgeAccountEntity` parameter. / `LuoguPublicCatalogSync` 暴露
`sync(force: Boolean): StageOutcome`；`LuoguSyncRepository` 复用现有官方导出/分页回退写入逻辑，
但公开入口不接收 `JudgeAccountEntity`。

### State and persistence / 状态与持久化

The repository records `PROBLEMS` as the current stage in the existing judge sync row. A public
run preserves an existing `accountId` when present, so it cannot detach account-bound sync state.
The ViewModel adds `catalogSyncing`, `catalogSyncItems`, and `catalogSyncError`; rows remain
visible while a refresh is running. / Repository 在现有评测平台同步行中记录当前阶段为 `PROBLEMS`。
公开同步在已有账号时保留 `accountId`，不会破坏账号同步关联。ViewModel 增加
`catalogSyncing`、`catalogSyncItems` 和 `catalogSyncError`；刷新期间仍保留已有题目行。

### UI behavior / UI 行为

- The action is shown only when the selected remote judge is Luogu. / 仅在远端评测平台选择洛谷时显示。
- Tapping it disables duplicate starts, clears the previous notice, and starts the public sync. /
  点击后禁止重复启动，清除旧提示并开始公开同步。
- Success displays the imported count and reloads the current first page from Room. /
  成功显示导入数量，并从 Room 重新加载当前首屏。
- Failure displays the existing error text and keeps any rows already persisted by bounded batches. /
  失败显示现有错误文本，并保留有界批次已经写入的题目。
- All added copy has English and `values-zh-rCN` resources. / 所有新增文案同时加入英文和简体中文资源。

## Acceptance / 验收

1. A public catalog sync succeeds without a connected account and persists catalog rows. /
   没有连接账号时公开题库同步成功并持久化题目。
2. A fresh non-forced run skips network work within the existing freshness window. /
   在现有新鲜度窗口内，非强制同步不会重复访问网络。
3. A partial/failing run reports failure and preserves rows written before the failure. /
   部分失败会报告失败，并保留失败前已经写入的题目。
4. The Problems ViewModel suppresses duplicate starts and exposes success/failure state. /
   Problems ViewModel 禁止重复启动，并暴露成功/失败状态。
5. Full tests, assembleDebug, lintDebug, emulator launch, push, and a public Release pass. /
   全量测试、assembleDebug、lintDebug、模拟器启动、推送和公开 Release 均通过。
