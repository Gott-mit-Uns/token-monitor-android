# Token Monitor for Android v0.63.0 r1

This candidate updates the Android companion's verified desktop baseline to
Token Monitor v0.63.0. It is not published yet.

## What changed

- Checked the v0.63.0 Hub read contract across five endpoints, complete stream
  events, and freshness events. Earlier versioned fixtures remain in the test
  suite.
- Show a Cursor conversation title in Sessions when the desktop reports one.
  Older and untitled sessions keep the existing client/model label. The app does
  not fetch prompt or response bodies.
- Keep the four-page Pages widget design and its bounded Live behavior unchanged.

## Compatibility

- Android: 8.0 or newer
- Desktop Token Monitor: verified through v0.63.0
- Package: `io.github.theminionooo.tokenmonitor`
- Planned upgrade: version code `630001` over the published v0.62.0 r1 APK,
  using the same release-signing certificate

Install a future signed release over the existing app; do not uninstall first.
The [install guide](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/INSTALL.md)
explains verification and the user-confirmed installer step.

## Verification

Completed and pending checks are recorded in
[Validation](https://github.com/The-Minion-oOo/token-monitor-android/blob/main/docs/VALIDATION.md).
