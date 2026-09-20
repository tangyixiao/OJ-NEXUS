# Note Index Design / 笔记索引设计

## Goal

让已经保存到本地的题目笔记变得可读、可检索、可复习：题库新增第三个范围 `NOTE INDEX`，按笔记更新时间倒序列出所有
笔记非空的题目，并支持按笔记字段、评测平台和解决状态筛选。

现状缺口：`problem_notes` 表（`key_insight` / `implementation_notes` / `complexity` / `general`）只能在题目详情页写入，
题库、训练、分析都没有任何读取入口。README 声明的 “history, notes, review, and stats work fully offline”
中，notes 实际上只写不读。

## Scope

只投影已有的本地 Room 数据，不新增表、不迁移、不升级 Room 版本、不发网络请求、不写任何数据。

## Architecture

数据路径（自下而上）：

1. `NoteDao.observeIndex()`：一条有界联表查询，`problem_notes` JOIN `problems`，返回扁平标量行 `NoteIndexRow`
   （题号 / 题名 / 难度 / 尝试次数 / solved / `EXISTS(reviews)` / 四个笔记字段 / 笔记更新时间），
   按 `n.updated_at DESC, n.problem_id DESC` 排序。
2. `NoteIndexRow.toDomain()`：映射为 `ProblemNoteEntry`，其中评测平台按 `JudgeId.fromId` 解析，未知 judge 降级为
   `JudgeId.LOCAL`（与既有 mapper 的降级策略一致）。
3. `ProblemRepository.observeNoteIndex()`：过滤掉四个字段全为空的笔记行后暴露 `Flow<List<ProblemNoteEntry>>`。
   空白规则放在 Kotlin 纯函数里，而不是 SQL 里，这样它可以被普通单测覆盖。
4. `ProblemsViewModel.noteState`：`combine(repository.observeNoteIndex(), noteFilter)` → `Loadable<NoteIndexUiState>`，
   与题库状态、远端题库状态并列；筛选只作用于内存列表。

纯逻辑层：

- `NoteField`（ALL / KEY_INSIGHT / IMPLEMENTATION / COMPLEXITY / GENERAL）
- `ProblemNotes.hasContent()`：四个字段是否至少有一个非空白
- `ProblemNotes.textFor(field)`：行内预览文本；ALL 取阅读顺序上第一个非空字段，空白降级为 `null`
- `ProblemNotes.searchScope(field)`：搜索可命中的文本集合；ALL 覆盖四个字段
- `ProblemNoteFilter` + `applyNoteFilter`：查询词（大小写不敏感、trim）匹配题名、公开题号或所选字段的笔记正文；
  另有 judge 与 unsolvedOnly 两个条件；保持输入顺序，不重排
- `summarizeNoteIndex(entries, visible)`：读数据（已索引 / 当前显示 / 未解决 / 复习中）

## UI behavior

`ProblemScope` 变为 `LIBRARY | REMOTE | NOTES`，`ScopeSwitcher` 三档互斥，与远端题库一致由父级持有范围状态。

`NoteIndexContent` 结构：范围切换 → 搜索框 → `NOTE INDEX PULSE` 四项读数 → 字段chip行 + 平台chip行（含「仅未解决」）
→ 区块标签与分栏线 → 行列表。

- 行（`NoteIndexRow`，76dp）：状态色轨（3dp）+ 评测平台 + 公开题号 + 文字状态标签 + 题名 + 预览行。
  状态永远带文字（UNSOLVED / ATTEMPTED / SOLVED / REVIEW），颜色只做强化。
- 预览行：`%1$s · %2$s`（字段名 · 正文）；所选字段为空时显示「该字段暂无文字」，不回退到别的字段。
- 筛选非默认时 `NOTE INDEX PULSE` 出现 `CLEAR FILTERS`，带无障碍描述。
- 空态区分：索引为空 → 「暂无笔记」+「在题目详情页保存笔记后会出现在这里」；筛选无结果 → 「没有符合筛选条件的笔记」。
- 平台 chip 只渲染索引里真实存在的评测平台，不显示空筛选。
- 行点击进入已有的 `PROBLEM_DETAIL` 路由，不新增路由。

## Non-goals

- 不在题库里编辑笔记；写入仍然只在题目详情页发生。
- 不新增笔记字段、标签、全文索引表或 FTS。
- 不新增网络请求、凭据流程、后台任务、云同步、本地编译器或自定义输入运行器。
- 不做笔记导出、分享或跨设备同步。

## Boundaries

- `res/values/strings.xml` 与 `res/values-zh-rCN/strings.xml` 双侧齐备；Kotlin 侧无硬编码文案。
- 只用设计系统 token（`NexusSpacing` / `NexusRadius` / `NexusTheme`），行高与状态轨宽度按仓库既有约定
  作为文件级命名布局常量。
- Room schema 与导出的 schema JSON 不变；`MigrationTest` 不需要新增用例。
