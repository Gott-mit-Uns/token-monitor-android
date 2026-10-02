# Install and update

## Requirements

- Android 8.0 or newer.
- Token Monitor desktop with Hub hosting enabled and a shared secret. Android v0.65.0 r1 is fixture-verified through desktop v0.65.0; its physical-phone upgrade is not yet verified.
- A private route to the desktop: Tailscale is recommended; home Wi-Fi is optional.

## Install

Download the signed v0.65.0 r1 APK from the public
[release](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.65.0-r1),
or use **Settings → App updates** in v0.62.0 r1 or newer. It uses the same package
and release certificate as earlier releases, with a higher version code. A
physical-phone in-place upgrade to v0.65.0 r1 has not yet been verified; see
[Validation](VALIDATION.md). The older v0.61.0 r1 app has no in-app updater,
so install a newer release manually once.

To build the current source, install JDK 17 and Android SDK 37, then run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`. It is
intended for development and uses a different signing identity from the release
APK.

To install a signed build:

1. Open this repository's [Releases page](https://github.com/The-Minion-oOo/token-monitor-android/releases).
2. Choose a release, read its compatibility notes and download its `.apk` asset.
3. Open the APK. If Android asks, allow installation from the browser or file
   manager you used, then follow the system confirmation.
4. Open Token Monitor and follow [Pairing](PAIRING.md).

There is no Play Store or F-Droid listing.

## Verify the download

Each signed release will include an APK and a `.sha256` file. Compare the hash:

```powershell
Get-FileHash .\token-monitor-android-v0.65.0-r1.apk -Algorithm SHA256
```

A matching checksum detects download corruption; download both files from the
trusted repository release. The release signing certificate SHA-256 is:

```text
eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c
```

## Update without losing pairing

Install the newer release APK over the existing release. The package and signing
identity stay the same, and the internal version code increases. Do not uninstall
first: uninstalling removes pairing, preferences and the saved snapshot.

Starting with v0.62.0 r1, Settings → App updates checks the latest
published GitHub release when opened. For newer releases with an update
manifest, tap **Download and install**. The
app checks the APK size, SHA-256, package, version code, and release signing
certificate before opening Android's installer. Android may ask you to allow
Token Monitor to install apps; return and tap **Install downloaded update** after
granting that permission. Android still asks you to confirm the installation.
If the release lacks an update manifest, use **View Android releases** instead.
For an installation conflict, check that you downloaded a release APK rather than
a debug/preview build. Keep developer previews in the separate `.preview` package.

After updating, open the dashboard once to verify the saved connection. The
original Usage widget remains resizable; the four-page Pages widget keeps one
fixed 1.82:1 composition. Widget Live is an explicit session and may need to be
started again.
