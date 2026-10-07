# Token Monitor for Android v0.67.0 r1

This release follows desktop Token Monitor v0.67.0 and adds its useful mobile
data and appearance changes without changing the fixed Pages widget layout.

## What changed

- TM2 theme codes can carry an independent chart color. K-line up candles, seven-day
  widget bars and activity heatmaps use that custom color; provider colors,
  existing TM1 codes and preset appearance remain intact.
- MiMo Console and Desktop Membership appear as separate account products.
  Console balances stay in their reported currency, not quota percentages.
  Month/all-time spend is provider-reported; today/week spend is labeled tracked.
- Compatibility checks exercise the released Hub's opt-in title delivery,
  default text stripping and revocation. Android still reads received titles
  only, respects its local visibility setting and never reads transcripts.

The previous revision's home Wi-Fi recovery, bounded widget Live sessions and
existing release certificate are retained. Shared alias/pricing documents and
title-sharing controls remain on desktop; Android does not write Hub settings.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.67.0. Package
`io.github.theminionooo.tokenmonitor`, version code `670001`, signed with the
existing release certificate. Use **Settings → App updates** or install the
APK over the current release. Do not uninstall first.

Build, emulator and review evidence is recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
This revision has not been installed on a physical phone. Its in-place upgrade,
pairing/widget retention, One UI behavior, Tailscale/Wi-Fi transitions and
battery use remain unverified on a physical device.
