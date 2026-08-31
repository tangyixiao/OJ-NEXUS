# Workspace Result Continuity / 工作区结果连续性

## Goal / 目标

When a user reopens a Luogu problem workspace, the latest locally persisted OpenApp evaluation
must be rendered with the same details that the submission center can show. Compilation success or
failure must also be explicit even when the upstream compiler message is empty. / 用户重新打开
洛谷题目工作区时，最近一次本地保存的 OpenApp 评测必须展示与提交中心相同的详情；即使上游
没有返回编译文字信息，也必须明确显示编译成功或失败。

## Scope / 范围

- Rehydrate `compileSuccess`, `compileMessage`, `output`, `exitCode`, `executionTimeMs`, and
  `memoryKiB` from `SubmissionJobEntity` into the restored `LuoguOpenEvaluation`. / 将提交任务中
  已保存的六类评测字段恢复到工作区评测对象。
- Render an explicit localized compile status in the workspace. The raw judge status remains
  visible and no upstream status code is reinterpreted. / 工作区明确展示本地化编译状态，仍保留
  原始评测状态，不重新解释上游状态码。
- Keep source code, standard input, credentials, cloud sync, main-site login, cookies, sessions,
  CSRF state, background submission, automatic POST retry, local compiler, and custom-input run
  out of scope. / 继续不保存源代码、标准输入或凭据，不增加云同步、主站登录、Cookie、Session、
  CSRF、后台提交、POST 自动重试、本地编译器或自定义输入运行。

## Data flow / 数据流

1. `LuoguSubmissionRepository.fetchResult` remains the only writer of evaluation details. /
   `LuoguSubmissionRepository.fetchResult` 仍是评测详情唯一写入入口。
2. `WorkspaceViewModel` maps every nullable persisted field when restoring the latest job. /
   `WorkspaceViewModel` 恢复最近任务时映射全部可空字段。
3. `WorkspaceScreen.EvaluationContent` displays compile status first, followed by available raw
   details. Missing values remain absent rather than fabricated. / 工作区先显示编译状态，再显示
   实际存在的原始详情；缺失字段保持为空。

## Verification / 验证

- A unit test fails before the mapping is added and passes after it is added. / 单元测试必须先在
  没有映射时失败，再在实现后通过。
- The full test suite, Debug assembly, and lint must pass. Pixel_9 must show the existing settings
  and submission center without a crash. / 必须通过全量测试、Debug 构建和 lint，并在 Pixel_9
  验证设置页与提交中心无崩溃。
