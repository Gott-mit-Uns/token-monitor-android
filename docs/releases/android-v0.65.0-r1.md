# Token Monitor for Android v0.65.0 r1

The companion now follows desktop Token Monitor v0.65.0, with more useful
session detail and a local privacy choice for conversation titles.

## What changed

- fx usage has its own lowercase label and upstream mark.
- Home and Sessions show generation speed and cache-hit percentage when the
  Hub supplies those counters. Speed is the session average, not a live rate.
- Supported Codex and Claude sessions can show a prompt-cache countdown. It
  is labeled as an estimate and disappears when expired; provider-side cache
  retention is not guaranteed.
- **Settings → Usage presentation → Show session titles** hides reported titles
  in both views. This changes display only; titles can remain in the local
  snapshot cache.
- The four-page widget design, private read-only Hub access and bounded Live
  behavior are unchanged. Desktop collector, hover and Edge Dock changes
  stay on the desktop.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.65.0. Package
`io.github.theminionooo.tokenmonitor`, version code `650001`, signed with the
existing release certificate. Use **Settings → App updates** or install the
APK over your current app. Do not uninstall first.

Missing optional metrics stay blank on older Hub snapshots. Per-model live
throughput and desktop provider-row visibility settings are not added to Android.
The phone does not collect usage, provider credentials or transcript bodies.

Unit, lint, build, emulator and APK-integrity evidence is recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
Physical-phone upgrade, pairing/widget retention and battery behavior were
not tested for this release.
See [installing and updating](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).
