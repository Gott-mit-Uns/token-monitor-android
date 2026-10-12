# Compact UI Guidelines — Token Monitor Android

Status: V5 candidate specification (not yet implemented or accepted). This document defines **semantic** layout guidance, not mandatory fixed heights.

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

## Scope for V5
- Apply compact design tokens to Trends charts, overview metric cards and data-like rows.
- Review Home and Settings for consistency **without changing already-validated geometry** unless a targeted regression test proves a defect.
- Keep chart geometry meaningful (bar heights, zero markers, axes, dates, labels), no global Canvas scaling shortcut or truncation to force a fixed size.
- Preserve 320/393/411 dp, dark/light, zh/en, system font scale 1.0/1.3/2.0, and reduced-motion behavior.
- Preserve page scroll, refresh, navigation, click, accessibility semantics, and one foreground SSE connection.

## Known initial finding
`DesktopTrendsMain.kt` currently derives spark height as `maxOf(48.dp + padding, viewport - fixedHeight)`, where `viewport` is based on `availableHeight`. This makes the chart fill the leftover viewport on tall screens. Refactor toward an appropriately bounded *preferred* chart height while keeping content's natural height and existing interaction semantics. Verify callers, constraints and tests before changing product code.

## Acceptance evidence
Record old/new main and secondary chart heights and metric row heights, screenshots from identical fixtures, readable axis labels, 48dp touch targets, full/empty data, window/font/locale matrices, scrolling, and non-regression. Distinguish simulation from physical-device evidence. Do not claim V5 complete until Hub, performance, networking and UI checks all meet their individual gates; release only once.
