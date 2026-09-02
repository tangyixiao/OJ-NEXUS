# Submission title context implementation plan / 提交题名上下文实施计划

**Goal:** Preserve the public Luogu problem title as local submission metadata so the submission
center can identify requests without changing the OpenApp request contract. / 目标：将公开洛谷题名保存为本地提交元数据，
使提交中心能辨认请求，同时不改变 OpenApp 请求契约。

**Spec:** `docs/superpowers/specs/2026-09-02-submission-title-context-design.md`

## Constraints / 约束

- PID remains the only submission identity.
- `title` is nullable, display-only, and never serialized into OpenApp JSON.
- Room migration `10 → 11` is additive and preserves all existing rows.
- No main-site credentials, cookies, sessions, CSRF state, cloud service, or source/input persistence.
- Tests are written and observed failing before production changes.

## Tasks / 任务

1. **Failing tests / 失败测试**
   - Test `LuoguProblemJudgeRequest.displayTitle` reaches local `SubmissionJobEntity.title`.
   - Test the OpenApp DTO does not expose or serialize `displayTitle`.
   - Test the v10→v11 migration preserves an existing submission job and gives it a null title.
   - Test title and PID-only rows render with the correct display context.

2. **Implementation / 实现**
   - Add the local-only display title to the request, entity, repository persistence, and workspace
     request construction.
   - Add the Room v11 column and migration, export schema 11, and render the title in the submission
     center with existing design tokens and localized copy.

3. **Docs and release / 文档与发布**
   - Update bilingual README and roadmap while preserving all earlier entries.
   - Create bilingual `docs/releases/v0.3.45.md` and `SHA256SUMS-v0.3.45.txt`.
   - Run the complete quality gate, sign/install without clearing data, and verify the local center.
   - Commit with bilingual message, push branch and annotated `v0.3.45`, publish the signed APK and
     checksum, and audit remote SHAs, Release assets, installed version, emulator state, and clean tree.
