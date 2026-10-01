# Android 共用布局验收 — 2026-10-01

本次延续已批准的全局视觉层级调整，补齐窄屏和字体缩放验收。起点为
`codex/android-visual-hierarchy` / `7edd6ea`，初始工作区干净。

## 修复

- `NexusSection`：标题使用剩余宽度换行，标题与右侧操作间保留 8dp 设计 token 间距。
- `NexusBottomBar`：导航行保持 60dp 最小高度，按最长标签需要增高；五个 tab 等高，
  图标和标签起始行对齐。点击区域包含内部留白，系统导航栏 inset 保留。
- 顶部栏和命令栏保持原实现。命令栏两侧留白仍能打开命令面板，按钮区域宽度覆盖整行且至少 48dp 高。

## 新增回归检查

`app/src/androidTest/java/com/ojnexus/core/designsystem/SharedLayoutComposeTest.kt` 包含 6 项：
中英文长区块标题与 trailing 操作、无 trailing 的完整标题、顶部标题纵向可见与横向省略、
五个导航入口的文字边界/对齐/回调、命令栏两侧边缘点击。组件夹具使用 320dp 宽度和 200% 字体。

修复前英文右侧操作不可见，中文右侧操作横向裁切，底部导航标签纵向裁切；修复后 6/6 通过。
文本检查比较实际行边界与测量尺寸，容许 1px 整数取整差异。不能仅用 `hasVisualOverflow` 判定：
段落可保留可用宽度，而 Text 节点按内容收缩，从而产生假阳性。顶部栏无需改动。

## 本次运行结果

Windows 本机 JBR + Pixel_9 模拟器，Android API 37：

```powershell
.\tools\gradlew-local.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ojnexus.core.designsystem.SharedLayoutComposeTest" --console=plain
.\tools\gradlew-local.bat :app:testDebugUnitTest --rerun assembleDebug lintDebug connectedDebugAndroidTest --console=plain
```

- 新增设备检查：6/6 通过。
- 单元测试：强制重新执行，547/547 通过，0 失败、0 错误、0 跳过。
- 完整设备测试：33/33 通过，0 失败、0 错误、0 跳过。
- Debug APK：`BUILD SUCCESSFUL`。
- Debug Lint：0 错误、93 警告；未据此宣称全仓警告清零。
- 整体 Gradle 命令：`BUILD SUCCESSFUL in 3m 6s`。
- 独立只读代码审查：未发现需要修复的问题。审查者未独立运行构建/设备测试；上述运行结果来自主执行者。
- `git diff --check` 通过。

完整应用截图也已查看：默认 100% 字体、英文和中文的 320dp / 200% 字体首页。
导航文字完整，图标起始行对齐，命令栏与系统手势区分离；放大后英文标签允许多行换行。
截图和日志保存在被忽略的 `app/build/outputs/visual-validation/`：

- `after-default.png`
- `after-narrow-large-en.png`
- `after-narrow-large-zh.png`
- `focused-red.log`、`focused-green.log`、`full-validation.log`

验收后恢复模拟器原有 `1080x1920` 分辨率 override、`font_scale=1.0` 和空应用 locale override。
这里只覆盖该模拟器、上述语言和字体条件；OEM 字体、全部页面、其他系统导航模式仍需各自验证。

测试使用现有 Compose 依赖及 [Android 官方语义测试 API](https://developer.android.com/develop/ui/compose/testing/apis)。
