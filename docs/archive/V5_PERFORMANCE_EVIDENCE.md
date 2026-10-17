# V5 Android projection performance evidence

V5 measures data projection and chart-target preparation separately before selecting a narrow optimization. This is an Android emulator measurement using deterministic public data, not production Hub timing, physical-device FPS or a claim that the whole app became faster.

## Conditions and fingerprints

The task owns `TokenMonitor_V5_059`, API36, `emulator-5588`. Baseline and candidate use the same signed Release build type, 800×360 source geometry, 1600×720 target pixels, all range, English source collation, per-client attribution and fixed default color `#888888`. Each side uses three independent instrumentation processes; each scenario has five warmups and 15 measured samples per process (45 measured samples pooled per scenario). Projection and preparation clocks are `System.nanoTime`; fixture creation, assertions, model fingerprinting and report I/O are outside measured intervals. P50 is the median; P95 uses nearest rank. Runs are sequential before/after groups, not randomized pairs or evidence of JIT/thermal stability.

Both timing APKs remain 0.2.58/code67; the candidate includes the V5 implementation before the formal 0.2.59 version bump. The formal release is separately built and verified. The original baseline APK remains frozen. The corrected baseline and candidate benchmark methods have identical code and fixtures; unrelated UI test changes in the test APK do not execute during measurement.

| Artifact | SHA-256 |
|---|---|
| Baseline app | `0d36f26d6bca3239e8b0077ee7512613f6e32299f5978654e5275c962d6800f9` |
| Baseline test-corrected.apk | `adb6c5e7849894af59587579b848f462501468a073bb6fb0062ff17637af416c` |
| Candidate app.apk | `f1b5921506e8681cf3a7d8beae86b3a29e12e157468616da8a9495897c101bcc` |
| Candidate test.apk | `e459bb57d984c178e66a922aaf5a7ac56f7d8cbbd9082286508c74c63722e7ed` |

The first perf pilot failed before timing because the fixture omitted the required default palette entry. Its original failure log and APK/test APK fingerprints are preserved in `evidence/v5-unified/performance/baseline/pilot-failed.txt` and `SHA256SUMS`; it is excluded from statistics. The corrected fixture supplies the same fixed palette to both sides.

## Results

| Public workload | Projection P50 / P95, baseline → candidate (ms) | Preparation P50 / P95, baseline → candidate (ms) |
|---|---|---|
| 30 days × 8 keys, BARS | 0.196 / 0.831 → 0.236 / 0.387 | 0.024 / 0.050 → 0.024 / 0.042 |
| 366 days × 80 keys, BARS | 5.392 / 7.585 → 4.626 / 5.188 | 12.746 / 15.851 → 13.097 / 13.945 |
| 366 days × 80 keys, KLINE | 1.028 / 1.751 → 0.663 / 0.950 | 0.026 / 0.684 → 0.024 / 0.047 |

Dense chart-target preparation remains the largest measured stage (~13 ms median). The narrow optimization removes redundant work from projection: validate/group each date once, retain the exact per-row BigInteger sum during attribution folding, and convert the common geometry denominator once per bar target instead of once per segment. Attribution aliases, unknown/duplicate/invalid data handling, key order, counters, axes and coordinates remain unchanged. Background raster work and cancellation/publication guards are unchanged.

The pooled dense projection median decreases 14.2%, and Kline projection decreases 35.5%. The ordinary 30-day projection median increases by 0.040 ms (0.196 → 0.236 ms); it does not show a consistent improvement. Dense preparation median increases 2.8%; that stage was not optimized. Individual dense projection process medians are baseline 7.191 / 4.532 / 4.916 ms and candidate 4.602 / 4.614 / 4.705 ms, so the aggregate percentage is not a promise of identical improvement in every run. These measurements do not establish an overall rendering, navigation, network or FPS improvement.

## Data preservation and inherited evidence

All six successful processes produce identical complete `DashboardTrendModel.toString()` SHA-256 per workload across both sides, including retained daily rows, segment geometry/order, exact counts, legend, issue flags and axes:

- 30 days / BARS: `5a85a3726e626bb61c566ab1d125122b4ffc97d32f037386b84739e95f96c5cb`.
- 366 days / BARS: `5e5972f51cd3d4b1f74eba4914e072cd6fda65afa517f61cecef0394831888c4`.
- 366 days / KLINE: `3b9c993efc32f03d89b6af6b35b0b41c35fa0afac264e4249b2004121e4d0987`.

17 existing `DashboardTrendProjectionTest` unit cases pass, including source-oracle geometry, aliases/collation, missing/duplicate/invalid data and exact-count adaptations. The compact minimum-height case is updated from 200 to 160 dp while retaining the 34 dp axis margin. V5 layout screenshots and font matrices separately validate the new compact geometry; fixed-height timing deliberately does not conflate those layout changes with projection work.

The historical B1 Hub freshness reuse result in `docs/HUB_CAPACITY_FRESHNESS_REUSE.md` is inherited only for its unchanged typed-stream/cache boundaries; it is not counted as a V5 measured speedup or a current real-Hub result. Unchanged dense atlas pixel parity and cancellation/publication checks are covered by the existing `DashboardPreparationScreenTest` in the final regression scope.

Raw results: `evidence/v5-unified/performance/{baseline,candidate}/run-{1,2,3}.{json,txt}`; pooled comparison: `evidence/v5-unified/performance/comparison.json`; build and 17-case unit XML: `evidence/v5-unified/performance/candidate/`. These are local task evidence, distinct from the final release APK fingerprint.
