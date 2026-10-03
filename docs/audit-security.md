# Cloudflare edition security audit

## Confirmed upstream strengths retained

- The complete Hub connection record, including the secret, is already encrypted with a non-exportable Android Keystore AES-256-GCM key. The IV is randomized by the cipher for each save. Authenticated decryption rejects modified ciphertext. There is no plaintext connection migration source in this release.
- App-private snapshot caches are excluded from cloud backup and device transfer along with all other app files. `allowBackup=false` and both platform backup XML rules already exclude these files. Cache JSON can contain project/session metadata, so it remains private local data even though it does not store the authentication header.
- Network request errors use curated messages and HTTP status, and do not log secrets or response contents. Pairing rethrows coroutine cancellation; network and API resources close in finally blocks.

## Fixes implemented

- All store operations share a process-wide lock, serializing Keystore key creation and complete IV/ciphertext reads and writes across repository instances. Added concurrent-instance instrumentation coverage.
- Secure connection writes and deletion now check the synchronous SharedPreferences commit result instead of silently treating disk failure as success. Pairing assigns the new in-memory connection only after secure storage succeeds; a failed save therefore retains the old connection.
- The independent edition does not automatically query upstream GitHub releases or expose upstream download/install buttons. Update entry points are additionally guarded against accidental use, and the install-packages permission is removed. Existing version/metadata validation remains available to upstream tests, but cannot install an APK signed for a different application.
- Added an instrumentation test for encrypted round trips, randomized ciphertext, absence of plaintext credentials, tamper rejection and deletion, and a JVM test for disabled updater guards. Instrumentation uses synthetic credentials and requires the isolated preview package.

## Scope and remaining limits

- Local caches are protected by the Android app sandbox and backup exclusion, rather than per-file encryption. No application-level protection is promised on rooted or compromised devices.
- Keystore invalidation or an unreadable record requires pairing again; no recovery copy of the secret is stored.
- Secure SharedPreferences commit failures are surfaced, but Android itself can update its in-memory preferences before reporting disk failure. Such a failure is not accepted as a successful connection change.
- Private-network HTTP support remains intentional for existing LAN/Tailscale installations. Public Hub URLs must use validated HTTPS in the connection layer.
- These are source-audit conclusions. Device-test pass status is reported only after the parent implementation runs the test suite.
