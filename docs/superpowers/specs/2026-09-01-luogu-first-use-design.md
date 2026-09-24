# Phase 41 — Luogu first-use public sync loop / 洛谷首次使用公开同步闭环

## Goal / 目标

Make a fresh OJ NEXUS installation immediately actionable for the Luogu public-data path:
from the Dashboard's disconnected state, a user can open the Luogu binding panel directly,
enter a public handle, and observe the existing queued/syncing/result receipt without searching
through the full Settings page. / 让全新安装的 OJ NEXUS 能直接进入洛谷公开数据流程：用户从 Dashboard
的未连接状态即可直接打开洛谷绑定区域，输入公开用户名，并观察现有的排队、同步和同步回执，不必在完整
设置页中手动寻找。

## Scope / 范围

1. Add a `settings/luogu` navigation destination that focuses the existing Luogu judge panel.
   The ordinary `settings` destination remains top-aligned, and `settings/openapp` keeps its
   current OpenApp focus behavior. / 新增 `settings/luogu` 导航目的地，聚焦已有的洛谷评测平台面板；普通
   `settings` 仍从顶部开始，`settings/openapp` 保持现有 OpenApp 聚焦行为。
2. Add a localized Dashboard action for Luogu setup when Luogu is not connected. It reuses the
   current account-binding ViewModel and WorkManager scheduling; it does not make a second
   request or create a second account state. / 当洛谷尚未连接时，在 Dashboard 添加本地化的洛谷配置操作；
   复用现有账号绑定 ViewModel 和 WorkManager 调度，不新增请求或第二套账号状态。
3. Keep the existing Dashboard connection row and generic Settings entry intact. Once Luogu is
   connected, the setup action is absent and the existing connection/sync state remains the
   source of truth. / 保留现有 Dashboard 连接行和通用设置入口。洛谷连接后，配置操作消失，现有连接和同步
   状态仍是唯一事实来源。
4. Add equivalent English and Simplified Chinese resources and accessibility descriptions.
   No user handles, problem titles, judge names, or server messages are translated. / 添加成对的英文和简体
   中文资源及无障碍描述；用户句柄、题目标题、评测平台名称和服务器消息不翻译。

## Architecture and data flow / 架构与数据流

`DashboardViewModel` exposes only whether an enabled Luogu account exists in its already observed
connection snapshot. `DashboardScreen` renders the setup action and emits a navigation event.
`NexusApp` navigates to `settings/luogu`. `SettingsScreen` locates the Luogu panel with the same
viewport-relative scroll calculation already used for OpenApp, then renders the existing
`SettingsViewModel` state. Binding still calls the existing public `api/user/search` connector;
successful binding still queues the existing public profile, Rating, contest, and problem stages.
/ `DashboardViewModel` 只从已经观察到的连接快照中暴露“是否存在启用的洛谷账号”；Dashboard 展示配置操作并
发出导航事件；`NexusApp` 导航到 `settings/luogu`；`SettingsScreen` 使用已有的 OpenApp 视口相对滚动计算
来定位洛谷面板，然后渲染既有 `SettingsViewModel` 状态。绑定仍调用现有公开的 `api/user/search`，成功后仍
排队公开资料、Rating、竞赛和题库阶段。

The Dashboard action is local navigation only. No main-site password, Cookie, Session, CSRF
state, cloud account, cross-device sync, local compiler, custom-input runner, or automatic
submission retry is introduced. / Dashboard 操作仅进行本地导航，不新增主站密码、Cookie、Session、CSRF
状态、云端账号、跨设备同步、本地编译器、自定义输入运行器或自动提交重试。

## Error and lifecycle behavior / 错误与生命周期

- Empty, invalid, not-found, rate-limit, network, and API errors continue to render through the
  existing localized Settings error mapping. / 空输入、非法句柄、未找到、请求受限、网络和 API 错误继续
  使用现有本地化设置错误映射。
- Focus scrolling is best-effort and bounded by the scroll state's maximum value. If the target
  has not been laid out, the screen remains usable at its current position. / 聚焦滚动是有界的尽力操作，受
  滚动状态最大值限制；目标尚未布局时页面仍保持当前位置可用。
- Re-entering ordinary Settings must not inherit Luogu focus. / 再次进入普通设置不能继承洛谷聚焦状态。

## Testing and acceptance / 测试与验收

- Unit-test the pure Dashboard predicate for no account, Codeforces-only, and Luogu-connected
  snapshots. / 为无账号、仅 Codeforces、已连接洛谷三种快照测试 Dashboard 纯判断逻辑。
- Test route declarations and the focus flag wiring at compile/test level; preserve existing
  OpenApp focus behavior. / 在编译/测试层验证路由声明和聚焦标志传递，并保持现有 OpenApp 聚焦行为。
- Verify English/Chinese resource-key parity and accessibility labels. / 验证中英文资源键集合及无障碍标签一致。
- Run `git diff --check` and the complete Gradle gate, install the Release APK without clearing
  the existing emulator data, and verify the app launches with no fatal exception. / 执行 `git diff --check`
  和完整 Gradle 门禁；不清除现有模拟器数据安装 Release APK，并验证应用启动且无致命异常。

## Non-goals / 不在本阶段

This phase does not add a new Luogu API, private submission-history import, main-site login,
credential storage beyond the existing OpenApp store, cloud service, cross-device sync, compiler,
or judge protocol. Earlier releases and documentation remain preserved. / 本阶段不新增洛谷 API、私有提交
历史导入、主站登录、除现有 OpenApp 存储之外的凭据存储、云服务、跨设备同步、编译器或评测协议；此前
Release 和文档继续保留。
