# Native Android Widget realignment

This document covers the Widget portion of the unified candidate. The release coordinator supplies the final version, commit, APK fingerprint and executed test receipts. Added tests and static conclusions below are not proof that any emulator or Xiaomi test has passed.

The user explicitly selected the Mac system desktop Widget added through Edit Widgets. Edge Dock, mini windows and main-window cards are separate surfaces. The installed Mac Widget's matching revision is `ac4eeb2c4aea239dd94b67f074c73f69aafc46f8`. The focused REA investigation, binary identity, accepted calls and live screenshot evidence live under `evidence/widget-settings-063/rea-native/` and `evidence/widget-settings-063/mac/`. Revision-matched Swift and JavaScript are supporting source evidence, not observations of live behavior.

## Information and native adaptation

| Item | Mac source / REA evidence | Android implementation | Limits / verification |
|---|---|---|---|
| Actual kinds | Dashboard large; Summary small; Activity, Breakdown, Quota medium | Existing HubWidgetProvider offers Summary, Dashboard, Activity, Tools and Models; existing HubQuotaWidgetProvider offers Quota | Two registered provider identities retained for placed-widget upgrade continuity |
| Dashboard | Metric and trend across top, quota across middle, activity / breakdown in lower columns | Real RemoteViews compact dashboard uses same structure at width >= 280dp, height >= 320dp, font scale < 1.5 | Needs real launcher measurements; extreme fonts/narrow sizes retain core values and explicitly request more space |
| Periods | day / month / total | today / natural month / allTime per instance | Never uses the App's last30 month preference as a Widget month |
| Numbers | Compact western or localized units | K/M/B or Chinese 万/亿 according to existing App compact-units and language choices | Exact full integer is accessible; unknown is an em dash, explicit zero remains zero |
| Usage cost | USD multiplied by configured rate, symbol + two decimal places | Same rate/currency projection; partial pricing is marked `+ ?` | Provider quota/balance preserves its own currency, independently of usage currency |
| Trend | Today seven local dates, month first date through today; total monthly. History/live use maximum, not addition | Same local-calendar range and maximum merges, from encrypted full history / snapshot preview | Missing Hub observations remain gaps instead of inventing zero; complete same-input fixtures can be compared directly |
| Breakdown | Tools/models ranked by tokens; share denominator is the dimension sum | Top four rows, vendor assets in standalone modes, percentage bars in dashboard | Cached Hub model-name aliases are reused; unsafe URL/path/email labels are omitted rather than exposed |
| Activity | Last 182 daily observations; large 16 weeks / medium 26 weeks; peak-relative four intensities | Raster chart inside RemoteViews, same coverage/intensities; missing days marked with a diagonal | Tap opens real Android Trends; individual tiny heatmap-cell intents are replaced by app inspection |
| Quota choice | Automatic ranking by data availability / status / freshness; custom at most two | Per-instance automatic or up to two stable hashed account identities | Missing custom identity stays missing; never substitutes another account. Emails always masked |
| Device attribution | Mac Widget configuration has no device scope | Usage is labeled all Hub collection devices; quota belongs to provider accounts | No phone-local collection, no per-device scope invented |
| Freshness | Shared snapshot file; 15-minute WidgetKit timeline, 20-minute stale threshold; refresh intent reloads timeline | Only authenticated encrypted Hub cache, 30-minute Android system refresh + App cache/preference events | Android background does not contact Hub. Cache age/source stale remain visible; tap enters foreground App update |
| Themes | Mac bundles request dark scheme, dark background + muted accent gradient | Dark palette aligns to #090B0E / #F3FBF7 / #A3ADBB; native light adaptation supported | Android launcher tint/rendering may differ from Mac desktop tint |

## Compatibility and lifecycle

Package name, signatures, `.HubWidgetProvider` and `.HubQuotaWidgetProvider` remain unchanged. Existing instances without new metadata default to Today Summary or automatic Quota. A separate metadata file stores only mode, period, dimension and hashed account identities; encrypted business-cache format is unchanged. Canceling launcher configuration does not write metadata; saving returns the original widget id. Deletion cleans metadata, id restoration snapshots all prior selections before moving them, and boot / package replacement perform an asynchronous cache-only redraw.

Open and configuration PendingIntents include instance id plus unique action/data URI; mode and period cannot overwrite another instance's action. Summary/dashboard open Home; tools/models open their dimension; quota opens Limits; activity opens Trends. Direct routes can open a hidden view. Month deep links keep natural-month semantics without changing the user's persisted month preference. The App maintains its existing foreground-only SSE and network lifecycle.

No foreground service, new worker, high-frequency alarm, local Agent or background Hub connection is introduced. Re-rendering cannot repair old business data while the App remains closed. The Android action therefore explains that opening the App updates data. Mac WidgetKit numeric transitions and per-cell AppIntents are not promised on Android RemoteViews.

Model display aliases are applied from the existing encrypted Hub cache before Widget-specific NFKC / case-insensitive display-name grouping, matching the Mac's display-name projection order. Each original counter contributes once. Normalized groups that exceed signed 64-bit counter capacity retain a visible unavailable value and unavailable shares; they are never wrapped, clamped, dropped or shown as zero. Automatic quota rank uses the Mac's exact 4/2/1 score and stable snapshot-order ties. Every non-OK provider is stale for that ranking even with a fresh timestamp; Android also honors an explicit Hub stale flag or an impossible future timestamp conservatively.

## Verification targets

- Existing WidgetUsageTest and QuotaWidgetTest preserve exact-decimal parsing, explicit zero/unknown, source freshness, distinct accounts and existing pure quota preference projection.
- WidgetAlignmentTest exercises the three actual periods, compact units / rate, same-input seven-day/month/monthly merge, timezone boundary, true stale/future source time, dimension denominator, Mac automatic rank and vanished custom account.
- HubWidgetTest retains encrypted-cache / no-network / corrupt-cache / forget / language-change / actual system host checks, updated for the intentional compact Widget number and two-decimal cost display plus the 30-minute cache-only schedule.
- QuotaWidgetHostTest retains actual host, both themes / font 2 / missing, zero, stale and masked-account cache checks.
- WidgetAlignmentHostTest binds a real AppWidgetHost, captures all five content modes, light/dark, small/normal and font 2; verifies dashboard sections at 320x360, account fields, 48dp edit/section targets, natural text fitting, separate metadata/actions, deletion and restore.
- WidgetAlignmentNavigationScreenTest checks initial/repeated hidden-view routes, natural month despite last30 preference, each instance's page/period, no navigation-only reads, configuration cancellation and a maximum of two custom accounts.

The coordinator must record actual failures and fixes without dropping assertions. Widget screenshots are synthetic cache evidence unless explicitly recorded as live read-only Hub data. Emulator host, launcher, signed covering-upgrade, boot/process-death recovery and Xiaomi physical-device results remain separate gates. No phone install is authorized in this round.
