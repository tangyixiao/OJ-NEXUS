# AGENTS.md — OJ NEXUS Project Rules

Rules for any agent or contributor working on this repository. These are long-term rules;
they outlive individual features.

## Identity

OJ NEXUS is a native Android competitive-programming command center (Codeforces, AtCoder,
Luogu, more later). It is a tool, not a social app and not an AI product. UI tone is English,
uppercase, telemetry-style; marketing copy is forbidden.

## Tech Stack (do not change without strong cause)

- Kotlin (AGP built-in Kotlin; the standalone `org.jetbrains.kotlin.android` plugin must NOT be applied)
- Jetpack Compose + Material 3 (via `androidx.compose` BOM)
- Navigation Compose
- Room, DataStore, WorkManager (later phases)
- Coroutines / Flow / StateFlow
- Retrofit + OkHttp + kotlinx.serialization (later phases)
- Hilt (later phases)
- JUnit; MockK/Turbine only when genuinely needed

No Flutter / React Native / Electron / WebView shells. Prefer AndroidX/Kotlin official
libraries over third-party.

## Environment

- JDK: Android Studio JBR (`D:/Android Studio/jbr`), pinned machine-locally via the USER-level
  `~/.gradle/gradle.properties` (`org.gradle.java.home`). The repository `gradle.properties`
  stays CI-safe — never add machine paths to it. No standalone JDK on this machine.
- Android SDK: `D:\Android` (`local.properties`, not committed).
- Because `services.gradle.org` downloads fail on this network, `gradle-wrapper.properties`
  points at the Tencent mirror of the official distribution. On other networks switch the
  `distributionUrl` back to `https://services.gradle.org/distributions/...` if preferred.
- `tools/gradlew-local.bat` is a machine-local helper that sets JAVA_HOME; it is not committed.

## Build & Test

```
.\gradlew.bat assembleDebug     # every stage must end with BUILD SUCCESSFUL
.\gradlew.bat test              # unit tests
```

Never skip compilation, never fake results, never mock data pretending to be real API output.

## Architecture

- Single `app` module + clean packages until real modularization is justified:
  `app/` shell, `core/designsystem`, `core/model`, `core/ui`, `core/sample`, `feature/*`.
- Layers: UI (Compose) → ViewModel (later) → Repository/UseCase → Data source (Room/Network).
- Composables never touch the network or complex business logic.
- UI state: `StateFlow`; lifecycle-safe collection; handle Loading / Success / Empty / Error / Offline.
- Local First: the app stays usable offline; network only syncs.
- Multi-OJ: all judge-specific logic lives behind the `JudgeAdapter` boundary (`judge/<judge>/`).
  Domain models are judge-agnostic; network DTOs never leak into core.

## Design System

- All color/spacing/typography/shape/motion tokens live in `core/designsystem`.
- Feature code must not call `Color(0xFF...)`, arbitrary `.dp`/`.sp` literals, or
  `RoundedCornerShape(...)` directly. Named file-level layout constants are the allowed
  escape hatch for screen-specific table columns and chart sizes.
- Dark first, single accent (NEXUS BLUE). One accent per theme.
- Restrained radii (4–12dp), hairline dividers and sections over cards.
- Forbidden: emoji icons, purple-blue gradients, glassmorphism, glow, particle/looping
  animations, "AI recommends" copy, chat entries, marketing text.
- Animation: 120–300ms, meaningful only, reduce-motion must remain possible.

## Strings & A11y

- Every UI string goes through `res/values/strings.xml`. Problem titles, judge names, tag
  names, and handles are data, not UI copy — they may live in code/sample data.
- State is never color-only (verdict tags always carry text).
- Meaningful content descriptions; adequate touch targets; font scaling must not break layouts.

## Security

- Never ask users for OJ passwords; never store plaintext credentials.
- No cookies/sessions in v0.x. Keystore-protected experimental features only, if ever.
- Never commit or log: passwords, tokens, cookies, keystores, `local.properties`.

## Git

- Default branch: `main`. Clear commit messages (`feat:`, `fix:`, `docs:`, `test:`, `build:`,
  `chore:`), one logical change per commit.
- Before any push: check `git status` / `git diff` for secrets. Never force push.
- CI (GitHub Actions) runs `./gradlew test` and `./gradlew assembleDebug`.

## Definition of Done

Code complete, no TODO stubs, compiles (`BUILD SUCCESSFUL`), core logic unit-tested,
no crashes, Loading/Empty/Error/Success handled, UI matches the design system, no obvious
performance problems. Only then say DONE.
