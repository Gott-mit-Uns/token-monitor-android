# Security and privacy

## Network boundary

The app accepts Tailscale addresses by default. Private LAN addresses require an
explicit setting. Public targets are rejected to keep transport private and
prevent Hub credentials from being sent to arbitrary internet hosts.

[Tailscale](https://tailscale.com/docs/reference/connection-types) normally
attempts a direct device-to-device connection and can fall back to an
end-to-end encrypted relay when network conditions require it. The app does not
operate a cloud relay or require the desktop Hub to be exposed on the public
internet.

The desktop Hub secret is sent as a bearer credential only to the validated Hub address. It is never logged or included in diagnostics.

The unauthenticated health response must identify a Token Monitor Hub before the
first protected request is sent. HTTP redirects are rejected so an authenticated
request cannot move the bearer secret to another host.

## Local storage

- The Hub URL and secret are encrypted with a non-exportable Android Keystore key.
- The app-private snapshot cache stores compressed Hub JSON responses, including usage and account/device/project metadata returned by the Hub. It is not separately encrypted by the app; Android app isolation protects access. Pairing credentials use the separate Keystore-backed store.
- Android backup/data-transfer rules exclude pairing and snapshot storage.

## Read-only API use

The app calls only:

- `GET /api/health`
- `GET /api/stats`
- `GET /api/devices`
- `GET /api/history`
- `GET /api/subscriptions`
- `GET /api/stats/stream`

It does not call ingest, subscription mutation, or device deletion routes.

Two optional connection features operate only within private address ranges:

- The **home Wi-Fi fallback** is a second saved address that must pass the
  same private-network validation. The secret is sent only to whichever of the
  two saved addresses identifies itself as a Hub.
- **Find** searches the phone's current private `/24` network for a Hub by
  reading the unauthenticated `/api/health` endpoint. It runs only when
  tapped, never sends the secret, and stops as soon as one Hub answers.

The Status view separately reads public service-health endpoints for Claude,
OpenAI, Cursor, and DeepSeek. Those requests do not include Hub credentials
or Token Monitor usage data.

Opening App updates checks this app's latest published GitHub release. A
download starts only after the user taps Download and install. These HTTPS
requests do not include the Hub secret or usage data. Before handing the APK to
Android, the app verifies the release checksum, package identity, version code,
and pinned release signing certificate. Android controls installation approval.

## Lifecycle and resource use

A user-started dataSync foreground service supports widget Live for one hour with
a 30-second refresh cadence, or a single refresh for up to 45 seconds. It shares
the dashboard repository, requests notification permission to expose Stop, and
stops without restarting after expiry, removal of the last widget, or a process
kill. There is no wake lock, scheduled worker, boot receiver, analytics SDK, or
advertising SDK.

`ACCESS_NETWORK_STATE` lets an active dashboard or widget session reconnect after a route changes.
The home-screen widget reads saved data while Live is off and receives current snapshots during an explicit session. It
shows saved totals, estimated cost, quota windows, and a small usage chart according
to its size. Adding it makes those figures visible on the launcher; account emails,
project labels, session identifiers, and connection credentials are omitted.

## Data visible on the phone

Hub responses can include device names, model/tool attribution, project folder labels, session identifiers and titles, account email/plan metadata, costs, and normalized limits. Conversation titles can reveal sensitive text and appear in Home and Sessions when the Hub provides them; they are not displayed on the home-screen widgets. Account emails are hidden by default and can be shown only through an explicit display setting. The Hub does not send absolute project paths or prompt/response transcript bodies.

## Reporting a problem

Do not include a Hub secret, unredacted account email, raw Hub response, or private tailnet address in a public issue. Reproduce with the sanitized fixture Hub when possible.
