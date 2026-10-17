> 已取消功能的历史资料。Android 小组件已移除；本文件不是现行规格，不授权恢复功能。

# Cached quota widget

The separate **Cached quotas / 缓存额度** home-screen widget preserves the existing Today tokens/cost widget. Both receive updates when the existing foreground app saves its encrypted Hub cache, changes display preferences or forgets the connection. Widget resize/launcher requests also read the same encrypted local cache. `updatePeriodMillis=0`; there are no timers, HTTP/SSE requests, workers, collectors or services.

`HubQuotaWidget` loads through `HubStore` and the existing `HubParser`. Account grouping, compact-window selection, provider ordering, hidden items/providers, used/remaining preference and quota value formatting reuse `LimitDisplay`, `LimitProviderLayout` and `LimitPreferences`. Accounts and periods remain separate. At most two period rows per displayed account appear; additional periods/accounts explicitly link back to the app. Email masking is always applied on the launcher, even if the app preference permits unmasked emails. Connection addresses, authentication fields, device/source details and arbitrary raw response fields are not placed in RemoteViews.

Missing cache, authentication/decryption failure and missing quota readings have different messages. Real zero is preserved. A saved cache older than 15 minutes is labelled stale, and source readings are also labelled stale when their source age is unknown, over 15 minutes, in the future, explicitly stale or past a supplied reset boundary. This conservative presentation threshold never changes the cached quota numbers or asserts that the Hub invalidated them. All views say cache; the absolute UTC save timestamp and age **at rendering** remain available, so a launcher retaining an old RemoteViews snapshot cannot turn the content into a claim of real-time data. No scheduled age or quota refresh runs.

Tap opens the existing Quotas page and uses the established foreground refresh/lifecycle. No network request occurs while rendering or stepping through cached accounts. The Today widget retains its existing click behavior. Initial and repeated quota intents are handled by the app's existing screen routing; navigation does not add a second SSE connection.

Normal layouts adapt between one and three accounts based on host height and system font scale. At enlarged system font sizes, a compact projection uses provider + account ordinal and period labels from the existing formatter; both period values remain visible. The complete masked account identity, model group, details and source warning remain in the accessible row description. This avoids clipping long email domains while retaining separate accounts. A 48dp open action remains available. The app remains the full-account/detail view. The widget supports explicit/system light and dark themes, app-selected English/Chinese and 280dp minimum width/360dp minimum resize height.

Targeted evidence:

- `QuotaWidgetTest`: account separation, dual periods, explicit zero vs missing, used/remaining and hidden providers, source staleness, layout capacity.
- `QuotaWidgetHostTest`: isolated encrypted cache empty/corrupt/stale paths, synthetic multi-account data, no local listener connection, actual Android widget provider binding, zero scheduled update interval, theme/size/font screenshots and text/action bounds.
- Host screenshot scenarios: 280×360dp light/font1, 400×440dp dark/font1, 280×480dp dark/font2, 280×360dp light/font2, with long masked fixture account names.

These test definitions are not a claim that the tests ran. Final build/test results and screenshot inspection belong to the combined release handoff. The host tests use synthetic cache data on an emulator. Physical launcher placement, vendor-specific resize behavior and long-running home-screen behavior require separate physical-device evidence.
