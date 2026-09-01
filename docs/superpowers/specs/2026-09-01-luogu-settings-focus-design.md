# Luogu settings focus correction / 洛谷设置定位修正

## Context / 背景

The Dashboard `CONNECT LUOGU` entry navigates to the existing Settings screen with
`focusLuogu = true`. The screen already measures the Luogu section and selects its
coordinates, but the scroll side effect currently guards execution with `focusOpenApp`.
As a result, the Luogu first-use entry can arrive at Settings without scrolling to the
intended Luogu panel.

Dashboard and the existing OpenApp workspace setup route must remain unchanged.

## Goal / 目标

When either supported focus route is active and both viewport and target coordinates are
available, Settings scrolls to the selected panel. Ordinary Settings navigation does not
scroll, and missing coordinates do not trigger a scroll attempt.

## Design / 设计

Extract a small pure predicate from the Compose side effect:

```kotlin
internal fun shouldScrollToFocusedSettingsSection(
    focusOpenApp: Boolean,
    focusLuogu: Boolean,
    viewportTop: Int?,
    targetRootY: Int?,
): Boolean
```

The predicate returns true only when `(focusOpenApp || focusLuogu)` is true and both
coordinates are non-null. `SettingsScreen` keeps its existing target selection and scroll
offset calculation, replacing only the guard with this predicate.

## Behavior matrix / 行为矩阵

| Route state | Coordinates | Scroll |
| --- | --- | --- |
| OpenApp focus | present | yes |
| Luogu focus | present | yes |
| no focus | present | no |
| either focus | missing | no |

## Boundaries / 边界

This is a local navigation correction only. It adds no network request, credential type,
main-site password, Cookie, Session, CSRF state, cloud service, cross-device sync, local
compiler, custom-input runner, or automatic POST retry. Existing bilingual resources and
all prior release notes remain intact.

## Verification / 验证

TDD will add the predicate test before production code. The focused test must first fail
because the predicate is absent, then pass after the minimal implementation. The complete
Gradle gate and Release emulator flow will verify that `CONNECT LUOGU` reaches the Luogu
settings panel while the emulator remains online.
