# Android Global Visual Hierarchy Refresh

## Goal

Make OJ NEXUS Android screens easier to scan by giving page titles, section headings, supporting labels, and numeric data distinct visual weight. Keep the existing dark telemetry identity and make the common command-palette action easier to tap.

## Context

The app already has a shared design system and reusable components. Several hierarchy levels currently share the same 11sp uppercase `sectionLabel` style, including page titles and section headers. Section headers also use the faintest text token. The bottom command row is 36dp high and its text alone is clickable, so the action is visually and physically small.

## Design

- Add a dedicated 18sp semibold screen-title typography token and use it in `NexusTopBar` with the primary text tone. Keep the top bar at 48dp; titles stay on one line and use end ellipsis when space is constrained by a trailing control.
- Set section headings to 12sp semibold with 1sp letter spacing and the secondary text tone. Keep them uppercase and restrained.
- Keep numeric values monospace and preserve the existing scale between prominent values and dense table data. Render metric labels in the existing 12sp `label` style with secondary text tone, and set label-to-value spacing to the existing 4dp spacing token.
- Increase compact tag vertical padding from 2dp to 4dp. Keep status dot size and its existing 4dp text gap; this pass should not make status indicators decorative or color-only.
- Keep the existing backgrounds, NEXUS BLUE accent, semantic status colors, hairline separators, and restrained corner radii. Do not introduce gradients, glow, decorative hero panels, or additional accent colors.
- Preserve the section-based layout. Do not wrap every block in a card or rearrange screen content as part of this shared-component pass.
- Give the command-palette action a full-width row at least 48dp high, make that row clickable, and expose it as a button while keeping its compact text treatment aligned to the trailing edge.
- Keep tag and status meanings explicit in text. Use the current tone tokens and avoid color-only state changes.

## Scope

Update shared Android design tokens and the relevant shared components: `NexusTypography`, `NexusSpacing`, `NexusTopBar`, `NexusSection`, `NexusMetric`, `NexusTag`, and `NexusBottomBar`. `NexusStatus` keeps its current size and text-plus-tone treatment. The visual update should flow through existing component consumers without screen-specific data or navigation changes. Do not alter Apple, Windows, network, storage, or business-logic code.

No new UI copy is expected. Existing English uppercase labels and Chinese translations remain intact. Any accessibility description remains localized through string resources.

## Accessibility and layout

- The command-palette action's full 48dp-high row must be clickable and exposed as a button.
- Screen titles and section labels must remain readable with system font scaling and longer localized labels; use existing wrapping or ellipsis behavior where a row cannot expand.
- Maintain visible text alongside tone colors for statuses and verdicts.
- Introduce no new animations. Existing animations remain unchanged and continue to respect the current reduce-motion setting.

## Behavior and state

This is a presentation-only change. Loading, success, empty, error, offline, synchronization, navigation, and stored data behavior remain unchanged.

## Acceptance

- A user can distinguish the current screen title from section labels at a glance.
- Section headings are readable against the dark background but remain subordinate to content and values.
- Numeric data retains the monospace telemetry treatment and existing semantic status colors.
- Shared sections remain line- and type-led rather than becoming a collection of cards.
- The command-palette action has at least a 48dp interactive area without changing its destination or behavior.
- The Android debug app compiles successfully with the repository's pinned local Gradle helper.
