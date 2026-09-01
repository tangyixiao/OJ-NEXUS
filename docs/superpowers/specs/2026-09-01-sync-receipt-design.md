# Phase 37 — Sync Receipt / 同步回执设计

## Goal / 目标

Make the Settings screen answer a practical first-use question: which synchronized
modules are available for this judge, and when was each module last refreshed? / 让设置页直接回答
首次使用时最重要的问题：当前评测平台有哪些同步模块，以及每个模块最后何时更新。

## Context / 背景

The sync pipeline already records independent timestamps in `SyncStateEntity` for profile,
rating, submissions, contests, and problemset data. The current UI mostly exposes only the
judge-level terminal state and one overall successful-sync time. That hides partial module
coverage, especially for Luogu where public profile, Rating, contests, and problemset sync are
real capabilities while private submission history is intentionally unavailable. / 当前同步管线已经
在 `SyncStateEntity` 中分别记录资料、Rating、提交、竞赛和题库时间戳，但设置页主要只显示评测平台级别的
终态和一次总同步时间。这会隐藏模块级覆盖情况，尤其是洛谷：公开资料、Rating、竞赛和题库是真实能力，
私有提交历史则明确不可用。

## User-visible behavior / 用户可见行为

1. Each connected judge panel contains a localized `SYNC COVERAGE / 同步覆盖` section.
   / 每个已连接评测平台面板显示本地化的“同步覆盖”区域。
2. The section lists only modules represented by the adapter's actual capabilities:
   `PROFILE`, `RATING`, `SUBMISSIONS`, `CONTESTS`, and `PROBLEMSET`. / 区域只列出适配器真实声明的能力：
   `PROFILE`、`RATING`、`SUBMISSIONS`、`CONTESTS`、`PROBLEMSET`。
3. A module with no timestamp says `NEVER SYNCED / 从未同步`; a timestamp is rendered as a
   localized relative age (`JUST NOW`, `N MIN AGO`, `N H AGO`, `N D AGO`, with Chinese equivalents).
   / 模块没有时间戳时显示“从未同步”；有时间戳时显示本地化相对时间。
4. Unsupported Luogu private submission history is not shown as a missing failure in this
   section; the existing OpenApp submission workflow remains separate. / 洛谷公开同步区域不把不支持的私有
   提交历史伪装成失败；现有 OpenApp 提交流程保持独立。
5. Existing judge-level state, error mapping, sync actions, language switching, and all previous
   historical documentation remain unchanged. / 既有平台级状态、错误映射、同步按钮、语言切换和历史文档均保持不变。

## Architecture / 架构

Add a small pure settings-domain mapping from `JudgeConnectionUi.capabilities` and
`SyncStateEntity` to immutable `SyncReceiptItem` values. Keep timestamp selection in one helper so
the UI cannot accidentally display a module that the adapter does not support. The Compose panel
renders the returned list using existing `NexusSection`, `Text`, spacing, and typography tokens;
no feature screen calls a network API. / 新增小型纯设置域映射，把
`JudgeConnectionUi.capabilities` 和 `SyncStateEntity` 转换为不可变的 `SyncReceiptItem`。统一由一个辅助函数
选择时间戳，避免 UI 展示适配器未声明的模块。Compose 面板使用已有设计系统组件渲染，不直接调用网络。

Proposed interfaces:

```kotlin
enum class SyncReceiptModule { PROFILE, RATING, SUBMISSIONS, CONTESTS, PROBLEMSET }

data class SyncReceiptItem(
    val module: SyncReceiptModule,
    val syncedAt: Long?,
)

internal fun syncReceiptItems(
    capabilities: Set<JudgeCapability>,
    state: SyncStateEntity?,
): List<SyncReceiptItem>
```

The output order is stable and follows the user-facing pipeline order: profile, rating,
submissions, contests, problemset. `RATING` requires `JudgeCapability.RATING_HISTORY`, while the
other modules map to `PROFILE`, `SUBMISSIONS`, `CONTESTS`, and `PROBLEM_CATALOG`. / 输出顺序固定为资料、
Rating、提交、竞赛、题库。`RATING` 要求 `RATING_HISTORY` 能力；其他模块分别对应 `PROFILE`、`SUBMISSIONS`、
`CONTESTS`、`PROBLEM_CATALOG`。

## Error and lifecycle behavior / 错误与生命周期

- A queued or active sync still renders the previous module timestamps; it never claims a module
  was refreshed before its stage stamps the timestamp. / 排队或同步中仍展示上一次模块时间，不提前声称已更新。
- A failed stage leaves its previous timestamp intact, so the receipt distinguishes stale data
  from never-synced data. / 阶段失败时保留旧时间，从而区分过期数据和从未同步。
- Relative-age formatting clamps negative clock skew to zero and uses resource strings for every
  UI phrase. / 相对时间格式会将时钟回拨限制为零，所有 UI 文案均来自资源文件。
- No raw server error or credential value is introduced. / 不引入服务器原始错误或凭据内容。

## Testing / 测试

Add focused unit tests before production code:

- capability mapping lists only supported modules in stable order;
- profile/rating/contest/problemset timestamps are selected from the right entity fields;
- a Luogu public capability set does not invent a submission item;
- relative-age formatting covers never, just-now, minutes, hours, days, and clock skew.

The existing full gate remains required:

```text
git diff --check
.\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain
```

The final APK must be installed and launched on the already-running `emulator-5554`; the
emulator and computer must remain on. / 最终 APK 必须安装并在已运行的 `emulator-5554` 上启动；不得关闭模拟器或电脑。

## Explicit non-goals / 明确不做

This phase does not add Luogu main-site password login, Cookie/Session/CSRF handling, private
submission scraping, cloud services, cross-device sync, local compiler, custom-input runner,
automatic POST retry, or a database schema migration. / 本阶段不新增洛谷主站密码登录、Cookie/Session/CSRF、
私有提交抓取、云端服务、跨设备同步、本地编译器、自定义输入运行器、POST 自动重试或数据库迁移。
