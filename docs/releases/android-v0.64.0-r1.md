# Token Monitor for Android v0.64.0 r1

The Android companion now follows desktop Token Monitor v0.64.0 and brings
recent sessions onto Home without changing your saved layout.

## What changed

- Muse Code has its own label and vendor mark in usage summaries.
- StepFun's reported plan name appears with its five-hour, weekly, or credit
  allowance. Codex's Pro/Pro More/Pro Max labels and OpenRouter's resetting key
  allowance use the values normalized by the desktop.
- Home can show the five newest sessions plus any others still running, with
  reported titles and recent context use. New installs show the module by
  default. On an existing saved layout, enable **Sessions** in
  **Settings → Main dashboard → Home modules**.
- Archived/deleted sessions no longer appear running. Grok Build's richer
  titles and project metadata use the existing session view.
- Session rows remain separate when two tools report the same session ID.
- The four-page widget layout and bounded Live behavior are unchanged.

## Compatibility and updating

Android 8.0 or newer; verified desktop baseline v0.64.0. Package
`io.github.theminionooo.tokenmonitor`, version code `640001`, signed with the
existing release certificate. Use **Settings → App updates** or install this
APK over the existing app; do not uninstall first.

Desktop iCloud Drive sync does not replace Android's private Hub connection.
The phone remains read-only and does not collect usage or provider credentials.
Reported titles may contain private text and appear in Home and Sessions, not
widgets.

JVM, lint, build, emulator and release-integrity evidence is recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
Physical-phone upgrade, pairing/widget retention and battery behavior have not
been verified for this release.
See the [install guide](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).
