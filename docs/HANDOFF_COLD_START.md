# Cold-start icon — staged for the next Android update

2026-10-13: user requested this change be pushed to GitHub and included with the next other update. Keep versionName 0.2.59/versionCode 68; do not publish a separate APK, Tag or Release for this source change.

## Agreed behavior

- Cold process startup shows the existing App icon while the encrypted store is read.
- With a saved Hub: keep the icon until the initial health/stats request settles. Successful stats, failed/timeout requests, unavailable network and locally blocked transport all lead to Home. Keep the accepted cache and existing error/Hub source information.
- With no saved Hub: show the existing Hub configuration page after the store check. Store decryption failure retains the existing error and configuration recovery path.
- Normal background return and Activity recreation retain the current page. Subsequent refreshes never reopen the startup icon.
- No artificial minimum display time, extra network request, new dependency or changes to authentication/fallback/SSE policies.

## Implementation

`HubViewModel.startup` is a one-time LOADING → HOME/SETTINGS state, independent of the general loading flag after startup completes. Completion occurs only after the store check and the existing initial request reaches an idle result. The original snapshot-null routing remains the default for existing isolated UI fixtures; production passes an explicit initial route.

`HubApp` keys its composition with a ViewModel-lifetime session identifier: a recreated Activity shares that key, while a new process ignores the previous process's saved page. This addresses both the asynchronous cache/Settings flash and restoring Settings after process death. `HubStartupIcon` displays the existing app_icon.png at 96dp on the themed background.

## Validation status and next-update gate

- `git diff --check`: passed before source submission.
- Added `HubStartupTest`: delayed successful stats, failed request/cache preservation, first install, unavailable network, and no splash on subsequent foreground return.
- Added `HubStartupScreenTest`: icon without navigation/content, transition to Home, Settings retention during later refresh/Activity state restoration, fresh-process key ignoring old Settings, first-install connection controls. Screenshot capture uses public synthetic data only.
- **Compilation, Android tests, Lint and screenshots have not run.** This Windows session has no Java, Android SDK/ADB or WSL distribution, and no configured SSH entry to the existing Mac toolchain. Added tests are pending, not passing evidence. No phone installation or live Hub access was performed.

Before merging/releasing with the next update, run the new two Android test classes plus existing HubLifecycleTest, NavigationStreamScreenTest and MainNavigationScreenTest. Run the relevant unit tests and Lint, inspect the captured startup screenshot in both themes, and verify actual force-stop/cold launch versus ordinary background return. Explicitly cover slow success, offline/failure/timeout, first-install configuration and Activity/process recreation. Use an isolated simulator with synthetic cache; do not clear user data or use production Hub credentials.

The known Mac toolchain paths from prior V5 scripts are `/Users/bing/android-toolchain/jdk-17.0.20.1+1/Contents/Home` and `/Users/bing/android-toolchain/sdk`; connectivity and current availability were not verified in this session. Follow the normal signed release/upgrade gates when this is incorporated into a future version.
