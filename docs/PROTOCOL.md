# Hub protocol compatibility

This Android app treats the desktop Hub as an external, read-only protocol.

## Verified baseline

The verified baseline is upstream Token Monitor **v0.63.1**, released 2026-09-28. The
release and source were checked directly from the `Javis603/token-monitor`
tag before this implementation. The older local desktop checkout was not
changed and is not the protocol authority.

The current Node Hub binds to loopback when no secret is configured. A secret is
therefore required before the Hub can bind to a Tailscale or LAN address. The
Android app always sends the secret as `Authorization: Bearer <secret>` and
never prints it.

| Read endpoint | Authentication | Android use |
| --- | --- | --- |
| `GET /api/health` | No | Verify that the target identifies itself as a Hub. |
| `GET /api/stats` | Bearer secret | Main snapshot: periods, limits, devices, projects, sessions, and history preview. |
| `GET /api/devices` | Bearer secret | Device periods, platform metadata, tracked tools, cadence, and retained per-device history. |
| `GET /api/history` | Bearer secret | Daily/monthly totals and per-tool/per-model trend attribution. |
| `GET /api/subscriptions` | Bearer secret | Subscription dashboard. |
| `GET /api/stats/stream` | Bearer secret | SSE `snapshot`, `stats`, and v2 `freshness` frames while a dashboard is visible. |

The Hub also accepts `X-Token-Monitor-Secret`; this app intentionally uses one
authentication style only. It does not call the Hub's ingest, subscription
write, or device deletion endpoints.

## Errors and streaming

The verified Hub returns `{ "error": "unauthorized" }` with HTTP 401 for a
protected read without a valid secret, and `{ "error": "not_found" }` with
HTTP 404 for an unknown route. The app gives a specific safe message for those
conditions and does not surface request headers or the secret.

Android sends `x-token-monitor-stream: 2` when it opens `/api/stats/stream`.
The Hub sends an initial `snapshot`, complete `stats` events when values change,
and small `freshness` events for limit and device timestamps. Each data envelope
contains `stats`. The Hub also emits an SSE comment heartbeat. The app closes the underlying stream in
`onPause`, opens it only on a dashboard route, refreshes first on resume, and
retries a disconnected stream with 1–30 second bounded exponential backoff plus
jitter.

`HubStreamProtocol` treats complete and incremental delivery separately from the
stable snapshot parser. A freshness frame may update only the top-level update
time, stale threshold, limit freshness, and device freshness fields. It is merged
into the last complete wire snapshot by device ID, preserving periods, sessions,
limits, provider details, and retained device history. A foreground refresh still
replaces the snapshot with the current read endpoints.

## Fixtures

Sanitized v0.63.1 examples for all five read endpoints and the live stream live
in `app/src/test/resources/protocol/v0.63.1/`; the retained v0.54.0, v0.55.0,
v0.56.0, v0.60.0, v0.61.0, v0.62.0, and v0.63.0 fixtures prove backward compatibility. They contain no user secrets,
machine paths, account identifiers, or real usage. Parser tests cover every
read surface, the SSE envelope, omitted optional fields, and a future unknown
field. The compatibility parser maps only the fields the dashboard needs and
ignores the rest. v0.54.0 coverage includes project `tokens`, session counts
and times, device OS/cadence/history, limit labels and balances, and history
component provenance inside `perClient` and `perModel`.

v0.55.0 adds `capabilities.throughput` and the optional period fields
`timedTokens`, `timedOutputTokens`, and `timedDurationMs`. Android maps and tests
those values, treats absent or explicitly unavailable throughput as unknown,
and does not currently claim a visible token rate.

v0.56.0 adds optional `windows[].boundaryKind` lifecycle wording and
`periods.*.sessions[].sessionKind` metadata. Android preserves both, renders
reset, expiry, and mixed boundaries accurately, and keeps older omitted fields
on their legacy behavior.

v0.60.0 adds optional session `contextTokens`, `contextWindow`, and tri-state
`turnEnded` fields. `false` means a recent turn is still running, `true` means
the turn ended, and an omitted value remains unknown; the parser does not collapse
those states. Android shows context use only when both positive values are present
on a recent Running or Finished session. The same fixture covers current Factory
percentage and credit allowance shapes plus stream-v2 complete and freshness events.

v0.61.0 keeps the read endpoint and session shapes stable. It normalizes Xiaomi
MiMo usage to the canonical `mimo` client, adds `devin` usage, and adds Cline and
Devin limit providers. Android recognizes those identities, keeps the legacy
`micode` label readable, and retains the existing Copilot and context-window
fields. Desktop-only Edge Dock, floating bubble, and macOS haptic changes do not
cross the Hub contract.

v0.62.0 keeps the five read endpoints and stream envelope. The Node Hub now
requires the secret in a header, which Android already sends as Bearer auth.
Usage identifies Oh My Pi as `omp` separately from `pi`. TypeSafe supplies a
credit-balance window and its plan in `accountLabel`; Devin may supply
`planLabel`. Android renders those available values and safely ignores optional
Claude reset-grant and provider usage-summary details it does not yet display.

v0.63.0 retains those endpoints, header authentication, and stream-v2 envelope.
The release changes mostly desktop presentation, collection, and limits-account
setup. Cursor may now send a locally resolved conversation `title` on a session;
Android reads that optional field and keeps its client/model fallback when absent.
The title is metadata visible in Sessions, not a prompt or response body. The
new Antigravity and Qoder CN source checks affect desktop collection, not phone
access to the Hub. The v0.63.0 fixture is synthetic and follows the released
wire contract; it is not an unredacted Hub capture.

v0.63.1 leaves `docs/API.md`, the Hub routes, header authentication, and the
stream-v2 envelope unchanged. Desktop normalization now reports Cursor Auto as
`cursor-auto` across current usage and historical model buckets, while ZCode and
OpenCode usage includes disjoint reasoning tokens in the reported output totals.
Android preserves those server-reported IDs and counters without recomputing
them. The v0.63.1 fixture is synthetic and covers that data shape, not a real
Hub capture. Collection-worker and Electron renderer performance changes stay
on the desktop.

When the desktop protocol changes, add a new versioned fixture directory and
tests before changing the Android mapping.
