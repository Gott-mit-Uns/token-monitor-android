# Compact UI Guidelines — Token Monitor Android

Applies to the current compact layout; reviewed 2026-10-17 against governance base cfab543. This document defines **semantic** layout guidance, not mandatory fixed heights. Current execution evidence and limitations: [VALIDATION](VALIDATION.md) and [HANDOFF](HANDOFF.md).

## Purpose
Align the Trends page's information density with the already compact Home and Settings pages, without changing Hub data, chart meaning, accessibility or established phone layouts.

## Candidate dimensions (normal text size)
| Token | Starting point | Application |
|---|---:|---|
| ChartHeightPrimary | 160 dp | Main bar/line trend chart, adjustable for axes/legend |
| ChartHeightSecondary | 120 dp | Simpler auxiliary charts |
| DetailRowMinHeight | 36 dp | Display-only, noninteractive data row |
| InteractiveMinHeight | 48 dp | Touch targets and clickable controls |
| SectionSpacing | 8 dp | Between related sections |
| CardContentPadding | 8–12 dp | Inside cards |
| ChartTitleSpacing | 4 dp | Title to chart |

Values must be refined through **real Compose measurement**, existing UI tests and screenshots; they are not unconditional exact values. Actual natural text height, non-linear Android font scaling, legends, axis ticks and content may increase heights. Display-only rows may be compact; their actions must retain accessible touch targets.

## Layout scope
- Apply compact design tokens to Trends charts, overview metric cards and data-like rows.
- Review Home and Settings for consistency **without changing already-validated geometry** unless a targeted regression test proves a defect.
- Keep chart geometry meaningful (bar heights, zero markers, axes, dates, labels), no global Canvas scaling shortcut or truncation to force a fixed size.
- Preserve 320/393/411 dp, dark/light, zh/en, system font scale 1.0/1.3/2.0, and reduced-motion behavior.
- Preserve page scroll, refresh, navigation, click, accessibility semantics, and one foreground SSE connection.

## Resolved initial finding
Before V5, `DesktopTrendsMain.kt` derived spark height as `maxOf(48.dp + padding, viewport - fixedHeight)`. V5 removes this viewport growth target and applies `CompactUiTokens.chartHeight()`. Independent Dashboard no longer fills the remaining viewport either. Real Android sp conversion adds room for enlarged axes; metric paragraphs retain natural height. Native measured date ticks preserve first/last dates and skip overlapping intermediate labels without removing any data points. All five numeric ticks remain visible and unclipped.

`DetailRowMinHeight` applies to display-only metrics, not natural-height legend captions. Legends retain their small existing rows, wrap long names and scroll above the secondary120dp cap. Clickable record rows use the48dp interactive minimum. Home and Settings retain their existing verified geometry.

## Acceptance evidence
Record old/new main and secondary chart heights and metric row heights, screenshots from identical fixtures, readable axis labels, 48dp touch targets, full/empty data, window/font/locale matrices, scrolling, and non-regression. Distinguish simulation from physical-device evidence. Results belong only in VALIDATION; release requires its own authorization.

## Settings dropdown exception (user approved 2026-10-13)

All ordinary setting-value dropdowns use the shared `DesktopSettingSelect` → `DesktopOptionMenu(compactSettings=true)` path. Expanded option rows have a 36dp minimum height, 4dp vertical padding per edge, zero row gap and 4dp menu outer padding. Actual height is max(36dp, natural text height + 8dp); each padding edge is rounded separately in pixel geometry. Long labels wrap and large system/App text may grow individual rows without shrinking, clipping or ellipsis. Existing 11-level typography, horizontal geometry, selected background and icons remain.

This is an explicit user-approved exception to the general 48dp interactive minimum for popup option rows. Adjacent targets must not overlap. The settings row and collapsed dropdown control retain their existing 48dp targets. Navigation, period and business menus keep their existing geometry and typography. Shared popup anchoring, visible screen/IME bounds, height cap, scrolling, dismissal, keyboard navigation and selected/accessibility semantics are preserved. No search, grouping or extra motion is added.

Gate: `CompactSettingsDropdownScreenTest` checks real popup geometry, selected semantics, exact adjacent touch edges, complete natural text, long-list scroll and keyboard/outside dismissal; capture both themes at narrow/ordinary widths and standard/large system font. Added tests are not executed evidence until the combined release coordinator runs them. Evidence and APK fingerprints belong in VALIDATION, with the actual source and execution scope.

## Component skin additions (2026-10-20)

The user-approved UI pass keeps all existing geometry and touch minimums, and adds:

- **Default interface face**: an unset interface font resolves to the desktop monospace face; Chinese still uses its CJK fallback and numerals keep the data font. "Token UI", "Token Data" and system faces remain explicit choices. Previously saved custom faces still render, but no custom-font input or menu option is exposed; see [upstream exclusions](UPSTREAM_SYNC.md#android-持续排除项).
- **Dialogs**: shared `DesktopAlertDialog` uses a 12dp shape, the panel surface and a 1dp hairline; no Material tonal elevation.
- **Filter pills**: shared `DesktopFilterChip` replaces Material `FilterChip` in records, device detail and chart modes. It keeps a 48dp touch target, a 6dp visible radius and a 12% selected overlay.
- **Press feedback**: flat rows and segments use a short 4.5% overlay tint (`desktopPressable`) rather than a 48dp splash; Material surfaces that retain a ripple must clip it to their visible shape.
- **Module headers** keep the 48dp module button and add a 9dp chevron so the detail affordance is visible without hover.

These are semantic rules, not fixed pixel heights; large-font and narrow-width behaviour still follows the measurement rules above.
