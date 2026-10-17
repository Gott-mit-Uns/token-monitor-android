# Record calendar behavior

Applies to Android `0.72.0-01` / code76; checked 2026-10-17 against the governance base. Source: `HistoryTrend`, `HistoryReadPresentation`, `RecordCalendarProjectionTest` and the fixed-source audit in [archive](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/archive/INDEX.md). Execution results belong only in [VALIDATION](VALIDATION.md).



The actual installed desktop v0.72.0 renderer extracted by REA is retained in `evidence/compact-ui/desktop-shipped/src/electron/renderer/`. `dashboard.css` defines the 13px title, compact 11–12px controls and 14px page inset; `dashboard.js::renderTrends` uses its existing clamped daily source. That is the density and hierarchy reference. The Android month calendar and 12-month selector are the user's requested mobile adaptation, not a claim that the desktop contains the same picker.

`HistoryTrend` now places the daily/monthly selector and corresponding calendar before inline exact details. Range chips retain the existing `HistoryRanges.summarize` scope anchored to the latest Hub history date. A calendar gap is never a numeric zero. The calendar uses Hub date keys without converting or rebucketing them. Daily cell values reference the same range rows as the displayed total. Monthly cells reference `HubHistory.monthly` directly, with an explicit full-month scope note; daily rows are never summed into new monthly totals. Duplicate date/month keys retain the existing ambiguity rule.

The existing chart, summary cards and annual activity remain available under the inline “Charts and activity” expander. Exact selected row cost, unpriced tokens, client and model detail remain available inline. Main selected day/month, month/year viewport, period, dimension and analysis expansion use saveable state. All data is read from the current accepted history payload, so replacing a payload updates selected details instead of retaining a stale object. Calendar navigation and selection issue no requests. Existing history request identity/cancellation protection remains in the ViewModel.

Known zero, unknown token fields, absent records, unrequested data, loading, failed reads, dates outside the daily range and future dates have separate cell states. Failed reads retain known cached rows; missing rows are marked failed instead of being declared empty. Outside-range cells remain unavailable under the existing range filter. Within that scope, an existing authoritative Hub row always takes precedence over the phone's future-date boundary, including recorded zero, unknown token fields and duplicate-key ambiguity. Only future dates without a Hub row are unavailable. Navigation reaches the later of the phone's current calendar and the last authoritative daily month or monthly year, so returning to the phone's month/year cannot strand a valid Hub row across a timezone boundary. The today marker uses the existing Dashboard `today` argument; production currently supplies its existing local-calendar day. Hub date keys and all usage calculations remain untouched. The Hub aggregate history contract does not supply a single timezone, and its participating devices can have different source zones.

Seven 48dp-wide daily targets fit within the ordinary viewport. A smaller viewport scrolls the 336dp grid horizontally instead of shrinking targets or text. Rows grow with natural text height. The month grid has three columns, four rows. Exact values remain in selected details and accessibility descriptions while cells use the shared compact-token formatter and current compact-unit preference.


## Dimension availability

`dailyProvided` and `monthlyProvided` are true only for wire arrays: omitted/null/non-array dimensions are unavailable, explicit empty arrays are known empty. Projection copies retain these flags and the raw encrypted cache requires no migration. Existing authoritative rows take precedence over the phone future-date boundary; loading and failed history reads retain their own states.

## History-owned read status

Only a matching `HistoryReadReceipt` for the current connection revision and history signature controls calendar status. STARTED means in-flight; FAILED and AUTH_BLOCKED stay history-specific; QUEUED, cancelled, cached, resolved or old-key receipts cannot borrow unrelated alias/pricing/subscription/device loading or errors. Existing coordinator/ViewModel trust, ordering, cancellation and serial busy rules still decide acceptance. Local range/day/month selection issues no reads.
