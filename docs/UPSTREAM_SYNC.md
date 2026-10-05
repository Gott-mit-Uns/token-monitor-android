# Keeping pace with desktop Token Monitor

## Version rule

The current Android release displays `v0.66.0` because it is fixture-verified against desktop Token Monitor `v0.66.0`. The Gradle properties and `upstream.json` identify tag `v0.66.0` and peeled commit `e7bac3aff6a1bc7cf74fd149a26d2730cfe32de8`. The signed release is [`android-v0.66.0-r2`](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.66.0-r2), recorded with its source commit in `release.json`. The earlier r1 Galaxy S25 Ultra upgrade preserved pairing and the placed Pages widget; r2 physical-device checks remain pending.

An Android-only fix increments `versionCode` and the GitHub release revision,
for example `android-vX.Y.Z-rN`, without changing the visible compatibility
version. A newly verified desktop release updates the visible version to match
it and starts again at release revision 1.

## Update procedure

1. Run `tools/check-upstream.ps1` or the scheduled GitHub workflow.
2. Read the upstream release notes and diff these areas first:
   - `docs/API.md`
   - `src/hub/server.js`
   - `src/shared/history.js`
   - `src/shared/usage.js`
   - `src/electron/renderer` view and presentation helpers
3. Save sanitized responses for every Android read endpoint under a new `app/src/test/resources/protocol/vX.Y.Z` directory.
4. Sort each change into one of four contained lanes:
   - stable wire fields in `HubDtos` and `HubProtocolParser`;
   - stream event delivery and freshness merging in `HubStreamProtocol`;
   - reusable display semantics in focused presentation helpers such as
     `SessionPresentation` and `VendorPresentation`;
   - native Android layout changes only when the new data improves a mobile view.
5. Keep prior fixture tests when backward compatibility is expected. An omitted
   optional field must retain its previous behavior.
6. Compare Android screens with the new upstream screenshots and renderer tokens.
   Desktop-only window features do not become Android work by default.
7. Update `upstream.json`, Gradle compatibility fields, README compatibility table,
   candidate release notes, and validation evidence together.
8. Run unit tests, lint, debug/release builds, emulator interaction checks, and the physical-phone gate before publishing.

The scheduled check reports a release and closes obsolete automated review
reminders already covered by the verified baseline. It preserves manual issues
and closed review decisions, and never merges upstream code automatically.
The pinned-source contract workflow also exercises the released Hub with
synthetic data and passes its responses through Android's parser; see
[Development](DEVELOPMENT.md). Desktop renderer code cannot be copied wholesale
into a native Android app, and protocol or privacy changes require review.

## Why updates stay contained

The wire adapter, stream reducer, domain model, and presentation rules are separate.
Additive fields are ignored safely. New useful fields are mapped once in the parser,
incremental transport events are merged without teaching screens about SSE, and UI
semantics are tested without Compose. Most upstream releases should therefore require
a versioned fixture plus small adapter changes rather than a dashboard rewrite.
