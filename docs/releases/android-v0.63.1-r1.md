# Token Monitor for Android v0.63.1 r1

This release updates the Android companion's verified desktop baseline to
Token Monitor v0.63.1.

## What changed

- Checked the unchanged Hub read routes and stream-v2 envelope against the
  released desktop source. A new sanitized fixture covers all five read
  endpoints, a complete stream event, and a freshness event.
- Preserve the desktop's canonical `cursor-auto` model in usage, session, and
  history views. ZCode and OpenCode totals include the reasoning tokens already
  counted by the desktop; Android does not add them a second time.
- Keep the daily token-component totals from full history when a matching live
  preview omits them. A changed day still replaces the older attribution.
- Keep the four-page Pages widget geometry and bounded Live behavior unchanged.

## Compatibility

- Android: 8.0 or newer
- Desktop Token Monitor: verified through v0.63.1
- Package: `io.github.theminionooo.tokenmonitor`
- Upgrade: version code `631001` over v0.63.0 r1, using the same
  release-signing certificate

Install this signed release over the existing app; do not uninstall first. See
the [install guide](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md).
The physical-phone in-place upgrade, saved pairing, widget retention, and battery
behavior have not yet been verified for this release.

## Verification

Completed checks and remaining release gates are recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
