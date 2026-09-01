# Luogu Workspace First-use Setup Entry / 洛谷工作区首次配置入口

## Goal / 目标

让首次进入洛谷代码工作区、但尚未配置 OpenApp 凭据的用户可以直接进入设置完成配置。现有警告继续保留，避免用户失去状态提示；新增操作只负责导航，不改变凭据存储、提交或同步策略。

## Scope / 范围

- `WorkspaceScreen` 接收一个 `onOpenSettings` 回调。
- 缺少凭据时，在现有 warning 下显示可访问的 `OPEN SETTINGS / 打开设置` 操作。
- `NexusApp` 将该回调连接到既有 `NexusRoutes.SETTINGS` 路由。
- 新增英文和简体中文资源及无障碍描述。
- 不新增网络请求、不自动提交、不保存密码、不引入云端服务。

## Interaction / 交互

1. 用户从题目详情或提交中心进入工作区。
2. 工作区检测到未配置 OpenApp 凭据，执行按钮保持禁用，原有 warning 显示。
3. 用户点击 `OPEN SETTINGS / 打开设置`，应用导航到已有设置页。
4. 返回工作区后，工作区继续由现有 `WorkspaceViewModel` 读取凭据状态；本阶段不增加新的状态同步逻辑。

## Architecture / 架构

导航依赖保持在应用壳层：`NexusApp` 创建路由并将设置导航回调传给工作区。`WorkspaceScreen` 只渲染状态并触发回调，因此仍符合 UI → ViewModel / navigation-shell 的边界，不让 Composable 直接操作 `NavController`。

## Error and accessibility / 错误与无障碍

没有凭据时仍显示警告，执行操作仍不可用；直达设置操作使用按钮角色和独立 content description。英文为默认资源，`values-zh-rCN` 提供简体中文翻译，系统语言选择逻辑保持不变。

## Verification / 验证

- 资源检查和编译必须通过。
- 单元测试继续覆盖工作区行为；新增 UI 导航契约通过参数接口和模拟器 UIAutomator 验证。
- 完整执行 `clean test assembleDebug lintDebug`。
- 安装 Debug APK 后，在模拟器中打开工作区并确认无凭据提示与设置入口可见，确认无 `FATAL EXCEPTION`。

