# Sync Receipt Implementation Plan / 同步回执实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an honest, localized per-module synchronization receipt in each connected judge panel.

**Architecture:** Add pure mapping and relative-age formatting helpers in the settings feature. The
Compose panel consumes those helpers and existing `SyncStateEntity` timestamps; it never calls a
network or database API directly. The capability set remains the sole source of truth for which
modules are shown.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room entities, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-01-sync-receipt-design.md`

## Global Constraints

- Use the existing Kotlin/Compose/Material 3 stack; add no dependency.
- Keep all UI strings in `app/src/main/res/values/strings.xml` and `values-zh-rCN/strings.xml`.
- Use existing `NexusTheme`, `NexusSpacing`, `NexusTypography`, and `NexusSection` tokens.
- Do not add Luogu main-site passwords, Cookie/Session/CSRF, cloud sync, local compiler, custom-input runner, or automatic POST retry.
- Do not change the Room schema; reuse `SyncStateEntity` fields.
- Preserve all previous README, ROADMAP, commit, and Release history.
- Run `git diff --check` and `.\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain` before completion claims.
- Keep `emulator-5554` and the computer powered on.

## File Map

- Create: `app/src/main/java/com/ojnexus/feature/settings/SyncReceipt.kt` — pure module mapping and age formatter.
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt` — render the receipt below existing sync metadata.
- Modify: `app/src/main/res/values/strings.xml` — English receipt labels and age formats.
- Modify: `app/src/main/res/values-zh-rCN/strings.xml` — Simplified Chinese receipt labels and age formats.
- Test: `app/src/test/java/com/ojnexus/feature/settings/SyncReceiptTest.kt` — mapping and formatter behavior.
- Modify: `README.md` — append bilingual Phase 37 status while preserving earlier text.
- Modify: `docs/ROADMAP.md` — append bilingual Phase 37 entry.
- Create: `docs/releases/v0.3.33.md` — bilingual release notes with verification evidence.
- Create: `docs/superpowers/specs/2026-09-01-sync-receipt-design.md` — approved design.
- Create: `docs/superpowers/plans/2026-09-01-sync-receipt.md` — this implementation plan.

### Task 1: Add the failing pure-domain tests

**Files:**
- Create: `app/src/test/java/com/ojnexus/feature/settings/SyncReceiptTest.kt`

**Interfaces:**
- Consumes the planned `syncReceiptItems(capabilities, state)` and `formatSyncAge(now, syncedAt)` functions.
- Produces executable behavior requirements for the implementation task.

- [ ] **Step 1: Write the failing tests**

```kotlin
class SyncReceiptTest {
    @Test
    fun `receipt follows capability order and selects entity timestamps`() {
        val state = SyncStateEntity(
            judge = JudgeId.LUOGU.id,
            profileSyncedAt = 1_000L,
            ratingSyncedAt = 2_000L,
            contestsSyncedAt = 4_000L,
            problemsetSyncedAt = 5_000L,
            submissionsSyncedAt = 3_000L,
        )

        assertEquals(
            listOf(
                SyncReceiptItem(SyncReceiptModule.PROFILE, 1_000L),
                SyncReceiptItem(SyncReceiptModule.RATING, 2_000L),
                SyncReceiptItem(SyncReceiptModule.SUBMISSIONS, 3_000L),
                SyncReceiptItem(SyncReceiptModule.CONTESTS, 4_000L),
                SyncReceiptItem(SyncReceiptModule.PROBLEMSET, 5_000L),
            ),
            syncReceiptItems(
                capabilities = setOf(
                    JudgeCapability.PROFILE,
                    JudgeCapability.RATING_HISTORY,
                    JudgeCapability.SUBMISSIONS,
                    JudgeCapability.CONTESTS,
                    JudgeCapability.PROBLEM_CATALOG,
                ),
                state = state,
            ),
        )
    }

    @Test
    fun `public Luogu capabilities do not invent private submissions`() {
        val items = syncReceiptItems(
            capabilities = setOf(
                JudgeCapability.PROFILE,
                JudgeCapability.RATING_HISTORY,
                JudgeCapability.CONTESTS,
                JudgeCapability.PROBLEM_CATALOG,
            ),
            state = SyncStateEntity(judge = JudgeId.LUOGU.id),
        )

        assertEquals(
            listOf(
                SyncReceiptModule.PROFILE,
                SyncReceiptModule.RATING,
                SyncReceiptModule.CONTESTS,
                SyncReceiptModule.PROBLEMSET,
            ),
            items.map(SyncReceiptItem::module),
        )
    }

    @Test
    fun `missing state keeps supported modules never synced`() {
        assertTrue(
            syncReceiptItems(setOf(JudgeCapability.PROFILE), state = null)
                .single().syncedAt == null,
        )
    }
}
```

Add formatter assertions in the same file:

```kotlin
@Test
fun `sync age covers never recent minutes hours days and clock skew`() {
    assertEquals(SyncAge.NEVER, formatSyncAge(now = 100_000L, syncedAt = null))
    assertEquals(SyncAge.JUST_NOW, formatSyncAge(now = 100_000L, syncedAt = 99_999L))
    assertEquals(SyncAge.MINUTES_AGO(2), formatSyncAge(now = 220_000L, syncedAt = 100_000L))
    assertEquals(SyncAge.HOURS_AGO(2), formatSyncAge(now = 7_300_000L, syncedAt = 100_000L))
    assertEquals(SyncAge.DAYS_AGO(2), formatSyncAge(now = 172_900_000L, syncedAt = 100_000L))
    assertEquals(SyncAge.JUST_NOW, formatSyncAge(now = 100_000L, syncedAt = 120_000L))
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```text
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.settings.SyncReceiptTest --no-daemon --console=plain
```

Expected: compilation/test failure because `SyncReceipt`, `syncReceiptItems`, and
`formatSyncAge` do not exist yet. Do not write production code before observing this failure.

### Task 2: Implement pure mapping and age formatting

**Files:**
- Create: `app/src/main/java/com/ojnexus/feature/settings/SyncReceipt.kt`

**Interfaces:**
- Produces `SyncReceiptModule`, `SyncReceiptItem`, `SyncAge`, `syncReceiptItems`, and `formatSyncAge` for Settings and tests.

- [ ] **Step 1: Implement the stable module model**

```kotlin
enum class SyncReceiptModule { PROFILE, RATING, SUBMISSIONS, CONTESTS, PROBLEMSET }

data class SyncReceiptItem(
    val module: SyncReceiptModule,
    val syncedAt: Long?,
)

sealed interface SyncAge {
    data object NEVER : SyncAge
    data object JUST_NOW : SyncAge
    data class MINUTES_AGO(val value: Long) : SyncAge
    data class HOURS_AGO(val value: Long) : SyncAge
    data class DAYS_AGO(val value: Long) : SyncAge
}
```

- [ ] **Step 2: Implement capability mapping**

Create one ordered table that maps:

```text
PROFILE    -> PROFILE       -> profileSyncedAt
RATING     -> RATING_HISTORY-> ratingSyncedAt
SUBMISSIONS-> SUBMISSIONS   -> submissionsSyncedAt
CONTESTS   -> CONTESTS      -> contestsSyncedAt
PROBLEMSET -> PROBLEM_CATALOG -> problemsetSyncedAt
```

Filter that table by capability and read the timestamp from the nullable `SyncStateEntity`.
Return an empty list when no capability is present.

- [ ] **Step 3: Implement `formatSyncAge`**

Use `maxOf(0L, now - syncedAt)` when a timestamp exists. Return `JUST_NOW` below 60 seconds,
minutes below 60 minutes, hours below 24 hours, and days thereafter. A null timestamp returns
`NEVER`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Task 1 command again. Expected: all mapping and age assertions pass.

### Task 3: Render the receipt with localized resources

**Files:**
- Modify: `app/src/main/java/com/ojnexus/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes `syncReceiptItems` and `formatSyncAge`.
- Produces a localized `SYNC COVERAGE / 同步覆盖` section for each connected judge.

- [ ] **Step 1: Add resource strings**

Add matching names in both locales:

```xml
<string name="settings_sync_coverage">SYNC COVERAGE</string>
<string name="settings_sync_module_profile">PROFILE</string>
<string name="settings_sync_module_rating">RATING</string>
<string name="settings_sync_module_submissions">SUBMISSIONS</string>
<string name="settings_sync_module_contests">CONTESTS</string>
<string name="settings_sync_module_problemset">PROBLEMSET</string>
<string name="settings_sync_never">NEVER SYNCED</string>
<string name="settings_sync_just_now">JUST NOW</string>
<string name="settings_sync_minutes_ago">%d MIN AGO</string>
<string name="settings_sync_hours_ago">%d H AGO</string>
<string name="settings_sync_days_ago">%d D AGO</string>
```

Chinese translations use `同步覆盖`, `资料`, `Rating`, `提交`, `竞赛`, `题库`, `从未同步`, `刚刚`,
`%d 分钟前`, `%d 小时前`, and `%d 天前`.

- [ ] **Step 2: Add small pure label functions in the screen**

Map each `SyncReceiptModule` to its resource ID and each `SyncAge` to its localized resource.
Use `System.currentTimeMillis()` only at render time for relative presentation.

- [ ] **Step 3: Insert the section after existing sync metadata**

Call `syncReceiptItems(connection.capabilities, sync)` only inside the connected-account branch.
Render one row per returned item with module label on the left and age label on the right, using
existing `NexusSpacing.xxs`, `NexusTheme.typography.dataSmall`, and existing colors. Do not use
hard-coded UI copy or arbitrary layout tokens.

- [ ] **Step 4: Run focused tests and compile**

Run:

```text
.\tools\gradlew-local.bat testDebugUnitTest --tests com.ojnexus.feature.settings.SyncReceiptTest --tests com.ojnexus.feature.settings.SettingsSyncCapabilityTest --no-daemon --console=plain
```

Expected: all focused tests pass and the app compiles.

### Task 4: Update bilingual project records

**Files:**
- Modify: `README.md`
- Modify: `docs/ROADMAP.md`
- Create: `docs/releases/v0.3.33.md`

- [ ] **Step 1: Append Phase 37 without deleting history**

Document that Settings now exposes capability-backed module timestamps and that Luogu private
submission history remains outside public sync. Keep the status line and all earlier phase entries.

- [ ] **Step 2: Write release notes**

Include English and Chinese sections, changed behavior, non-goals, test command, emulator evidence,
commit placeholder to replace after commit, and the final APK SHA-256 to fill after build.

### Task 5: Verify, commit, publish, and install

**Files:**
- All files from Tasks 1–4.

- [ ] **Step 1: Run the full gate**

```text
git diff --check
.\tools\gradlew-local.bat clean test assembleDebug lintDebug --no-daemon --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero test failures, zero lint errors.

- [ ] **Step 2: Install and launch without powering down the emulator**

```text
& 'D:\Android\platform-tools\adb.exe' -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& 'D:\Android\platform-tools\adb.exe' -s emulator-5554 shell am force-stop com.ojnexus
& 'D:\Android\platform-tools\adb.exe' -s emulator-5554 shell monkey -p com.ojnexus 1
& 'D:\Android\platform-tools\adb.exe' -s emulator-5554 devices
```

Expected: install succeeds, `emulator-5554 device` remains present, and `MainActivity` is resumed.

- [ ] **Step 3: Record the final APK SHA and commit**

```text
Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
git status --short --branch
git diff --check
git add README.md docs/ROADMAP.md docs/releases/v0.3.33.md app/src/main app/src/test docs/superpowers
git commit -m "feat: show sync receipt / 显示同步回执"
```

Replace the release-note commit/hash placeholders before this commit if needed; never include
credentials or machine-local files.

- [ ] **Step 4: Push and create GitHub Release**

```text
git push origin HEAD:codex/phase-5-arena
git tag -a v0.3.33 -m "OJ NEXUS v0.3.33 / sync receipt"
git push origin v0.3.33
gh release create v0.3.33 app\build\outputs\apk\debug\app-debug.apk --verify-tag --title "OJ NEXUS v0.3.33 — Sync receipt / 同步回执" --notes-file docs\releases\v0.3.33.md
```

Verify the branch SHA, peeled tag SHA, release status, asset digest, clean worktree, and emulator
status before reporting the phase as published.
