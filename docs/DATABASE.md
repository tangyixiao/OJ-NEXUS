# OJ NEXUS — Database Plan (Room)

Status: **designed, not implemented**. Phase 1 introduces Room with exactly this shape (v1).
Entity fields below are the contract; indexes/constraints are binding.

## Entities

- **JudgeAccount** — one connected judge identity per user profile.
  `id PK`, `judgeId (JudgeId string, indexed+unique-with-handle)`, `handle`, `linkedAt`,
  `lastSyncAt`, `lastSubmissionSyncAt`, `syncState (IDLE/SYNCING/SUCCESS/ERROR/PARTIAL)`,
  `syncError`.
- **Problem** — unified problem. `id PK`, `judgeId`, `problemCode` (judge-side code),
  `title`, `difficulty` (unified rating, nullable), `url`.
  UNIQUE(`judgeId`, `problemCode`); index on `judgeId`, `difficulty`.
- **ProblemTag** — `problemId FK→Problem (indexed, CASCADE)`, `tag`. Index on `tag`.
  Tags are judge-side data strings; a future TagAlias table normalizes synonyms.
- **Submission** — unified submission. `id PK`, `judgeId`, `problemId FK→Problem`,
  `externalId` (judge-side id), `epochMs (indexed)`, `verdict` (unified), `rawVerdict`,
  `language`, `timeMs`, `memoryKb`, `contestId (nullable)`, `attemptOrder`.
  UNIQUE(`judgeId`, `externalId`) — idempotent incremental sync; index (`problemId`, `epochMs`).
- **Contest** — `id PK`, `judgeId`, `externalId`, `name`, `startTimeMs (indexed)`,
  `durationMs`, UNIQUE(`judgeId`, `externalId`).
- **ContestParticipation** — user's contests: `contestId FK`, `ratingBefore`, `ratingAfter`,
  `delta (indexed)`, `rank`. UNIQUE(`contestId`).
- **Review** — per-problem review state. `problemId PK/FK`, `state`, `nextReviewAt (indexed)`,
  `reviewStage` (1/3/7/21d schedule), `lastReviewedAt`, `rootCause`, `keyInsight`,
  `complexityNote`, `implementationNote`.
- **FailureEntry** — `id PK`, `problemId FK (indexed)`, `submissionId FK (nullable)`,
  `verdict`, `rootCause` (Thinking/Implementation/Boundary/Complexity/KnowledgeGap/Reading/
  Careless/Other), `note`, `epochMs`.
- **ProblemNote** — `problemId PK/FK`, `body`, `updatedAt`.
- **TrainingSession** — `id PK`, `type (PRACTICE/FOCUS/UPSOLVE/REVIEW)`, `startedAt`,
  `endedAt (nullable)`, `targetDurationMin`, `note`. Elapsed time derives from
  `startedAt` + a paused-accumulation counter; never a per-second DB write.
- **TrainingSessionProblem** — `sessionId FK`, `problemId FK`, `solved`, `attempts`;
  PK(`sessionId`, `problemId`).
- **KnowledgeNode** — `id PK`, `parentId FK (self, nullable)`, `name`, `path` (materialized,
  indexed). Seeded from the fixed taxonomy in KnowledgeArea.
- **ProblemKnowledgeRelation** — `problemId FK`, `nodeId FK`, `weight`;
  PK(`problemId`, `nodeId`).
- **MasterySnapshot** — `id PK`, `nodeId FK (indexed)`, `score (0–100)`, `computedAt (indexed)`,
  `reasons` (serialized explain list). Written by the Mastery Engine only.
- **Achievement** — `id PK` (stable slug), `name`, `description`, `rarity`, `category`.
- **AchievementUnlock** — `achievementId PK/FK`, `unlockedAt`, `progressSnapshot`.
- **SyncMetadata** — `judgeId PK`, `lastSyncAt`, `lastSubmissionTime`, `state`, `error`.

## Rules

- Foreign keys with explicit actions (CASCADE on owned children); indices for every query path
  used by features; UNIQUE constraints carry sync idempotency.
- DAOs return `Flow` for UI lists and suspend for writes; large history queries paginate.
- Migrations are forward-only and tested; exportSchema=true with committed schemas.
- All timestamps are UTC epoch millis; local day boundaries are computed with the user's zone
  at query time (heatmap/streak/day bucketing must agree via one shared day-key helper).
