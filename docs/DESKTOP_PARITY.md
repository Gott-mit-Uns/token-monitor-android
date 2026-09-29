# Desktop parity for v0.63.1

The Android release is fixture-verified against desktop Token Monitor v0.63.1. It mirrors
the desktop information that the Hub can safely provide while keeping the phone
read-only and lightweight.

## Dashboard coverage

| Desktop surface | Android coverage |
| --- | --- |
| Home totals and period selection | Day, month, week, 7 days, 30 days, and total, with a configurable default |
| Limits | Provider windows, remaining or used bars, reset time, plan/source metadata, and hidden-by-default account email |
| Tools | Token and cost totals, proportional bars, cache hit, cache miss, output, and unclassified details |
| Status | Provider status, message, update time, and provider status-page link |
| Devices | Device totals, clients, models, collection cadence, last upload, and retained history |
| Models | Token or cost ranking, with the desktop vendor mark for recognized model families and a generic model mark for unknown names |
| Projects | Totals, session/tool counts, date range, and tool breakdown |
| Sessions | Reported conversation title, project, tool, model, start/update time, session ID, tokens, cost, Running/Finished/Idle activity, and recent context-window use; untitled sessions retain the client/model label |
| Usage dashboard | Overview cards, activity heatmap, and model/tool summaries |
| Trends | By tool or model, Bars or K-line, and 7/30/90-day, one-year, or all-history ranges |
| Display settings | View and Home-module visibility/order, ranking metric, limit-bar metric/source/email visibility, compact total, tool colors, default range, reduce motion, and a three-step text size in place of the desktop Zoom slider |
| Appearance | Interface theme with the desktop's Default, Obsidian, and Porcelain presets, desktop `TM1-…` theme codes pasted as-is, the Live indicator and Tool icons toggles, and a phone-only **Follow phone light and dark** switch: Porcelain by day, the chosen dark preset at night. Glass and Depth are desktop window effects with no Android equivalent |

All expandable rows, selectors, values, bars, and screen changes use short
interaction-driven motion. **Reduce motion** can follow Android, minimize motion,
or leave the app's animations enabled. There is no continuous decorative
animation.

Desktop interface colors are user-configurable, so a different selected palette
is not treated as a parity failure. Android keeps a touch-oriented native theme
and preserves the desktop app's translucent segmented controls with their
sliding selection, recessed panels, hierarchy, spacing, and semantic accents.
Time-based labels follow the desktop wording: limit windows count down with
`Reset 1h 59m`, `Expires 1h 59m`, or `Changes in 1h 59m` for a mixed boundary, and devices, providers, and limit accounts show ages such as
`Updated 5m ago`, refreshed on a slow clock between Hub events. Desktop
glass/backdrop, window opacity, zoom, title-bar layout, and desktop font
controls are window-specific rather than Hub dashboard features.

## Intentional boundaries

The following remain on desktop because they collect data, control a desktop
window, or require information the v0.63.1 Hub does not transmit:

- tool discovery, account sign-in, collection cadence, diagnostics, export, and
  subscription editing;
- Hub hosting/receiver setup, device deletion, tray/widget/bubble behavior,
  startup behavior, global shortcuts, Discord presence, and desktop updates;
- prompt and response transcript bodies, absolute project paths, local
  credential state, and collector logs;
- exact renderer-only attribution that is not present in the Hub response.
- live token-rate presentation. The v0.55.0 throughput capability and timed
  counters are parsed compatibly, but Android does not display a rate yet.
- the Edge Dock, floating AI bubble, macOS haptics, and their desktop-window
  behavior. Sessions remain a normal mobile dashboard destination.

v0.61.0 presentation parity includes canonical Xiaomi MiMo and Devin usage,
Cline and Devin limits, GitHub Copilot labeling, and the desktop provider names.
Those are display mappings over the existing Hub data, not new phone-side
collection.

v0.62.0 distinguishes Oh My Pi (`omp`) from Pi and shows TypeSafe's plan and
balance plus Devin's reported plan. Detailed TypeSafe token summaries and
Claude reset-grant explanations remain desktop-only for now.

v0.63.0 can attach locally resolved conversation titles to Cursor sessions.
Android displays a reported title in the Sessions list and otherwise keeps its
existing label. Titles may contain private text, so they are not used in widgets
or public examples. The v0.63.0 glass styles, background-image control, and
credential setup are desktop-only; collector fixes change the numbers supplied
by the Hub without adding phone-side collection.

v0.63.1 consolidates Cursor Auto under `cursor-auto` in usage and history and
includes reasoning tokens in the ZCode and OpenCode totals it reports. Android
displays those source values as received, without a second normalization or
token addition. Background scan, client-mode publish, and Electron renderer
performance changes remain desktop responsibilities.

The Android app does not replace these with remote commands. It reads only the
documented Hub endpoints listed in [`SECURITY.md`](SECURITY.md).

## Mobile additions

- Live streaming while a dashboard is visible, efficient 30-second widget Live refreshes, refresh on resume, and an offline snapshot.
- Tailscale-first address validation, with private LAN use behind an explicit
  opt-in and public targets rejected.
- An optional home Wi-Fi fallback address. The phone tries the address that
  answered last and moves to the other when it is silent, for snapshot reads
  and the live stream alike.
- Android Keystore-backed pairing storage, with the saved Hub address shown in
  Settings so the phone never has to guess which Hub it is paired to.
- Dense mobile navigation plus configurable view and Home-module order.
- System Back returns to Home from any view and from Settings to the view that
  opened it, instead of leaving the app.
- Pull down on any dashboard view to request a fresh snapshot.
- A light haptic tick on tab and view changes.
- An on-demand check for signed Android releases, with a Releases link and no
  background polling or silent installation.
- A four-page home-screen widget for Overview, Limits, Breakdown, and Activity.
  It changes pages locally inside one fixed 1.82:1 card, without adding a network
  request, timer, or background task.

When upstream changes, update the fixtures and compatibility adapter first.
See [`UPSTREAM_SYNC.md`](UPSTREAM_SYNC.md) for the repeatable process.
