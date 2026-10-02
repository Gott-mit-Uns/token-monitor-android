# Validation

## v0.65.0 r1 release — 2026-10-02

Desktop v0.65.0 is pinned to released tag commit
`db325fdf46ea7f7328feeb47a4f2005fe339909f`. Review of the v0.64.0-to-v0.65.0
diff confirmed unchanged Hub read routes, Bearer authentication and stream-v2
delivery. New synthetic endpoint and stream fixtures cover fx usage, optional
session output/duration counters, cache components and prompt-cache estimates;
all earlier fixtures remain. Per-model live throughput and desktop provider-row
visibility settings are deliberately not displayed or controlled by Android.

All 106 JVM tests and 37 instrumentation tests passed. Android lint reported no
errors; preview debug assembly and the signed R8 release build passed. The API 36
Pixel 10 Pro XL emulator verified title hiding on Home and Sessions, metrics,
preference reload and the existing navigation, widget and lifecycle regressions.
The preview app paired with the synthetic fixture Hub; native Home and Sessions
captures were inspected for fx's mark and session metrics. The expired synthetic
cache estimate was correctly absent; fixed-clock interaction tests verify its
visible label and unit tests cover expiry and invalid/future observations.
Captures remain private. Widget coordinates, type sizes, gallery and hero are
unchanged. No network ownership, polling or background-work changes were made.

The independent read-only review covered the exact diff, parser/domain mapping,
callers, privacy settings, numerical/date edge cases, fixtures and documentation.
The final documentation pass corrected an outdated install guide and the
title-setting path in the release notes. No material code findings remained.
Local documentation links resolve; Git diff whitespace checks passed.
GitHub build and interaction checks must pass on the exact release main commit.

APK: `token-monitor-android-v0.65.0-r1.apk`, 2,418,300 bytes,
package `io.github.theminionooo.tokenmonitor`, version code `650001`.
File SHA-256:
`fdb6d301af07397b5a8381831f963daef065db2b7d3dd40c714a3ced5f1c74c7`.
Certificate SHA-256:
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.
The APK, checksum and matching update manifest use the existing upgrade identity.
Physical-phone upgrade, pairing/widget retention, Tailscale/Wi-Fi transitions
and battery behavior remain unverified for this release.

## v0.64.0 r1 release — 2026-09-30

The released desktop tag is pinned to
`9ad1ca2f6ec27e497eb38fffe7d9533aec0d3c38`. Review of the v0.63.1-to-v0.64.0
diff found no change to the five Hub read routes, Bearer authentication, or
stream-v2 contract. New synthetic fixtures cover Muse output, Grok title/project
metadata, StepFun Coding/Token Plan windows, optional archive flags, Codex plan
labels, and OpenRouter's normalized key allowance. Earlier fixtures remain.
Home's recent-session selection is tested for canonical identity, ordering,
five-row cap plus running overflow, background review exclusion, invalid dates,
and archived-session activity/context suppression.

All 99 JVM tests passed. Android lint reported no errors; preview debug assembly
and the signed R8 release build passed. All 34 instrumentation tests passed on
the API 36 Pixel 10 Pro XL emulator (`emulator-5554`), including Home session
titles/context/navigation, cross-tool session IDs, and saved Home-layout retention. The preview app
paired with the loopback synthetic Hub through the emulator's host route.
Native captures of Home and Sessions were visually checked: StepFun plan labels,
Muse marks, unmodified mixed-case titles, and Home-to-Sessions navigation were
present. Captures stay private. This is emulator evidence, not One UI or phone
verification. The Pages widget coordinates/type sizes and marketing gallery
are unchanged.

The independent read-only review covered the exact diff, callers, fixtures,
preferences, privacy wording and release metadata. A stale fixture description
in the development guide was corrected. The final navigation review also found
that the Sessions list keyed rows by ID alone. Rows and expansion state now use
client plus ID; a device regression covers two tools sharing an ID. The final
regression pass found no
remaining material findings. Local documentation links and fixture JSON parsed.
GitHub build and emulator checks must pass on the release's exact main commit
before publication.

The APK is `token-monitor-android-v0.64.0-r1.apk`, 2,417,304 bytes, package
`io.github.theminionooo.tokenmonitor`, version code `640001`.
Its certificate SHA-256 remains
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`;
its file SHA-256 is
`734f5670672558d10f775b008b4f69b57a03fb5a928b196cfcfcb468a3c36389`.
The release carries the APK, checksum and matching
`token-monitor-android-update.json` manifest for in-app updates.
Physical-phone in-place upgrade, saved pairing/widget retention and battery
behavior remain unverified. The existing release certificate and higher version
code preserve the upgrade identity; they are not a claim of physical-phone testing.

## v0.63.1 r1 release — 2026-09-28

Desktop v0.63.1 is pinned to the released tag commit
`e38f60a94ce6310341f8ad2de6888f1ec51dd755`. The v0.63.0-to-v0.63.1
review found no changes to `docs/API.md`, the five Hub read routes, bearer
authentication, or the stream-v2 envelope. Desktop normalization now reports
one `cursor-auto` model across usage and history and includes disjoint reasoning
tokens in ZCode/OpenCode output totals. Android preserves the reported values;
it does not collect or add tokens. The new synthetic fixture covers all five
read endpoints, a complete stream event, and a freshness event. Earlier fixtures
remain in the regression suite. Review of that fixture exposed a sparse-preview
merge that dropped matching daily token-component totals; the merge now retains
them only while the day's token and cost totals match. The Pages widget geometry
and type sizes are unchanged. The independent diff review also found an
internally inconsistent synthetic output breakdown and release-note wording;
both were corrected. The final regression pass found no remaining material
issues.

All 94 JVM tests passed, including the new model/counter regression. Android
lint, debug assembly, and the R8 release build passed. All 31 instrumentation
tests passed on the API 36 Pixel 10 Pro XL emulator (`emulator-5554`). The
fixture JSON parsed and local documentation links resolved. The scheduled
local upstream check identifies v0.63.1 as the latest released version; nine newer
`main` commits remain unreleased and are not part of this baseline.

The signed APK reports package `io.github.theminionooo.tokenmonitor`,
version code `631001`, and the existing release certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`.
Its file SHA-256 is
`1a517ad54d9dc58f180f5126f3cf661764ee66bed2b12c4978a349b8d436264b`.
The APK, checksum, and matching update manifest are published under
[`android-v0.63.1-r1`](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.63.1-r1).
The owner requested publication to perform the in-app upgrade remotely, so the
physical-phone in-place upgrade, retained pairing and widget registration, and
battery behavior remain unverified at publication.

## v0.63.0 r1 release

Desktop v0.63.0 is pinned to tag commit
`f756d1d0fdbf415c1cd4a7178f3fe5d7a026fc90` (released September 26,
2026). Review of the released diff found no change to the five Hub read routes,
header authentication, or stream-v2 envelope. The documented Antigravity source
check and Cursor title metadata are additive. Synthetic, sanitized v0.63.0
fixtures cover every read endpoint, a complete stream event, and a freshness
event; v0.54.0 through v0.62.0 regressions remain in the suite.

On September 27, all 93 JVM tests, Android lint (zero errors), preview debug
assembly, fixture JSON parsing, and local documentation-link checks passed.
All 31 instrumentation tests passed on the API 36 Pixel 10 Pro XL emulator
(`emulator-5554`), covering the existing dashboard navigation, lifecycle,
widget controls, and four-page renders. The new Cursor title and untitled-row
fallback are covered by focused JVM tests; the title was not visually captured
on a physical phone.
The release APK was built locally with R8 and signed with the existing release
certificate (SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`).
It reports package `io.github.theminionooo.tokenmonitor`, version code
`630001`, and file SHA-256
`84ff5841e5c4dbec5a11e24c80115d0b23a9f2bd0838843d3255ae3755427492`.
The owner elected to skip the physical-phone gate for this release. An in-place
upgrade, saved pairing/widget retention, and battery behavior have therefore
not been verified for v0.63.0; v0.62.0 r1 remains the latest phone-verified
build. The Pages widget geometry and type sizes are unchanged.

The previous public release is Android **v0.62.0 r1** (`620001`). Its published
APK was installed over v0.61.0 r1 on a physical phone without losing the saved
connection or placed Pages widget registration.

## v0.62.0 r1 release

The release is pinned to desktop v0.62.0 tag commit
`dcccfb01557e2786888fd5479552f392ac6c0d32`; the installed Windows desktop
executable reports v0.62.0. The released read endpoints and stream envelope
remain compatible. The Node Hub requires header-based secret authentication,
which the Android client already uses. A new sanitized fixture covers all five
read endpoints, a complete stream event, and a freshness event. It exercises
separate `pi` and `omp` usage, TypeSafe's plan and credit window, and Devin's
plan without containing real Hub credentials, account data, or usage.

The on-demand updater carries forward from the unpublished v0.61.0 r2 source
candidate. Its version comparison, release metadata, package, and rollback
rules passed four focused JVM tests. On this v0.62.0 source, all 91 JVM tests,
Android lint, signed R8 release assembly, and the documentation-link check
passed. Both public build and emulator interaction checks passed on the release
commit `6eb97d83f6b4b7966bb1a05201b8bdef79a36747`.

On the API 36 Pixel 10 Pro XL emulator (`emulator-5554`), the side-by-side
preview paired with the synthetic v0.62.0 fixture Hub. The Home screen showed
separate Pi and Oh My Pi tool rows, TypeSafe's Pro plan and $42.50 balance, and
Devin's Core plan. Settings showed `v0.62.0 r1` and correctly treated the
published v0.61.0 r1 Android release as older. All 31 instrumentation tests
passed after removing the Android CLI layout helper; the first run had two
`UiAutomation` conflicts while that helper was active, not app assertions.

The published APK was installed over v0.61.0 r1 on a Galaxy S25
Ultra (`R5CY7205W0B`) without uninstalling. Android reported version code
`620001`; the release certificate matched the pinned SHA-256 fingerprint.
The saved Home Wi-Fi connection remained active, the placed Pages widget kept
its launcher registration (widget ID 42), and Settings showed `v0.62.0 r1`
with the older public Android release correctly reported as not newer. The
widget's visual content was not recaptured after this upgrade. The temporary
Android CLI layout helper was removed from the phone afterward.

The GitHub release APK was downloaded back from the draft and matched the
phone-tested file byte-for-byte. Its SHA-256 was
`036044ac4944f7839325b9d95fdba7bb86bb5ed1b73d34feda4ef9e8688827e1`.
Its manifest, checksum file, size, version code, and pinned signing certificate
were verified before publication.

End-to-end in-app download and installer handoff still needs a later published
release carrying update metadata; v0.61.0 r1 has no updater code.

## v0.61.0 r1 release

The release is pinned to desktop Token Monitor v0.61.0 tag commit
`dc3cc1321d9490873ff96abb662e07eecea75751`. The released Hub keeps the same
five read endpoints and stream envelope used by v0.60.0. A sanitized v0.61.0
fixture covers health, stats, devices, history, subscriptions, complete stream
events, and freshness events; no local Hub address, secret, account, device, or
usage response is included.

The compatibility delta is presentation and identity: canonical `mimo` and
`devin` clients, Cline and Devin limit providers, GitHub Copilot, and current
desktop provider labels. Older fixtures from v0.54.0 through v0.60.0 remain in
the suite. The r7 Pages widget specification, coordinates, and type sizes are
unchanged.

Before the updater addition, all 85 JVM tests, Android lint, the debug build,
the R8 release build, and the documentation-link check passed. The hero and
social preview were regenerated
from the existing synthetic captures with the v0.61.0 r1 footer and inspected
at full size.

The independent read-only diff review of the original compatibility candidate
found a premature release-status claim, which was corrected before publication.

The published APK reports package `io.github.theminionooo.tokenmonitor`, version
code `610001`, certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`, and file
SHA-256 `cc52c84aa519d72de65465ed39224043ef8e1cd044dc6abda013c1e27882f705`.
Its certificate matches v0.60.0 r7. On September 24, the published r1 APK was
installed over r7 on the Galaxy S25 Ultra without uninstalling. Android reports
version code `610001` and the unchanged first-install time
(`2026-09-21 11:03:41`). After unlocking, the app opened to live Hub data over
the saved Home Wi-Fi connection, and Settings showed the preserved preferences
and `v0.61.0 r1`. The existing Pages widget remained placed on the Samsung
launcher at its prior 406×216 dp size. Overview rendered current data; the
widget advanced through Limits, Breakdown, and Activity and returned to
Overview. No uninstall or re-pairing was needed. The temporary Android layout
helper was removed and the original USB display setting restored.

## v0.60.0 r7 release

A phone inspection of r6 on September 22, 2026 with real two-tool, three-model
data showed the dense Breakdown rows crowded: the model names' descenders
touched the token line beneath them, and the tool line packed name, tokens and
share together. r7 gives both columns one two-line row with fourteen units
between the lines and aligns three rows across the divider. The same inspection
showed "0 MESSAGES" on Activity because the Hub reported no message count for
the day; r7 shows today's cost in that slot instead of a false zero.

The specification was updated before the renderer. 83 JVM tests, lint with no
errors, the debug build, the seven-test emulator design run, the sixteen-test
gallery capture and the documentation link check pass.

The signed r7 APK reports package `io.github.theminionooo.tokenmonitor`, version
code `600007`, certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c` (unchanged
since r3), and file SHA-256
`562d28d63902f86e55079d56120c30398dbbbd03914d19bf1328d41d48747f96`. It was
installed over r6 on the Galaxy S25 Ultra without uninstalling; the first-install
time (`2026-09-21 11:03:41`) and the placed widget were preserved. All four
pages were captured on One UI at the 406×216 dp allocation and are in
`local-private/widget-r7-phone-2026-09-22/`. Breakdown shows the two-tool,
three-model state with clear rows in both columns; Activity shows the cost
fallback in place of the missing message count; Overview and Limits are
unchanged from r6. The owner reviewed the Breakdown render twice during the work: the row
spacing was widened, then the vendor mark was lowered to center on the two
text lines, and the same centering was applied to the header app icon and the
Limits provider marks, each time before a signed rebuild and phone install.

## v0.60.0 r6 candidate

The owner's final r5 One UI screenshot showed that the common one-tool/one-model
state still looked like a mostly empty dense table: small figures clustered at
the top and the token total could read into the share. r6 gives this sparse state
a dedicated feature layout with large token and share figures on opposite column
edges, explicit captions, matching baselines, and full-width bars. Multi-row data
continues to use the dense table. The card ratio and the other three pages do not
change.

The written grid specification was updated before the renderer. All 82 JVM
tests, all 31 API 36 instrumentation tests, lint, debug assembly, signed release
assembly, documentation links, and the final diff check pass. The independent
review found no material issue.

The signed r6 APK reports package `io.github.theminionooo.tokenmonitor`, version
code `600006`, certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`, and file
SHA-256 `0f73f96da54087481ec416b486e35b8cd90833fc1bb5d7b289ae15e896fb7ef6`.

The APK was installed over the existing r5/r6 candidate on a Galaxy S25 Ultra
(SM-S938U) without uninstalling. The original first-install timestamp
(`2026-09-21 11:03:41`) and One UI widget ID `42` were preserved. Physical
captures of Overview, Limits, Breakdown, and Activity are in
`local-private/widget-r6-phone-2026-09-22/`. The sparse Breakdown capture shows
the token and share figures separated and aligned at the installed 5×2 widget
size; the other three pages remain visually consistent with the approved r5
phone-size comparison.

The bounded battery gate found no active Token Monitor service or wake lock and
no Android CLI test instrumentation. This is an idle-state check, not a
multi-hour battery soak.

## v0.60.0 r5 candidate

The candidate is based on desktop Token Monitor v0.60.0 commit
`8031cf3b75c7f354db8a990a983999c69086da28`. It retains v0.54.0, v0.55.0,
and v0.56.0 fixtures and adds a sanitized v0.60.0 fixture for session context,
tri-state activity, current limit shapes, and stream-v2 events.

On September 21, 2026:

- all 76 JVM tests passed;
- Android lint, debug assembly, R8 release assembly, and local documentation-link
  checks passed;
- all 31 instrumentation tests passed on the API 36
  `TokenMonitor_API36_Pixel10ProXL` emulator (`emulator-5554`), including the
  stream-v2 request header, dashboard/widget ownership switching, fixed widget
  geometry, 48 dp controls, saved/offline/stale states, and page cycling;
- production renders of Overview, Limits, Breakdown, and Activity were captured
  together at 320×180 dp in Default. The final pass increased the shared type
  scale; restored brighter cyan, mint, and coral accents; kept sparse Breakdown
  names and values distinct; and strengthened the Activity chart and heatmap
  without changing the 1.82:1 card;
- phone inspection of r2 confirmed One UI allocated 406×215.8 dp while the
  renderer supplied only a 560-pixel-wide raster. r3 renders at launcher density,
  producing a 960-pixel-wide card for the 320 dp emulator case; instrumentation
  asserts the density-matched bitmap size and unchanged 1.82:1 aspect ratio.
- the r3 production APK was signed with the established release certificate and
  installed successfully on the API 36 emulator. It reports version code
  `600003`, certificate SHA-256
  `eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
  and file SHA-256
  `4c0d95fe4dc0ef5fe2d31d481bfd6a0a7ab0e46a11efd3959a0b6437bdf928ab`.
- r3 phone screenshots showed that density alone did not correct the layout.
  r4 added shared layout bands, but its phone captures on the Galaxy S25 Ultra
  (September 21, 2026, saved under `local-private/widget-r4-phone-2026-09-21/`)
  still showed the Overview stat column overlapping, oversized Limits and
  Breakdown rows, and a Samsung-substituted monospace face. The emulator
  comparison that had passed r4 used a two-row fixture, which hid all of that.
- r5 replaces the page bodies with one measured grid (`docs/WIDGET_SPEC.md`,
  `WidgetDeckGrid`), bundles a JetBrains Mono subset so the phone and the
  emulator draw the same face, and renders the design test from the dense
  showcase fixture. All four pages were rendered at 250×110, 320×180 and
  360×220 dp in Default and Porcelain on the API 36 emulator; the 360×220
  captures are the gallery images and were placed beside the concept cards for
  the owner's approval.
- r5 also repairs eleven vendor logo drawables whose compact SVG arc flags
  Android rejects; the dense fixture's Qwen model was the first render to hit one.
- the r4 production APK (`600004`, file SHA-256
  `ba7dd7a4b90385586985148d2a9f16ef05f278715a7df6c7ed056be1c2628de2`) was
  installed on the Galaxy S25 Ultra and is the build the r4 phone captures show.
- On September 22, the independent review gate found and corrected the bitmap-cap
  enforcement, root-directory font-subset generation, and third-party notice.
  All 81 JVM tests, all 31 API 36 instrumentation tests, lint, debug assembly,
  release assembly, documentation links, and the final diff check then passed.
- The signed r5 APK reports version code `600005`, package
  `io.github.theminionooo.tokenmonitor`, certificate SHA-256
  `eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
  and file SHA-256
  `0acb04d601a5b45700580d4511512038ea5247dece06dc7257d791c592bad990`.
  Its certificate matches the installed r4 release.
- On September 22, the signed r5 APK was installed in place over r4 on the
  Galaxy S25 Ultra (`SM-S938U`) without uninstalling. Android retained the
  original September 21 install timestamp, pairing data, and One UI widget ID
  `42`; the launcher continued to allocate 406.04×215.82 dp at 2.8125 density.
- Overview, Limits, Breakdown, and Activity were captured from that preserved
  One UI widget under `local-private/widget-r5-phone-2026-09-22/` and compared
  with `local-private/widget-r5-qa/concept-vs-r5.png`. The final Breakdown pass
  reserves a measured eight-unit gap between the tool token total and its share,
  keeping `12.2M` and `100%` distinct without changing the other three pages.
- The bounded physical battery gate found no active Token Monitor service or
  app wake lock with Saved off. The Android CLI instrumentation helper used for
  an earlier inspection was not installed when final phone evidence was taken.

## Current development checks

The local suite uses the isolated preview package on the API 36
`TokenMonitor_API36_Pixel10ProXL` emulator (`emulator-5554`).

- 85 JVM tests cover v0.54.0 through v0.61.0 protocol parsing, throughput capability
  boundaries, network boundaries, storage and shutdown, history aggregation,
  widget data, the widget grid, and presentation rules.
- 31 instrumentation tests exercise navigation, filtering, period changes, day
  details, search, connection cancellation, both widget families, all four deck
  pages, responsive rendering, theme rendering, and Live controls.
- Lint completes with no errors. Remaining warnings are reviewed separately and
  are mostly narrow-widget typography or platform-preview guidance.
- Documentation links are checked by `node tools/check-docs.mjs`.

Run the same checks locally:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PtokenMonitorPreview=true
.\gradlew.bat :app:connectedDebugAndroidTest -PtokenMonitorPreview=true
node tools/check-docs.mjs
```

## Physical-device coverage

On September 8, 2026, r19 was installed over an earlier signed release on a
Galaxy S25 Ultra with pairing preserved. The 4×2 and 4×3 widgets and the Live
notification rendered and operated on the phone.

r20 also completed a 25-minute screen-off widget Live measurement on the same
phone. Android attributed about 1.54 mAh to the app and reported 52.7 MB PSS, but
the session received 104.19 MB and posted 698 notification updates. Those results
led to the current source's 30-second stats refresh and notification deduplication.

The signed v0.55.0 r1 candidate was installed in place on the Galaxy S25 Ultra
on September 10, 2026. Its certificate matched the installed app, and Android
preserved the app UID, original install time, pairing, and existing responsive
widget. The separate pages widget exposed Samsung's `StackView` card-fan
presentation: multiple scaled and offset pages were visible at once, so r1 is
not a publishable candidate.

The v0.55.0 r2 fixed-card experiment was rejected because it replaced the requested
swipe interaction. r5 keeps the native four-item `StackView` and constrains every
adapter child to one wide 4x2 canvas. Instrumentation verifies the same measured
canvas across Overview, Limits, Breakdown, and Activity at small, medium, and
large allocations in both interface themes. The launcher may still scale and
offset rear cards as part of its native stack presentation.

The signed r5 APK reports version code `550005`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`4da0a340cec7e7ef4d188fd30713babe2fbe760d7da1839ba770e5d0b94bc92c`.
It was installed in place on the Galaxy S25 Ultra with UID `10280` and the
September 6 first-install time preserved. The 28-test emulator interaction suite,
including the four-page deck and fixed picker preview, passes. Final launcher
inspection remains pending because the phone locked after installation.

On September 12, the four r5 deck pages were inspected on the Galaxy S25 Ultra.
Samsung allocated a 5-column by 2-row host with a reported 406 by 215 dp size,
while the renderer applied a second 90% height reduction. The v0.56.0 r1 fix
uses the full launcher-provided height within a 1.82:1 wide-card cap and replaces
weighted Breakdown rows with top-aligned content-height rows. Instrumentation
verifies one measured canvas across all four pages and covers the sparse one-tool,
one-model state. Physical-phone inspection of r1 then showed that One UI could
still clip the lower part of each child hierarchy even though the measured roots
matched.

The v0.56.0 r1 APK reports version code `560001`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`70208ae186eb12d1510c196b57f1bc2c5be4d2964f64117dd804315fecdd5415`.

The v0.56.0 r2 candidate replaces each child hierarchy with one complete 1.82:1
bitmap while retaining native StackView swiping and separate 48 dp-or-larger
Refresh, Live, and Open targets. The four frames have identical bitmap dimensions,
and alpha-preserving output is capped at 560 px wide to stay below the per-item RemoteViews
transfer limit. The 72 JVM tests, lint, debug build, and all 29 API 36 emulator
instrumentation tests pass. Physical-phone installation and One UI inspection are
the remaining release gate.

The signed r2 APK reports version code `560002`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`1e2c999c119cf3c03c9ffbf78597be5a5e7aeb223d4d7be5d6f76a18f08d4d4a`.

The v0.56.0 r3 candidate applies one shared typography scale to the four rendered
pages: section headings, primary values, body rows, and secondary labels now match
across page boundaries. Only the overview token total remains larger. Emulator
captures were compared together at the same 360 by 220 dp allocation, and the
full 72-test JVM suite, lint, debug build, and 29-test instrumentation suite pass.

The signed r3 APK reports version code `560003`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`b561066b217bfa5a49856f11e1e328a14ab3a079013cba4c05b1090192175d7e`.
Physical-phone installation and One UI inspection remain pending because the
phone was not visible over ADB during final packaging.

The v0.56.0 r4 candidate replaces the launcher-controlled collection with one
full-size 1.82:1 card. Forty-eight-dp left and right edge targets change the
locally stored page, and four dots show the current position. A hosted-widget
test verifies both buttons change the rendered page; page changes do not start
a request, timer, service, or wake lock. The old collection service and mutable
PendingIntent template are removed. The 72 JVM tests, lint, debug and signed
release builds, documentation checks, and all 31 API 36 instrumentation tests
pass. Design QA compared the selected mockup with the final Android render and
passed after navigation gutters were added.

The signed r4 APK reports version code `560004`, matches certificate SHA-256
`eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`,
and has file SHA-256
`5aee9e2bd59582f08abab295c76272b8cbc994b346e55b1f79a7d7ec009216e4`.
It is published as GitHub release `android-v0.56.0-r4` with that APK and
checksum file.
Physical-phone installation and One UI inspection remain pending because only
the API 36 emulator was visible over ADB during final packaging.

The phone's usage history also records a Token Monitor widget Live foreground
service from about 01:54 to 05:15 on September 10, substantially longer than the
one-hour requested session. Current battery statistics were reset after that
interval and cannot assign its exact mAh share; the production service was not
running when the v0.55.0 r3 candidate was installed.

The September 12 battery investigation instead identified
`com.android.cli.interact.instrumentation` as the material drain source. Retained
charged-cycle statistics attribute about 32h 11m of foreground-service time and
about 10h of CPU time to that package. Samsung's battery UI independently showed
13h 37m of background time and 3h 27m of CPU for the current day, with no screen
time or wakes. The helper is test tooling rather than part of Token Monitor; it
must be force-stopped and uninstalled after the final physical-phone capture.
It was absent during the final r4 emulator cleanup, and the emulator was shut down.
