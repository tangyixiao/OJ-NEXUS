# Luogu OpenApp Settings Focus / 洛谷 OpenApp 设置定位

## Goal / 目标

让洛谷工作区的“打开设置”操作直接把用户带到 OpenApp 凭据配置区，使首次配置链路可以实际完成，而不是只打开设置页顶部。

## Scope / 范围

- `SettingsScreen` 增加可选的 `focusOpenApp` 参数，默认值保持普通设置入口行为。
- `NexusApp` 从工作区跳转设置时传入 `focusOpenApp = true`。
- 设置页使用 Compose `BringIntoViewRequester` 定位 OpenApp 区域；不使用固定像素滚动。
- 不改动凭据验证、Keystore、提交、同步、语言或云端逻辑。
- README、路线图和 Release 说明继续中英文结合，历史内容保留。

## Interaction / 交互

1. 用户在无凭据的洛谷工作区点击 `OPEN SETTINGS / 打开设置`。
2. 应用进入设置页并将 `LUOGU OPENAPP` 配置区滚动到可见位置。
3. 用户可以立即填写 OpenApp 用户名和密钥；返回工作区仍由现有状态流刷新凭据状态。
4. 用户从其他入口进入设置时，不传入焦点参数，仍从顶部开始。

## Architecture and safety / 架构与安全

导航仍由 `NexusApp` 持有，设置页只消费一个 UI 定位参数。定位只影响滚动位置，不读取或写入任何凭据。凭据继续只进入现有 Keystore 流程，不进入保存状态或备份。

## Verification / 验证

- Kotlin 编译、全量单元测试、Debug 构建和 Lint 必须通过。
- 模拟器从工作区点击入口后，UIAutomator 必须同时看到 `SETTINGS` 和 `LUOGU OPENAPP` 配置内容。
- 检查应用日志没有 `FATAL EXCEPTION`，且模拟器保持运行。

