# Phase 42 — OpenApp action intent clarity / OpenApp 操作意图明确化

## Goal / 目标

Make the primary workspace action accurately describe what the selected gateway will do. The
current Luogu Open Platform gateway supports problem submission but explicitly does not support
custom-input execution, so its workspace must say `SUBMIT` rather than the generic `EXECUTE`.
/ 让工作区主操作准确描述当前网关将执行的动作。当前洛谷 Open Platform 网关支持题目提交，但明确不支持
自定义输入运行，因此工作区必须显示“提交”，不能继续显示含义模糊的“执行”。

## Scope / 范围

1. Add a pure `workspaceActionLabelRes(WorkspaceState): Int` mapping in the workspace feature:
   `WORKING` while busy, `workspace_mode_submit` in submit mode, and `workspace_mode_run` in run
   mode. / 在工作区功能中添加纯 `workspaceActionLabelRes(WorkspaceState): Int` 映射：忙碌时显示
   `WORKING`，提交模式显示 `SUBMIT`，运行模式显示 `RUN`。
2. Use that mapping for the primary action label. The existing capability gate continues to hide
   run controls for the real Luogu gateway, and the submit callback remains unchanged. / 主操作标签使用该
   映射；已有能力门禁继续在真实洛谷网关上隐藏运行控件，提交回调保持不变。
3. Reuse existing English and Simplified Chinese resource keys. No new network, persistence,
   credential, or API behavior is introduced. / 复用现有英文和简体中文资源键，不新增网络、持久化、凭据或
   API 行为。

## Architecture and data flow / 架构与数据流

`WorkspaceScreen` derives the label resource ID from the already collected `WorkspaceState` and
passes it to `stringResource`. The ViewModel and `LuoguOpenGateway` remain untouched: actual
mode selection still comes from `supportsCustomInputRun`, and submission still calls
`submitProblem` with the selected language and code. / `WorkspaceScreen` 从已收集的 `WorkspaceState` 推导
资源 ID，再交给 `stringResource`；ViewModel 和 `LuoguOpenGateway` 不变：模式仍由
`supportsCustomInputRun` 决定，提交仍调用 `submitProblem` 并传递所选语言和代码。

## Error and accessibility behavior / 错误与无障碍行为

- Busy state continues to use the existing `WORKING` label and disabled button behavior. / 忙碌状态继续
  使用现有 `WORKING` 标签并禁用按钮。
- The visible label and accessibility node now communicate the actual action through the same
  text, without color-only meaning or additional raw server text. / 可见标签和无障碍节点通过同一文本表达真实
  操作，不依赖颜色，也不增加服务器原始文本。
- Existing credential, network, quota, result, and retry/error behavior is unchanged. / 现有凭据、网络、配额、
  结果和错误行为保持不变。

## Testing and acceptance / 测试与验收

- Unit-test busy, submit, and run mappings without Android or network dependencies. / 在无 Android 和网络依赖
  下测试忙碌、提交和运行三种映射。
- Run the complete Gradle gate and install the Release APK without clearing the emulator data.
  Open a Luogu workspace and verify the real unsupported-run gateway displays `SUBMIT`; verify
  the app has no fatal exception and the emulator stays online. / 执行完整 Gradle 门禁，在不清除模拟器数据的
  情况下安装 Release APK；打开洛谷工作区，验证真实的不支持运行网关显示“提交”，确认无致命异常且模拟器
  保持在线。

## Non-goals / 不在本阶段

This phase does not implement a local compiler, custom-input runner, main-site password, Cookie,
Session, CSRF state, cloud account, cross-device sync, or automatic POST retry. / 本阶段不实现本地编译器、
自定义输入运行器、主站密码、Cookie、Session、CSRF 状态、云端账号、跨设备同步或自动 POST 重试。
