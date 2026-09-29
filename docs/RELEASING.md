# Releasing

## Versioning

The visible Android version matches the verified desktop Token Monitor version.
Android-only releases keep `versionName` and increment the final three digits of
`versionCode` and the GitHub tag revision.

```text
versionName: v0.63.1
versionCode: 631001
release tag: android-v0.63.1-r1
```

A newly verified desktop version updates the visible version and starts its
Android revision at 1. Keep `gradle.properties`, `upstream.json`, README, and
protocol fixtures aligned.

## Release checks

Before publishing:

- Bump version metadata and add concise notes under `docs/releases/`.
- Update README compatibility and widget marketing from production captures;
  do not replace the established hero for a widget-only release.
- Run JVM tests, lint, debug assembly, and release assembly.
- Wait for Android checks and Android interaction checks on the exact `main`
  commit being released.
- Exercise affected dashboard and widget behavior on the emulator.
- Install the signed candidate over the previous release on a phone and confirm
  pairing and preferences survive.
- For networking, lifecycle, or widget changes, check Tailscale, Wi-Fi fallback,
  offline resume, Live/Stop/expiry, and the affected widget sizes on the phone.
- Confirm the working tree contains no keystore, passwords, private addresses,
  personal data, captures, or raw Hub responses.
- Perform an independent read-only review of the release diff and fix validated
  findings.

Record what was actually checked in [Validation](VALIDATION.md). Do not describe
emulator results as phone coverage or design safeguards as measured battery life.

## Signing

Release builds read four environment variables:

```text
ANDROID_KEYSTORE_FILE
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

The keystore and passwords stay outside the repository. Keep an encrypted backup
of the keystore and record the certificate SHA-256 fingerprint separately; losing
the key prevents future APKs from updating installed copies.

Verify a local APK with Android build tools:

```powershell
$buildTools = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name | Select-Object -Last 1
& "$($buildTools.FullName)\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## Publishing

The manually triggered **Android release** workflow runs JVM tests and lint,
builds and verifies the signed APK, creates its SHA-256 file and the
`token-monitor-android-update.json` asset, then opens a draft
GitHub release. It reads the release body from
`docs/releases/android-v<version>-r<revision>.md` and refuses to overwrite an
existing tag. The workflow requires the four signing secrets to be configured
in the public repository; do not copy a private keystore into the repository.
If the workflow is unavailable, sign locally with the same release key and
create a draft with the APK, checksum, and matching update manifest. Check the
uploaded asset hashes before publication.

Check that the manifest's version code, APK name, size, and hash match the
uploaded APK. Review the draft, install its exact APK on the phone, then publish
it. Release notes should state what changed, compatibility, and any important update action;
link to [Install and update](INSTALL.md) instead of repeating the full procedure.

Batch related work and publish when the APK is worth reinstalling. Documentation,
marketing-image placement, and other non-app changes do not need a new Android
revision.
