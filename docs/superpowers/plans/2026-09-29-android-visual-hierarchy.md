# Android Global Visual Hierarchy Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Improve scanability across OJ NEXUS Android screens by strengthening shared typography hierarchy and making the command-palette row easier to tap.

**Architecture:** Keep visual rules in the existing design-system tokens and shared Compose components. Apply the shared styles at their component boundaries so existing screen consumers inherit the refresh without screen-specific layout or state changes.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Compose Foundation, existing `NexusTheme` and design-system tokens.

**Spec:** `docs/superpowers/specs/2026-09-29-android-visual-hierarchy-design.md`

## Global Constraints

- Dark first, single accent (NEXUS BLUE). One accent per theme.
- All color/spacing/typography/shape/motion tokens live in `core/designsystem`.
- Feature code must not call `Color(0xFF...)`, arbitrary `.dp`/`.sp` literals, or `RoundedCornerShape(...)` directly.
- Every UI string goes through `res/values/strings.xml`.
- State is never color-only (verdict tags always carry text).
- Preserve hairline dividers and section-led structure; do not add gradients, glow, or decorative hero panels.
- Do not change navigation destinations, business logic, stored state, or Apple/Windows clients.
- This is presentation-only; add no tests or runtime dependencies. Compile the Android debug app with `tools/gradlew-local.bat assembleDebug`.

## Review Focus

These are layout and accessibility review cases, not new behavior tests:

1. A top bar with a trailing action and a longer title must not overlap; constrain the title to one line with end ellipsis.
2. Chinese and English section labels must retain the uppercase/localized behavior and remain distinguishable from page titles.
3. Larger system font scales must not clip the 18sp screen title or section headings inside their rows.
4. The full command-palette row, including space away from the text, must be a button target at least 48dp high.
5. The taller command row must continue to respect the existing navigation-bar inset and selected-tab layout.

---

## File Map

- `app/src/main/java/com/ojnexus/core/designsystem/NexusTypography.kt` — shared screen-title and section-heading styles.
- `app/src/main/java/com/ojnexus/core/designsystem/NexusDimens.kt` — shared command-row height and existing spacing values.
- `app/src/main/java/com/ojnexus/core/designsystem/component/NexusTopBar.kt` — screen-title presentation and truncation.
- `app/src/main/java/com/ojnexus/core/designsystem/component/NexusSection.kt` — section-heading presentation.
- `app/src/main/java/com/ojnexus/core/designsystem/component/NexusMetric.kt` — supporting label style and label/value gap.
- `app/src/main/java/com/ojnexus/core/designsystem/component/NexusTag.kt` — compact tag vertical padding.
- `app/src/main/java/com/ojnexus/app/NexusBottomBar.kt` — full-row command-palette button target.

## Tasks

### Task 1: Establish the shared typography hierarchy

**Files:**
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/NexusTypography.kt`
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/component/NexusTopBar.kt`
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/component/NexusSection.kt`
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/component/NexusMetric.kt`

**Interfaces:**
- Produces: `NexusTypography.screenTitle: TextStyle`, 18sp semibold, 0.2sp letter spacing.
- Updates: `NexusTypography.sectionLabel` to 12sp semibold and 1sp letter spacing. Keep its callers uppercase via their existing string resources.

- [x] **Step 1: Add the screen-title style and update the section-label token**

In `NexusTypography.dark()`, add `screenTitle` at 18sp semibold with 0.2sp letter spacing. Set `sectionLabel` to 12sp semibold with 1sp letter spacing. Leave the existing `label`, `data`, `dataSmall`, `dataLarge`, and `displayData` values unchanged.

- [x] **Step 2: Apply the screen-title token in `NexusTopBar`**

Render the title using `NexusTheme.typography.screenTitle` and `textPrimary`. Set `maxLines = 1` and `TextOverflow.Ellipsis`; preserve the 48dp top-bar height, horizontal inset, trailing slot, and divider.

- [x] **Step 3: Apply the section-label token in `NexusSection`**

Keep its existing layout and trailing content. Change the heading color from `textTertiary` to `textSecondary` and use the updated `sectionLabel` token.

- [x] **Step 4: Separate metric labels from headings and values in `NexusMetric`**

Render metric labels with the existing `label` style and `textSecondary`. Change label-to-value spacing to `NexusSpacing.xxs` (4dp). Keep values and change indicators unchanged.

### Task 2: Improve common control rhythm and command-row reach

**Files:**
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/NexusDimens.kt`
- Modify: `app/src/main/java/com/ojnexus/core/designsystem/component/NexusTag.kt`
- Modify: `app/src/main/java/com/ojnexus/app/NexusBottomBar.kt`

**Interfaces:**
- Consumes: the `NexusSpacing.xxs = 4.dp` token already present in the design system; `NexusBottomBar` uses the updated `NexusTypography.sectionLabel` from Task 1.
- Produces: `NexusSize.commandBarHeight = 48.dp`; a full-width command-palette row with `Role.Button` semantics and the existing `onOpenCommandPalette` callback.

- [x] **Step 1: Give compact tags more vertical breathing room**

In `NexusTag`, change vertical padding from `NexusSpacing.xxxs` (2dp) to `NexusSpacing.xxs` (4dp). Keep horizontal padding, selected state, tone mapping, border, and text unchanged.

- [x] **Step 2: Set the command-row height token to 48dp**

In `NexusSize` in `NexusDimens.kt`, set `commandBarHeight` from 36dp to 48dp. Do not change the 60dp tab bar, navigation-bar padding, or tab touch targets.

- [x] **Step 3: Make the entire command row the button target**

In `NexusBottomBar`, move the existing command-palette click action from the `Text` to the full-width command-row container. Apply `clickable(role = Role.Button, onClick = onOpenCommandPalette)` before the row's horizontal content padding so the inset area remains inside the hit target. Keep the label aligned to the trailing edge and tinted with the accent. Remove the nested text click handler.

### Task 3: Compile the Android debug app

**Files:**
- No source changes; this is the final compilation gate.

- [x] **Step 1: Run the repository-local debug build**

Run: `tools/gradlew-local.bat assembleDebug`

Expected: final Gradle output is `BUILD SUCCESSFUL`. If compilation fails, fix only issues caused by these planned shared-component changes and rerun the build.

## Completion Review

- Compare the source diff with every acceptance item in the spec.
- Confirm only the seven files in the file map changed for implementation.
- Confirm no new strings, colors, gradients, cards, dependencies, or behavior changes were introduced.
- Confirm `git diff --check` is clean and record the final build result.
