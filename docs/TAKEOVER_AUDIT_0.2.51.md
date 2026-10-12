# 0.2.51 接管审计与 v0.69.0 对齐规划

日期：2026-10-12，Asia/Shanghai。仅审计/文档；没有改产品源码、创建新APK、覆盖安装、清理数据、重启或升级服务。文档中的下一步不是执行授权。

## 基线及构建结论

源码根目录：`/Users/bing/Documents/Codex/项目分类/AI与软件/token-monitor-android-native`。它是既有原生源码工程，不是WebView移植；目标上游版本仅作规划。

| 核验项 | 结果/证据 |
|---|---|
| Git状态/分支/标签/最近提交 | 当前目录/父目录均非Git仓库；没有.git，**不可查询**，不称干净、不编造提交来源。未初始化/清理 |
| 交付APK | `dist/token-monitor-native-0.2.51-dev.apk`，15,759,362 bytes；本轮重新计算SHA256，与交接及相邻校验文件记录一致 |
| SHA-256 | `ef830691261dcd38d322eafd4e286068856871ea52e1347efbfd4b2123a4d805` |
| applicationId | `dev.bing.tokenmonitor.nativeclient`；namespace `dev.bing.tokenmonitor`；源码与APK manifest一致 |
| versionName/code | `0.2.51-dev` / `60`；minSDK26，target/compileSDK37；aapt实读 |
| 签名 | 本轮apksigner verify通过；Android Debug证书；SHA256 `560f4beedb200ebd4ac974702d3f5417e57929f1293f203c8d20f28b5d732107`。旧交付记录说明与048一致；本轮亦核对证书 |
| 当前源与冻结构建 | `evidence/ui51/final-feedback-build/source-files.json` 511文件全部存在且指纹相同；冻结含app/src与app/build.gradle.kts，**不是所有编译输入** |
| 交付与诊断构建关系 | diagnostic APK SHA `771319a9dbcc077f72b6d76f2849f9911dd6551b9885bfecba689d5bb8accdac`；本轮逐条比较215个ZIP entry payload完全相同，支持产品内容等价，不混淆封包哈希 |
| 封包来源回执 | `work/s5-ordinary-rebuild/build-receipt.md`：offline普通构建exit0，385单元，绑定ef830691与测试APK2b341ba8；该目录现仅保留receipt，不能声称所有原始编译输入/日志俱全 |
| 构建配置 | 既有JDK17、SDK37、wrapper Gradle9.8.0（本机缓存存在）、AGP9.4.1/Kotlin2.4.20、Compose BOM2026.09.00；未安装/升级工具 |

**可复现结论：**当前选定源码与保留成功构建一致，交付与该构建的APK内容等价，具备复建0.2.51-dev/code60的配置与已有成功证据。**本轮没有独立重新构建，因此未验证今天可从完整当前环境重建成功，也不能保证字节级同SHA复现。** 缺Git提交来源、旧回执未覆盖全部编译输入，禁止把内容等价提升为完整可重复构建证明。为了满足“不生成新APK”的边界，本轮以指纹/现有构建产物核验结束。

当前root build文件及wrapper指纹另存，形成下一步可追踪起点，但不会倒填历史构建来源。主证据：[baseline-verification.json](../evidence/takeover-069-2026-10-12/baseline-verification.json)、[source指纹](../evidence/takeover-069-2026-10-12/baseline-source-files.json)。

## 测试：已完成、继承与未验证

本轮只重新执行APK metadata/signature/checksum、选定source及ZIP payload比较、既有XML汇总、连接设备列表、上游diff与公开health读取。没有重跑未受影响的测试套件；以下“既有通过”均有版本/环境边界。

| 项目 | 状态与范围 | 保留证据 |
|---|---|---|
| 单元测试 | 既有385 passed/0 failures/0 errors/0 skipped；本轮汇总74份final-feedback XML一致 | `evidence/ui51/final-feedback-build/unit-results`、build-receipt；包含parser、SSE framing/merge/freshness、网络规则、周期/历史/费用、额度/标题/分组/显示纯函数、动效政策 |
| 普通Android | 既有API36自有AVD：102类252项通过，应用/测试包hash绑定；本轮读suite receipt，未重新运行 | `work/s4-final-ordinary/suite-summary.md`；ordinary包含network/TLS/store/lifecycle/Compose/widget等，旧失败在`evidence/ui51/ordinary-all/run1/batch4` |
| Lint | 最终封包摘要0 errors/93 warnings/1 hint；另一个内容等价diagnostic的原始XML为0 errors/97 warnings/1 hint，本轮实读 | `evidence/package-051/final/build-checks.md` 与 `evidence/ui51/final-feedback-build/lint-results-debug.xml`；**两份来源分开，不称同一报告** |
| 窗口/字体/主题 | 既有18组36项通过；八入口、语言完整、字号和48dp触控 | `work/s2-layout-matrix/matrix-summary.json`、USER_FEEDBACK_048最新首段；实际模拟器窗口，非实体手机 |
| 动效窗口 | 两种系统动效倍率各11项boundary及Home history window通过 | `work/s2-boundary-scale0/scale1b`及`s2-home-window-scale0/scale1`；保留首失败，不将旧虚拟时钟当手机FPS |
| 048→051升级 | 既有模拟器覆盖安装成功；UID10670保持，两个私有文件指纹前后一致，新实现解密旧记录 | `work/s4-upgrade-048-051/run-summary.md`、seed/verify；合成配置，不是真机 |
| TLS与SSE状态机 | 有既有受控网络/TLS/ViewModel/Activity专项；涉及取消、后台、401、network replacement、Retry-After、旧帧隔离 | `HubLifecycleTest`、`ActivityNetworkTest`、`HubHttpLifecycleTest`、`HubNetworkTransitionTest`、`HubEndpointTest`、`HubTlsTest`、`HubRetryTest`、`FreshnessReuseIntegrationTest`；不能代替公网实机 |
| 可选测试工程 | 当前源码75 unit Kotlin、107 androidTest Kotlin（含helpers，不能当测试数）、17 live/3 permission/5 perf/4 capacity | `app/build.gradle.kts` opt-in sourceSets；普通252并不包含所有可选套件；未新执行 |
| 容量/perf | 继承perf50局部freshness复用证据；整体probe/内存并未一致改善，真机流畅度仍未知 | `HUB_CAPACITY_FRESHNESS_REUSE.md`、`evidence/hub-performance50`；192MiB heap边界，不能以局部ms代表整体提升 |
| Lucky电脑端 | 历史系统TLS、私有stats/SSE成功记录；本轮仅公开health成功，4devices/core74 | `work/s5-lucky-https/run-summary.json`及本轮receipt；不是Android公网成功 |
| 真实Hub的LAN桥接 | 既有16次home运行15次通过、1次待查；history两读间隔1.685秒的失败不作通过 | `docs/USER_FEEDBACK_048.md:14`、`work/s5-lan-home-r10.txt`；日志存在，本轮未重跑；P1-09定向定位，不能把历史重复读取等同重复SSE |
| LiveHttpsEntryTest | 既有liveTest尚未通过，合成凭据的Android实际TLS/401行为仍待验证 | `docs/USER_FEEDBACK_048.md:15`；不包含在普通252通过的结论内 |
| 实体手机覆盖安装/数据保留/触摸/弹窗/图表 | **未验证**，adb仅emulator-5556 | 手机清单见UI_PARITY；交接也标记UNOBSERVED |
| Android蜂窝公网HTTPS | **未验证**；旧模拟器该域名解析至LAN路由且不可达 | `evidence/package-051/final/build-checks.md` appSideSuccessRun=false |
| 实机前后台、网络切换、回退、SSE单连接/全量一致性 | **未验证**；同时存在当前策略与新要求差异 | `HubViewModel.kt`状态机；本轮没修改；UI_PARITY成功标准 |
| 当前NAS部署/私有新字段 | **未验证**，SSH192.168.31.12:22拒绝；health只作build参考 | 不读取敏感配置、不尝试重启SSH或绕过安全连接 |

现有测试体系很丰富，但未含v0.69的新字段、关系投影和目标LAN优先策略；这部分属于新任务定向增量，而不是重复跑全部历史检查。

## 上游、风险与实施顺序

上游实际16提交从`5d2db368`到`3f106ae3`；发布说明、固定diff和相关代码已经交叉核对。功能A/B/C/D/E分类、Android与Hub生产端责任、等待租约、关系去重、Grok历史overlay、Antigravity标题/速度/推理及双周期、设备名/图标、SSE有效合同增量均在 [FEATURE_MATRIX](FEATURE_MATRIX.md) 与 [HUB_API_CONTRACT](HUB_API_CONTRACT.md)。

当前Lucky健康build对应旧core74，目标core78；不能因Node server.js没改就说wire合同没变，也不能从health替代鉴权样本。源码中HTTP/SSE均有后台取消与generation保护，没有发现需要本轮立即改线上服务的证据；高风险区是集中式ViewModel、双入口/身份边界、会话新关系与大帧缓存/页面派生。逐项证据、影响和动作见 [ARCHITECTURE](ARCHITECTURE.md)。

P0先保存可追溯基线和安排真机交付保留数据；P1先独立修Antigravity单组双周期，再分别做连接策略、当前Hub能力证据、等待/分组；P2设备resolver和Lucide资源。详见 [NEXT_TASKS](NEXT_TASKS.md)。其中Hub/Agent升级不是本轮执行项。

需要用户配合：提供已授权可连接手机及其实际旧版本，覆盖安装与蜂窝网络实测；必要时恢复现成NAS安全只读访问。完整步骤见 [UI_PARITY](UI_PARITY.md)。不要求聊天提供秘密，不将模拟器结果提升为真机验收。

## 文档入口与本轮完成范围

新增且此前不存在的`AGENTS.md`、ARCHITECTURE、FEATURE_MATRIX、UI_PARITY、HUB_API_CONTRACT、UPSTREAM_SYNC、NEXT_TASKS组成增量索引；保留原PROTOCOL/VALIDATION/ACCEPTANCE/各PARITY与交接文档，不重复生成旧验收记录。JSON证据先用markitdown转换已有ordinary/upgrade摘要到`work/audit-069-source/`；精确hash/XML计数采用结构化直接核验，以免格式转换改变字节证据。上游获取的失败原因与成功来源另存acquisition notes。

本轮到此停止。没有新版本、新APK、产品重构、服务变更或真实手机验收声明。
