# OJ NEXUS — Roadmap 121–220 v1 (documentation prerelease)

This is a **planning snapshot for review**, containing 100 planned phases and no new
application binary. Android remains 1.0.3 / 105. The source branch is
`codex/roadmap-121-220`, based on main `11555095e2a66c8dfc4dd9918118ed81d97720da`.
Publishing this snapshot does not merge its planning PR or approve phase implementation.

## Included

- [Full 100-phase roadmap](../LONG_RANGE_PLAN_121_220.md): 20 waves, one shared deliverable
  plus Android, Windows, Linux and Apple in each wave; 20 phases per lane.
- [Planning design](../superpowers/specs/2026-10-23-balanced-100-phase-roadmap-design.md):
  verified baseline, chosen structure, permanent product/security constraints.
- [First-wave kickoff](../superpowers/plans/2026-10-23-phase-121-125-kickoff.md): execution
  checklist for truthful platform baselines and deferred runtime acceptance.
- [Validation record](../verification/2026-10-23-planning-validation.md): structure, dependency,
  link and review checks plus fresh Android baseline build/test evidence.

## Delivery policy

Each independently reviewable feature should receive a scoped PR. Qualified native releases
follow platform-specific build, runtime, upgrade and signing gates; one platform's success
cannot certify another. No empty PR, artificial coauthor, unreviewed merge or fake release is
required for GitHub achievements. No badge is promised or claimed.

## Boundaries

- All Phase 121–220 entries remain PLANNED.
- No Android, Windows, Linux, macOS or iOS implementation changed in this snapshot.
- No production E2EE, live sync endpoint, new credentials or new schema is introduced.
- No attached-device, Apple runtime or Linux runtime acceptance is claimed.
- The docs-only prerelease uses its own tag and is not marked as the latest application release.

---

这是一份供审查与下载的 **100 期规划文档预发布**，没有新应用安装包。
每轮公共契约、Android、Windows、Linux、Apple 各一期；所有阶段均待实现。
本次不自动合并规划 PR，也不把规划发布当成应用功能已经完成。
