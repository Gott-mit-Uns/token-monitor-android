# Architecture

## Product boundary

Token Monitor for Android is a lightweight, read-only window into one or more
desktop Token Monitor Hubs. It presents totals, limits, tools, models, devices,
projects, sessions, subscriptions, service status, and history without becoming
another collector or a remote-control client.

The desktop remains the source of truth. Pairing uses a private Tailscale address
and Hub secret, with an explicitly enabled home Wi-Fi address as an optional
fallback. There is no Token Monitor relay or supported public-Hub path.

## Responsibility split

Desktop Token Monitor discovers local tools, parses their files, estimates
cost, checks provider limits, and aggregates device history. The Android app
does none of that. It reads the desktop Hub over a private network and presents
the synchronized result. This keeps the phone light and avoids duplicating the
desktop's adapters for dozens of tools.

```text
Desktop collectors → Token Monitor Hub → Tailscale or home Wi-Fi → HubRepository → HubSnapshot → Compose views
```

## Layers

```text
ui/         App shell, screens, presentation rules, ViewModel, ActivityHeatmap
domain/     HubSnapshot, UsagePeriod, DeviceUsage, LimitAccount, HistoryPoint, ...
data/       HubRepository
  protocol/ HubDtos, HubProtocolParser          complete wire → domain
             HubStreamProtocol                  SSE event → complete wire
  network/  HubApiClient, HubAddressValidator, EndpointFailover, HubDiscovery, BackoffPolicy, ServiceStatusClient
  storage/  SecureConnectionStore, SnapshotCache, DisplayPreferences
  update/   ReleaseUpdates (published GitHub release check and verified APK)
widget/     Responsive RemoteViews, cached-snapshot pages, explicit Live service and controls
```

Screens receive domain models and callbacks;
they never see JSON field names, headers, or storage. The stable parser owns the
complete wire shape; the stream reducer owns incremental delivery. A desktop
protocol change is absorbed in `protocol/`, its versioned fixtures, and focused
presentation helpers before any screen needs to change.

## Connection lifecycle

`MainActivity` forwards resume and pause to `DashboardViewModel`, which tells
`HubRepository` whether a dashboard is visible. Settings does not count as a
dashboard. Opening it releases dashboard ownership; an explicit widget session
can keep the same shared repository active on its lighter polling mode.

When a dashboard becomes visible the repository:

1. Reads a full snapshot: `/api/health` (unauthenticated identity check), then
   `/api/stats`, `/api/devices`, `/api/history`, `/api/subscriptions` with the
   bearer secret. A 404 on an optional endpoint is tolerated; a redirect is
   refused so the secret cannot be sent elsewhere.
2. Opens `/api/stats/stream` with `x-token-monitor-stream: 2`. Complete
   `snapshot` and `stats` events replace the wire snapshot. Small `freshness`
   events are merged by `HubStreamProtocol`, which updates only timestamp and
   stale metadata while retaining periods, sessions, providers, and device history.
3. On stream failure, reports "Live updates paused", waits according to
   `BackoffPolicy` (bounded exponential with jitter), and reconnects while the
   dashboard is still visible.

When neither dashboard nor widget session needs updates, network work stops
immediately and the pending cache write is flushed. Returning to the app repeats
step 1 before streaming resumes. Cached data remains visible while that request
completes.

State changes are serialized on the main dispatcher; sockets, snapshot parsing,
and queued cache writes use IO dispatchers. Each shared repository run owns a closable
HTTP client and a generation token so cancelled work cannot replace newer state.
A callback active only while a dashboard or explicit widget session needs it restarts the connection when routes change.
Reconnections fetch a full snapshot to recover missed history. Subscription
refreshes are single-flight and throttle retries of a failed version.

### Two addresses, one connection

`HubConnection` holds a primary address and an optional home Wi-Fi address.
`EndpointFailover` orders candidates with the address that answered last
first, tries the next when one is silent, and stops immediately on a rejected
secret because another address cannot fix that. Snapshot reads use the
ordered list; foreground updates use the active address and alternate on each retry.
The active address is surfaced to the UI so the header and Hub status can say
when the home route is in use.

### Finding a Hub on Wi-Fi

`HubDiscovery` enumerates the phone's private IPv4 addresses, derives up to two
`/24` prefixes, and probes each host's `/api/health` with a short timeout and
bounded parallelism. It runs only on a tap of Find, touches only
private ranges, and never sends the secret.

## Storage

| Store | Content | Mechanism |
| --- | --- | --- |
| `SecureConnectionStore` | Addresses, secret, private-network choice | AES-GCM with a non-exportable Android Keystore key; ciphertext in app-private preferences |
| `SnapshotCache` | Last successful wire snapshot | One gzip file in private storage, written atomically; migrates the first release's preferences store on first read |
| `DisplayPreferences` | Non-secret display choices | Plain app-private preferences, exposed as a `StateFlow` |

Backup and device-transfer rules exclude all three, so a phone migration does
not carry the pairing with it.

Cache writes during foreground updates are coalesced to at most one per minute,
plus one when the app pauses. `SerialDiskQueue` preserves their order and keeps
repository shutdown behind the final save or clear operation.

Cache readers and writers share a process lock around `AtomicFile`. Cache reads
accept older SSE envelopes, and foreground writes store the normalized stats
object. The responsive widget updates after a saved snapshot, when resized, and
after each stats refresh during an explicit Live session; Android 12+ selects
compact, medium, or large RemoteViews using responsive size mappings. Older
Android versions receive portrait and landscape layouts. A separate fixed-layout
pages provider renders Overview, Limits, Breakdown, and Activity from one current
cache/session snapshot. It sends the launcher one complete 1.82:1 page bitmap
plus transparent native touch targets for Previous, Next, Open, Refresh, and
Live. It requests 4×2 but never switches composition when a launcher allocates a
different physical size. The selected page is stored locally per widget. This
avoids launcher collection transforms and keeps page changes local without fetching.
Neither provider registers a periodic update.
See [Android widget layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts).

## Presentation

`DashboardViewModel` owns the current destination, the return destination for
Settings, the connection form state, the display preferences, provider status
loading, and Wi-Fi discovery. It exposes everything as `StateFlow`s.

The Compose presentation layer renders the app from those flows. Its files are
split so that each file has one job. `TokenMonitorApp.kt` sets up theme and
motion and hosts the scaffold; `DashboardChrome.kt` and `Menus.kt` are the shell
around the content; `DashboardContent.kt` routes the selected view to one of the
screen files (`HomeScreen`, `BreakdownScreen`, `LimitsScreen`, `DevicesScreen`,
`ProjectsScreen`, `StatusScreen`, `TrendsScreen`), which share `DetailRows.kt`,
`Primitives.kt`, and `ActivityHeatmapGrid.kt`; `ConnectionSettings.kt` owns pairing
and settings. Shared presentation rules live alongside them in focused files:

- **Palette and typography.** `Theme.kt` holds `InterfaceTheme` (the four
  desktop-customizable colors, with the desktop's `TM1-…` code format) and
  `Palette`, every color the app draws, resolved from a theme the way the
  desktop stylesheet does it: a light background flips the overlay, line,
  panel, and sunken surfaces, and semantic colors stay fixed. The palette is
  provided through `LocalPalette`; semantic color getters and typography live
  in `Theme.kt`. Typography is built once per text size setting. Widget RemoteViews
  resolve this same Palette; the theme preference changes both surfaces. A local
  widget redraw applies new colors without enabling Live or fetching data.
- **Time.** `LocalNow` is a composition local updated every thirty seconds.
  Countdowns (`formatReset`) and relative ages (`formatRelativeAge`) read it,
  so labels stay accurate between Hub events without per-row timers.
- **Motion.** `LocalInteractionMotion` resolves the reduce-motion setting
  against the system animator scale. Every animation reads it and collapses
  to zero duration when motion is off. `DesktopEaseOut` is the desktop's
  easing curve. The headline total rolls toward new values, bars and charts
  grow in on open, chevrons rotate, and views cross-fade with a small slide.
- **Menus.** `AnchoredMenu` is a popup positioned a few pixels off its anchor,
  replacing the stock menu whose screen-edge margin left a gap above the view
  switcher.
- **Periods.** `UsageAggregation.kt` maps Day, Month, and Total to Hub periods and
  computes Week, 7-day, and 30-day ranges from daily history plus today's live
  period. Rolling ranges have no sessions or projects because the Hub reports
  those only for its own periods.
- **Formatting.** `UsageFormatting.kt` centralizes cached formatters and stable
  labels; `VendorPresentation.kt` maps tool and model names to marks and colors;
  `SessionPresentation.kt` owns the Running, Finished, and Idle window plus
  context-window calculations. Those rules are unit-tested without Compose.
- **Home sessions.**
  `HomeSessions.kt` derives the five most recent sessions plus running overflow
  from month/today, deduplicated by client and ID. It uses the shared activity,
  context and slow clock helpers rather than new timers or requests. Archived,
  deleted and source-deleted wire flags map to one idle domain state. Existing
  stored Home module choices are retained when new defaults are added.

- **Settings.** `ConnectionSettings.kt` sections collapse to a header with a summary,
  like the desktop settings list. A fresh install shows `WelcomeSetup` instead
  of Settings until a Hub is saved.

## Heatmap geometry

`ActivityHeatmap.kt` computes the rolling twelve-month contribution grid and
its intensity ramp outside Compose so partial or duplicated history is handled
deterministically and unit-tested. The Compose layer only draws the cells.

## Deliberate limits

- The Hub does not synchronize prompt or response bodies, so the phone cannot
  render session transcripts.
- The app is read-only: no ingest, no account switching, no subscription
  edits, no device deletion.
- Desktop appearance, window, tray, collection, and credential settings remain
  desktop responsibilities.
- Widget Live is an explicit, bounded foreground service with a Stop notification. No worker, wake lock, boot restart, or analytics.

## Widget Live and motion

The dashboard and service acquire a main-thread reference-counted repository. A
visible dashboard uses the Hub's SSE stream for immediate updates. Widget Live uses
one authenticated `/api/stats` read every 30 seconds after its initial snapshot,
avoiding a full stream frame for every desktop ingest. Opening or closing the
dashboard switches delivery modes without running both. Turning Live off releases
the widget lease; changing pairing or disconnecting first cancels the session.

The service is START_NOT_STICKY, has a one-hour monotonic expiry, handles Android's
dataSync timeout callback, and does not request a wake lock or battery exemption.
Refresh uses the same service for one snapshot with a 45-second timeout. First use
requests notification permission so Stop remains available outside the launcher.
Android sleep and connectivity can delay data; the widget always includes its last
successful update time and labels reconnecting sessions separately from Live.
The notification is reposted only when text visible to the user changes.

The midnight layout uses the existing upstream vector assets. Counts use full Long
precision with grouping. A native ViewFlipper slides the whole total only when
new data changes it; app Reduce Motion switches to a static TextView. This is not
the Compose per-digit renderer. No intermediate token values or animation timer
are sent through AppWidgetManager. Resizing adds quota rows and a seven-day chart;
missing history observations are shown as gaps.
