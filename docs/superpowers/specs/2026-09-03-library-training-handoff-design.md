# Phase 68 — Problem Library to Training Handoff

## Goal

让用户可以把题库当前可见的筛选结果一次性带入训练会话表单，减少从题库找题、再回训练页重复选择的操作，同时保持现有的可编辑确认流程。

## User experience

在本地题库的筛选结果区域下方增加一个克制的蓝色侧轨操作条：

- 标题：`BUILD FROM VIEW`
- 说明：`N PROBLEMS READY FOR TRAINING`
- 点击后进入现有 `TRAINING` 顶层页面，并自动打开已有的新建训练会话对话框。
- 对话框预选当前题库视图中可见的题目，训练类型默认 `PRACTICE`，标签默认 `LIBRARY VIEW`。
- 用户仍可取消、修改类型、时长、标签和题目；只有点击现有 `START` 后才创建会话。
- 空题库或无匹配结果不显示该操作条；远程题库结果不参与该操作。

操作条使用单一 NEXUS BLUE 侧轨、现有表面与分割线层级，不使用渐变、发光、循环动画或图标装饰。内容描述完整表达数量和动作，状态不依赖颜色单独传达。

## Architecture and data flow

```
ProblemsScreen (visible local rows)
        │ one-shot List<Long>
        ▼
NexusApp pendingTrainingProblemIds
        │ top-level Training navigation
        ▼
TrainingScreen initialProblemIds
        │ one-shot local dialog prefill
        ▼
Existing NewSessionDialog → existing SessionViewModel transaction
```

- 新增 `buildTrainingProblemIds(List<Problem>): List<Long>` 纯函数，负责从当前可见题目提取去重后的稳定 ID 顺序。
- `NexusApp` 只持有导航期间的内存上下文；消费后立即清空，不进入 DataStore、Room 或路由字符串。
- `TrainingScreen` 将上下文转换为对话框初始选择，消费后清空；关闭对话框时清除本地草稿。
- 现有 `TrainingViewModel.startSession`、仓储和数据库事务保持不变。
- 远程题库保持只读浏览，不能触发该本地训练动作。

## State and edge cases

- 当前可见列表为空时，操作条不存在，避免产生无效训练草稿。
- 列表中的重复 ID 被去重，首次出现顺序保留。
- 从题库跳到训练后，用户取消对话框不会创建任何记录；再次进入训练页不会重复打开旧请求。
- 已有活动会话时仍交给现有 `TrainingViewModel` 错误状态处理，不添加第二套业务规则。
- 应用因配置变化重组时，导航上下文只在尚未消费前存在；训练对话框内部使用现有 `rememberSaveable` 保存编辑状态。
- `reduceMotion` 开启时，操作条内容变化直接切换；默认使用 120–300ms 的设计系统动效。

## Testing and verification

- 单元测试覆盖空列表、重复 ID 和顺序保持。
- Android ComposeTest 覆盖操作条的文本、可见性语义和点击回调。
- 手工模拟器回归：本地题库筛选出 `CODEFORCES 1029E`，点击 `BUILD FROM VIEW`，确认 Training 对话框预选该题；取消后确认没有新增活动会话，再次点击 `START` 才创建。
- 完成门禁：`test`、`assembleDebug`、`lintDebug`、`connectedDebugAndroidTest` 均成功，并更新版本为 `versionName=0.3.66`、`versionCode=66`。

## Non-goals

本阶段不增加多选编辑器、不增加题目排序算法、不增加远程题库训练、不增加网络请求、数据库迁移、后台任务、账号凭据、编译器或自动提交行为。
