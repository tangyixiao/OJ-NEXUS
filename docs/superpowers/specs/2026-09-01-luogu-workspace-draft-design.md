# Luogu Workspace Drafts / 洛谷工作区草稿设计

## Objective / 目标

Make the native Luogu workspace usable across navigation and process restart by
persisting the current editor draft locally. / 让原生洛谷工作区在离开页面和进程
重启后仍可继续编辑，将当前草稿仅保存在本地。

## Scope and boundaries / 范围与边界

- Store one draft per `(judge, pid)` in Room: source code, custom input, selected
  Open Platform language, optimization flag, and update time. / 按 `(评测平台、题号)`
  在 Room 中保存一份草稿：代码、自定义输入、语言、优化开关和更新时间。
- Restore the draft when the workspace opens; if the user edits before a slow read
  completes, the user's edit wins. / 打开工作区时恢复草稿；若慢速读取完成前用户已
  编辑，以用户的新编辑为准。
- Debounce writes by 300 ms and expose LOADING, SAVING, SAVED, and ERROR states so
  the user can tell whether the draft is local and persisted. / 写入防抖 300 毫秒，
  显示加载中、保存中、已保存和错误状态，让用户知道草稿是否已本地保存。
- Room backup/import includes drafts because they are local study data. / 草稿属于
  本地学习数据，随 Room 备份和恢复一起处理。
- Do not add source code or input columns to `submission_jobs`; do not transmit
  drafts, add cloud sync, add main-site credentials, or add background submission. /
  不向 `submission_jobs` 增加代码或输入字段；不上传草稿、不增加云端同步、主站
  凭据或后台提交。

## Architecture / 架构

`WorkspaceDraftEntity` and `WorkspaceDraftDao` form the Room boundary. A small
`WorkspaceDraftRepository` maps the entity to a feature-neutral `WorkspaceDraft`
value and owns timestamps. `WorkspaceViewModel` receives the repository by
constructor injection, restores it once, then schedules latest-state saves after
editor mutations. `WorkspaceScreen` wires the application repository and renders
the localized draft state. / `WorkspaceDraftEntity` 与 `WorkspaceDraftDao` 构成
Room 边界；`WorkspaceDraftRepository` 映射实体与通用 `WorkspaceDraft` 值并负责
时间戳；`WorkspaceViewModel` 构造注入仓库，启动时恢复一次，编辑变更后保存最新状态；
`WorkspaceScreen` 接入应用仓库并显示本地化草稿状态。

The database moves from schema 9 to 10 with a non-destructive table creation.
The composite key prevents one problem's draft from overwriting another judge's
draft. / 数据库从版本 9 升到 10，仅新增表，不做破坏性迁移；复合主键保证不同题目
或不同评测平台的草稿互不覆盖。

## State and error handling / 状态与错误处理

- No repository: `DISABLED` for isolated tests or alternate callers. / 没有仓库时
  为测试和其他调用方显示 `DISABLED`。
- Repository read with no row: `CLEAN`; with a row: restore fields and show `SAVED`.
  / 没有草稿显示 `CLEAN`；有草稿则恢复字段并显示 `SAVED`。
- A write is `SAVING`; successful completion is `SAVED`; read/write failures keep
  the editor usable and show `ERROR`. / 写入时显示 `SAVING`，成功后显示 `SAVED`；
  读写失败不阻塞编辑，只显示 `ERROR`。
- A failed draft write never blocks an explicit Open Platform submit. / 草稿写入
  失败不能阻塞用户明确发起的 Open Platform 提交。

## Testing and acceptance / 测试与验收

1. Repository tests prove upsert/read isolation and timestamped persistence.
2. Migration tests prove schema 9 data survives and `workspace_drafts` exists.
3. ViewModel tests first fail for restore/save behavior, then prove restoration,
   latest user edit precedence, debounce, and write-error visibility.
4. Run focused tests, then `clean test assembleDebug lintDebug`.
5. Install the fresh APK in the emulator, edit a Luogu workspace, leave it, reopen
   the same problem, and verify code/language plus `DRAFT SAVED` survive.

## Explicit non-goals / 明确不做

No Luogu main-site password, Cookie, Session, CSRF login, cloud account,
cross-device synchronization, local compiler, custom-input runner, or automatic
submission retry is added. Historical phase notes and Releases remain unchanged.
不新增洛谷主站密码、Cookie、Session、CSRF 登录、云端账号、跨设备同步、本地编译器、
自定义输入运行器或提交自动重试；历史阶段说明和 Releases 保持不变。
