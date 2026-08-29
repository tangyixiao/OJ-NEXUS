# OJ NEXUS — Design System

Implemented in `core/designsystem`. Dark first; v0.1 ships exactly one theme (NEXUS BLUE).

## Tokens

Access via `NexusTheme.colors` / `NexusTheme.typography` inside composition. Feature code never
declares raw colors, sizes, or shapes. The Material 3 scheme is derived from the same tokens so
stray M3 components stay consistent.

### Color (`NexusColors`)

| Token | Value | Use |
| --- | --- | --- |
| background | #090B0D | app background |
| surface | #101317 | panels, bars |
| surfaceElevated | #161B21 | raised fills, empty chart tracks |
| border | #262B31 | hairlines, dividers |
| borderStrong | #39414B | emphasized borders |
| textPrimary | #ECEFF1 | primary copy |
| textSecondary | #9AA3AC | supporting copy |
| textTertiary | #5F6871 | labels, disabled, faint data |
| accent | #4FA1FF | THE accent (NEXUS BLUE) |
| onAccent / accentContainer | #05101E / #14273D | accent fills |
| success / danger / warning (+ containers) | #3ECF8E / #E5484D / #E3B341 | status only |

Semantic states map through `NexusTone` (Accent, Success, Danger, Warning, Neutral) — components
take tones, not colors. Status is never color-only: every tone is paired with a text label.

### Typography (`NexusTypography`)

Sans (default) for copy; `FontFamily.Monospace` for all data (rating, timers, codes, verdicts).

- `displayData` 28sp mono — hero numbers (player-card rating)
- `dataLarge` 20sp mono — countdowns, metric values
- `data` 14sp mono — default data
- `dataSmall` 12sp mono — table cells, tags, statuses
- `sectionLabel` 11sp, +1.4sp tracking — uppercase section headers (strings arrive uppercase)
- `title` 16sp — panel titles
- `body` 14sp/20sp — body copy
- `label` 12sp — small supporting copy

### Spacing / Radius / Fixed sizes (`NexusSpacing`, `NexusRadius`, `NexusSize`)

- Spacing: 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 dp (`xxxs…xxl`), screen gutter = 16.
- Radius: 4 / 6 / 8 / 12. Nothing larger; hierarchy comes from lines and type.
- Fixed: top bar 48dp, bottom bar 60dp, table rows 44dp, divider 1dp, status dot 6dp.

### Motion (`NexusMotion`)

Fast 120ms / Normal 200ms / Slow 300ms with standard/exit easings. Meaningful transitions only;
no looping or idle animation; reduce-motion switch planned (Settings).

## Components

| Component | Role |
| --- | --- |
| `NexusTopBar` | flat 48dp bar: uppercase label + trailing slot + hairline |
| `NexusSection` | labeled content block — the structural unit of screens (not cards) |
| `NexusDivider` | 1dp hairline, optional end inset for list interiors |
| `NexusMetric` | label + mono value + colored delta |
| `NexusStatus` | dot + explicit label (sync/state rows) |
| `NexusTag` | bordered compact chip: verdicts, judges, filters |

Screen-specific table columns/chart sizes live as named file-level constants in the feature
file — the documented escape hatch; arbitrary inline dp is not allowed.

## Visual Bans

Emoji, gradients, glassmorphism, glow, particle/idle animation, large hero banners, rounded
"card soup", marketing copy, "AI recommends" phrasing.
