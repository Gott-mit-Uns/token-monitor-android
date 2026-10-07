# Pages widget specification

This is the measured contract for the four-page widget (Overview, Limits,
Breakdown, Activity). The renderer implements these numbers; nothing in the
renderer positions content by any other means. When the design needs to change,
change this file first, then make the renderer match it, then recapture the
gallery images. Do not nudge coordinates in code without updating this page.

The reference images are the four concept cards approved on September 21, 2026.
They were measured at 1536×1024 pixels; the card in each is 1382 pixels wide, so
one reference unit below is 1382 / 364 ≈ 3.8 image pixels.

## Card and coordinate system

- The Pages widget is fixed-layout, not responsive. It requests a 4×2 launcher
  placement and never switches to a compact, wide, portrait, or detailed
  composition. A launcher may allocate different physical dimensions or expose
  resize handles; that only changes how this one card is fitted.
- The card is always 1.82:1. The launcher allocation is fitted to that ratio and
  the card is centered in it.
- Every coordinate in this document is in **reference units**: a card that is
  364 units wide and 200 units tall. The renderer scales the whole drawing
  uniformly by `frame width / 364`, so a 393 dp card on the phone draws every
  number below at 1.08 dp per unit, and a 320 dp card at 0.88 dp per unit.
- There is no separate compact layout. A smaller test or launcher allocation is
  the same card drawn smaller. The launcher minimum (250×110 dp, card 200×110 dp)
  scales to 0.55 dp per unit and is the same picture.
- Bitmaps are rendered at the launcher's display density with a 1200-pixel
  width cap, then handed to one `ImageView`. Charts, bars and the heatmap are
  drawn straight onto that canvas, never as separate bitmaps that get scaled.
- Content inset: 18 units on the left and right. Content spans x = 18 … 346.
- Corner radius 20 units. Card fill is the palette gradient with the two
  radial glows; edge stroke 1.35 units in the strong line color mixed with the
  palette blue (dark themes) or the strong line alone (light themes).

## Type

Two families, loaded from `res/font`:

| Role | Family | Weight | Size (units) | Case and tracking | Color |
| --- | --- | --- | --- | --- | --- |
| Brand | JetBrains Mono | Bold | 13.5 | Uppercase, +0.04 em | ink |
| Page | JetBrains Mono | Regular | 9 | Uppercase, +0.06 em | muted |
| Section | JetBrains Mono | Bold | 10.5 | Uppercase, +0.06 em | label |
| Status | JetBrains Mono | Bold | 10.5 | Uppercase, +0.04 em | accent when Live, else muted |
| Display | system sans-serif | Bold, tabular | 44 (fits down to 26) | — | ink |
| Figure | system sans-serif | Bold, tabular | 20 | — | ink |
| Stat | system sans-serif | Bold, tabular | 13 | — | ink |
| Body | JetBrains Mono | Regular | 10 | — | ink |
| Body strong | JetBrains Mono | Bold | 10 | — | ink |
| Legend | JetBrains Mono | Bold | 9.5 | — | ink |
| Secondary | JetBrains Mono | Regular | 9 | — | muted |
| Caption | JetBrains Mono | Regular | 7.5 | Uppercase, +0.03 em | muted |
| Axis | JetBrains Mono | Regular | 7 | Uppercase | muted |

These sizes are the concept's measured sizes plus about five percent. JetBrains
Mono advances 0.6 em per character, so a 10-unit body character is 6 units wide;
the column rules below are derived from that.

"label" is the muted color pulled 30 percent toward ink so section titles stay
legible on a launcher without reading as primary text. "muted" is the palette
muted color unchanged. Tabular figures come from the `tnum` feature.

Text never shrinks to fit except the Overview display total. Everything else is
ellipsized at its column width. Token figures in rows use one decimal below
100K and below 100M and none above ("764K", "2.5M", "199M") so number columns
stay narrow.

## Palette roles

All colors come from the dashboard `Palette` for the active theme.

- ink, muted, label: text as above.
- accent: Live status, the Live toggle, the current-day bar and active page dot.
- success, orange, danger: quota bar fill at more than 35, 15–35, and at most
  15 percent remaining.
- Vendor colors (`originalToolColor`): tool and model marks, breakdown bars,
  the Overview segmented bar and legend dots, and the Claude share on the
  seven-day chart. Unknown vendors fall back to blue, orange, purple, yellow in
  row order.
- chart: seven-day bars for days other than today. TM2 may supply a custom
  chart color; TM1 and unchanged presets retain their existing blue bars.
- heat ramp: the five palette heat steps, index 0 for an inactive day. A custom
  TM2 chart color supplies the four active steps with successive white tints;
  otherwise the established theme ramp is retained.
- line and strongLine: rules, dividers, bar tracks, inactive page dots.

## Header (all pages)

| Element | Position (units) |
| --- | --- |
| App icon | 22×22 at (18, 20), centered on the brand and page lines |
| Brand "TOKEN MONITOR" | x 48, baseline 30 |
| Page "OVERVIEW · 1/4" | x 48, baseline 43 |
| Refresh icon | 16×16 at (238, 20) |
| Status text | right edge 296, baseline 31 |
| Live toggle | 40×22 at (306, 16), radius 11 |
| Toggle knob | radius 8, center 11 units from the active end |
| Left chevron | center (9, 100), 6 wide, 10 tall |
| Right chevron | center (355, 100) |
| Page dots | y 190, 4 dots, 7 units apart, centered; active radius 2, others 1.5 |

The toggle track is accent at 18 percent over the shell with an accent stroke
when Live; otherwise the overlay color with a strong line stroke and a muted
knob.

Touch targets are separate transparent views in `usage_widget_deck.xml` and are
not drawn: 48 dp edge columns for page changes, 48×48 dp for Refresh 96 dp from
the right edge, and 72×48 dp for Live at the right edge.

## Overview

| Element | Position (units) |
| --- | --- |
| "TOTAL TOKENS" section | x 18, baseline 66 |
| Total (display) | x 18, baseline 104, fits within x 18 … 232 |
| "$1.67 estimated cost" (body, muted) | x 18, baseline 124 |
| Vertical divider | x 243, y 54 … 128, line color |
| Stats column | x 257; three rows at 26-unit pitch starting at row top 50 |
| Stat value (stat) | baseline row top + 12 |
| Stat caption | baseline row top + 22 |
| Segmented tool bar | x 18 … 346, y 135 … 141, radius 3 |
| Legend | three equal slots from x 18; dot radius 3 at slot x + 4, y 154 in the vendor color; text (legend, ink) at slot x + 13, baseline 158 |
| Rule | y 167, x 18 … 346 |
| "THIS WEEK" caption | x 18, baseline 184 |
| Week tokens (stat) | x 88, baseline 184 |
| "· $15.68" (body, muted) | follows the week tokens with one space |

Stats are, in order, messages today, active time today, and the streak; the
seven-day total fills a missing row. The legend names use the tool display
name; the segmented bar uses the same order and colors as the legend.

## Limits

Rows are providers, not windows. The provider with the tightest window comes
first; each row shows that provider's two tightest windows, tightest on the
left. At most two providers.

| Element | Position (units) |
| --- | --- |
| Row 1 top | 52 |
| Row 2 top | 122 |
| Horizontal rule between rows | y 117.5, x 18 … 346 |
| Provider mark | 22×22 at (18, row top + 10), centered on the title and percentage lines, vendor color |
| Left cell text x | 46 |
| Right cell text x | 197 |
| Left cell right edge | 162 |
| Right cell right edge | 346 |
| Title "CODEX · WEEKLY" (bold mono 10, uppercase, +0.04 em, label color) | baseline row top + 14 |
| Percent (figure) plus " left" (regular mono 13, ink) | baseline row top + 36 |
| Bar | y row top + 42 … row top + 46.5, radius 2.25, track strong line |
| Reset (secondary) | baseline row top + 57 |

The title is "Provider · Window" when that fits the cell, otherwise the window
name alone. The percent takes the ink color above 35 percent remaining and the
risk tone below. One provider draws one row. No limits draws the "no limits"
empty state.

## Breakdown

When both reported lists contain at most one row, the page uses the sparse
feature layout below. This is the common installed state and must not look like
an almost-empty dense table.

| Sparse element | Position (units) |
| --- | --- |
| Tool identity mark | 18×18 at (18, 72) |
| Tool identity | x 42, baseline 87, body strong, ellipsized at x 168 |
| Model identity mark | 18×18 at (192, 72) |
| Model identity | x 216, baseline 87, body strong, ellipsized at x 346 |
| Token figure | column left edge, baseline 118, figure type |
| Share figure | column right edge, baseline 118, figure type, right aligned |
| "TOKENS" caption | column left edge, baseline 132 |
| "SHARE" caption | column right edge, baseline 132, right aligned |
| Share bar | full column width, y 142 … 148, radius 3 |
| Tool cost | x 18, baseline 163, secondary type |

The token and share figures use opposite edges of the same column, so neither
can read as part of the other. Both halves use identical baselines and type.

When either list contains more than one row, use the dense table:

| Element | Position (units) |
| --- | --- |
| Column divider | x 181, y 54 … 186, line color |
| "TOOLS" section | x 18, baseline 60 |
| "MODELS" section | x 192, baseline 60 |
| Rows, both columns | up to 3, tops at 68, 108, 148 |
| Mark | 16×16 at (column left, row top + 4), centered on the two text lines; tools column left 18, models column left 192 |
| Name (body) | x 40 for tools, x 214 for models, baseline row top + 10, ellipsized 8 units before the share |
| Share (body strong) | right edge 168 for tools, 346 for models, baseline row top + 10 |
| Detail "2.5M · $1.18" (secondary) | same x as the name, baseline row top + 24; the cost is omitted when it is not reported |
| Bar | column left … column right, y row top + 29 … row top + 33, radius 2 |

Both columns use the same two-line row: identity and share on the first line,
tokens and cost on the second. The concept put tokens and share on one line,
but JetBrains Mono is wider than the concept's face and the phone showed that
line crowded; a second line fourteen units below the first keeps the name's
descenders clear of the figures, and the row sits high in its slot so the air
is under the bar, not above the name. Three rows per column keep the rows aligned
across the divider; a fourth model is not shown.

Shares under one percent print as "<1%". Bars use the row's vendor color.
Fewer rows leave the remaining space empty; row pitch never changes.

## Activity

| Element | Position (units) |
| --- | --- |
| Column divider | x 214, y 54 … 186 |
| "7 DAYS" section | x 18, baseline 60 |
| Week tokens (stat) | x 18, baseline 79 |
| "TOKENS · $15.68" (secondary) | follows the week tokens with one space |
| "PEAK 6.1M" (caption) | right edge 204, baseline 79 |
| Chart plot | x 38 … 204, y 92 … 168 |
| Axis labels (axis) | x 18, at the 100, 67, 33 percent gridlines and 0 at the floor |
| Gridlines | dashed, line color, at 33, 67 and 100 percent of the peak |
| Floor | solid strong line at y 168 |
| Bars | 7 columns, bar width 56 percent of the column, radius 2; chart color with the Claude share stacked in orange; today in accent |
| Day labels (caption) | centered under each bar, baseline 179 |
| "ACTIVITY" section | x 226, baseline 60 |
| Heatmap | x 226 … 346, y 68 … 132; 13 week columns, 7 rows Sunday first, 2-unit gaps, radius 1.5 |
| Month labels (caption) | baseline 144 at the column where each month starts |
| Rule | y 152, x 226 … 346 |
| Active days (stat) | x 226, baseline 172 |
| "ACTIVE DAYS" caption, or "DAYS" when it does not fit | x 226, baseline 184 |
| Stat divider | x 286, y 158 … 186 |
| Messages today (stat), or today's cost when the Hub reports no messages | x 292, baseline 172 |
| "MESSAGES TODAY" caption, or "MESSAGES" when it does not fit; "COST TODAY" or "COST" for the cost fallback | x 292, baseline 184 |

A day with no observation draws no bar and a dash-length stub in the strong
line color. The axis top is three times the clean step nearest above a third of
the seven-day peak, so the three gridlines carry round labels. Month labels are
drawn only at columns that contain the first day of a month.

## Empty and non-ideal states

- No snapshot: header plus the centered empty message in body muted text,
  wrapped to the content width.
- Missing limits, breakdown or history: the same centered message for that page.
- Saved, Stale, Offline, Connecting, Updating: only the status text and toggle
  change. Figures stay.

## Verification

- `WidgetDeckGridTest` (JVM) checks the scale rule, bitmap cap, and fixed bands.
- `WidgetDeckDesignTest` (instrumented) renders all four pages from the dense
  showcase fixture, which has three tools, four models, four quota windows and
  65 days of history, at 250×110, 320×180 and 360×220 dp in Default and
  Porcelain and a custom TM2 chart theme. The Default 360×220 renders supply
  the gallery images; custom-theme checks do not replace the approved gallery.
- Before any release, the four gallery images are placed beside the concept
  cards at phone size and approved by the owner. Emulator captures alone do
  not pass the visual gate.
