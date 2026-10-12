# V5 unified compact trends — 0.2.59

2026-10-13, Asia/Shanghai. This task uses the local Mac Kotlin/Compose project and the existing Gradle, API36 emulator and external formal signing configuration. The baseline is local main13e3669 plus safely fetched remote main087cc6e and V5 branchc04533e. The untracked `AGENTS-original.md` is preserved. Work is confined to branch `feat/v5-unified-compact-trends` and one formal release.

## Scope and stage evidence

- Real Desktop0.69.0/NAS data and network: commitbb43e2f, `HANDOFF_V5_NETWORK.md` and `HUB_API_CONTRACT.md`. The authentic TLS check found main-thread idle-pool closure; Android cleanup now captures original pools and closes them onIO independently of a cancelled ViewModel scope. Same-test-APK strict Conscrypt baseline fails and repaired code passes. Production services/configuration are unchanged. Raw private payloads, credentials and real-data screenshots are never archived.
- Performance: commit47b2418 and `V5_PERFORMANCE_EVIDENCE.md`; three processes per side, fixed geometry and identical full model fingerprints. Dense projection P50 improves5.392→4.626ms, Kline1.028→0.663ms; ordinary projection and unchanged preparation do not show a general improvement. This is not physical FPS evidence.
- UI: `CompactUiTokens` removes remaining-viewport stretch in the main preview and independent Dashboard; primary160dp and secondary120dp preferences grow for actual Android font conversion. Short screens scroll rather than clipping. Overview metric text wraps naturally, chart modes and exact record data remain intact. Record row vertical padding12→6dp with a48dp minimum target. Legends retain natural text height, complete names and an independently scrollable120dp cap. All chart data/proportions, zero markers and source semantics remain.
- Shared design contract: `COMPACT_UI_GUIDELINES.md` and `.ui-craft/tokens.md`; Home/Settings source geometry and fonts are unchanged. Existing theme, reduced-motion and Kotlin/Compose architecture remain.

## Same-condition UI comparison

Actual1080×2400px/440dpi/font1.0 (~393dp), identical public data, zh, both themes. Final matrix normal-height results match the measurements below; enlarged text intentionally grows naturally.

| Region | Before → after (dp) | Change |
|---|---|---|
| Main plot | 398.909→160 | -59.9% |
| Main complete item | 600→342.545 | -42.9% |
| Dashboard bars | 435.636→160 | -63.3% |
| Dashboard Kline | 481.091→160 | -66.7% |
| Record bars/Kline | 180→160 | -11.1% |
| Record line | 180→120 | -33.3% |
| Overview metric cell | 69.818→56.364 | -19.3% |
| Interactive record row | 64.364→52.727 | -18.1%, ≥48dp |
| Main metric card / ordinary legend | 51.273 /45.455 unchanged | Preserve existing natural sizes |

Before/after screenshots: `baseline/{dark,light}-corrected/` and `ui-matrix/393-1.0-{dark,light}-zh/`. Native font2 screenshot review additionally discovered overlapping fixed date ticks; the final measured native placement retains first/last dates and five numeric ticks, with bounds/nonoverlap assertions. Only visible intermediate date labels adapt; all30 fixture records and exact detail actions remain.

## Baseline and failures

Both themes use the same public fixture and actual1080×2400px/440dpi/font1.0 window (~393dp). Full screenshot and measured heights are archived under `evidence/v5-unified/baseline/{dark,light}-corrected/`. Baseline final methods pass2 per theme using the frozen old app and corrected test-only APK. The original baseline app SHA is0d36f26d6bca3239e8b0077ee7512613f6e32299f5978654e5275c962d6800f9; this local reconstruction is distinct from the published0.2.58 APK.

New test fixture failures (DpRect imports, resource-language lookup, expected ungrouped number) and their APKs/logs are retained. Perf pilot omitted a required default color and failed before timing; it is excluded from measured results. No product failure is hidden or counted as passing. A pilot width rounded393dp to1081px; final comparisons restore baseline1080px rather than claiming the pilot was the same window.

The first27-case affected UI regression had25 passes and two failures. The inherited motion test still expected viewport stretch; the corrected test now requires viewport height invariance AND actual font-driven geometry growth, retaining normalized progress/interruption/source-endpoint assertions. A Home/Settings44dp check failed from subtracting floating-point Dp coordinates (43.99994dp); it now verifies the unchanged44dp minimum as exactly rounded native pixels, retaining the45dp compact maximum. Both complete3-case classes pass on the final repaired product (6methods). These are test-only corrections, with no Home/Settings product changes or relaxed targets. First log: `regression/ui.txt`; corrected log: `regression/ui-corrected.txt`.

## Inherited evidence

The0.2.58/code67 Home/Settings acceptance on owned API36 (320dp1.0/light,411dp1.3/dark,320dp2.0/light), four numeric fonts, independent footer actions and immediate icon scaling is inherited only for unchanged geometry. This version adds a scoped nonregression smoke. The0.2.53 Xiaomi13 Ultra navigation/settings/about checks remain historical, not new V5 phone acceptance. Unchanged B1 freshness reuse and dense atlas behavior retain their recorded scope; relevant preparation/cancellation, lifecycle, network and storage tests are rerun here. Full physical-device FPS, cellular switching and long-duration behavior are not inferred from emulator tests.

## Final acceptance and publication

Release unit419 passed/0 failures/0 errors/0 skipped. Final Lint0 errors/93 existing warnings/1 hint; notice inventory67 verified. Window matrix36 combinations320/393/411dp×system1.0/1.3/2.0×light/dark×zh/en passes, two methods each; two extra actual-system/application-largest combinations pass (38groups/76 executions of the same2methods, not76 independent test cases). Final repaired source runs25 network/cache/lifecycle/freshness/navigation/strictTLS methods, all pass.27 distinct affected UI regression methods are covered as described above; unaffected25 pass on identical UI source before the isolated pool cleanup fix, and both corrected classes rerun successfully after it. Existing preparation pixel/cancellation and reduced-motion/STOP tests pass. No unmodified full historical Android suite or full physical-device suite was repeated.

Real Lucky via transparent TCP route and realLAN via authenticated read bridge pass, with3 authoritative periods/timed rates and actual metadata preserved.65sec steady realLAN observation receives2heartbeats, background upstream becomes zero. Direct simulatorLAN refusal remains correct; direct public/cellular/physical paths are explicitly unverified. Same-signature67→68 upgrade retains appId/two public encrypted files; final repair rereads the old snapshot/history and preference. These limitations remain in the Release notes.

Final signed APK/tag/source fingerprint and GitHub download receipt are recorded in `GITHUB_PUBLICATION.md` and the release-manifest asset. AGP embeds Git commit metadata in`META-INF/version-control-info.textproto`; builds after source commits therefore change APK SHA even with identical runtime content. Release verification compares every ZIP entry payload (allowing only this explicit commit-metadata change), checks the embedded final commit, signature/package/version, installs the final bytes and reruns a final scoped UI/strictTLS/live-Lucky check. Candidate hashes are not substituted for formal asset hashes.

Evidence root: `evidence/v5-unified/`; final delivery: `dist/release-0.2.59/`. V5 stops after this single publication. No later development round is authorized by this handoff.


## Published receipt — 2026-10-13

Formal v0.2.59/code68 is published at https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.59 (not draft/prerelease). Fixed source/tag commit: `a509800a062732cb8ecd9820458d48440a199f3c`; both main and the V5 development branch were pushed. Formal APK SHA256: `06375896d1d9d4083ca9b39ab6198b4133942dff41633f5be075dd56bafcd3e7` (11,472,453 bytes). All four published assets were downloaded after publication and match local hashes individually; receipt: `evidence/v5-unified/release-gates/download-verification.json`. Final APK installation, UI, strict TLS, real Lucky relay and old encrypted-store recovery checks passed on the exact final bytes. Publication documentation is appended without rebuilding APK or moving the fixed tag. V5 is complete; no subsequent round is started.
