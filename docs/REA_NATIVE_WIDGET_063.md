# Mac 系统桌面 Widget 原生逆向证据

日期：2026-10-14（Asia/Shanghai）。本报告对应用户确认的 Mac **系统桌面 Widget**，即“编辑小组件”加入桌面的 WidgetKit 扩展；不把 Edge Dock、悬浮窗、迷你窗口或主窗口卡片混入参照。它是 Android 本轮统一候选版本的原生调查附件，不代表所有界面、真机或发布验收已经完成。

## 目标身份和工具

目标是本机已安装的 `/Applications/Token Monitor.app/Contents/PlugIns/TokenMonitorWidget.appex/Contents/MacOS/TokenMonitorWidget`，共 1,612,656 字节，Mach-O arm64，SHA-256 `62d4514bc6722c03fd901b7ca290c066b082456bba0505b770f07790c20a76f5`。

REA 读取扩展 Info.plist 确认：版本 `0.70.0`，bundle ID `com.javis.tokenmonitor.widget`，WidgetKit extension，UI 版本 `47`、snapshot schema `10`、构建 revision `ac4eeb2c4aea`、最低 macOS 14。REA Evidence：`ev_30f79ffbe7c88b26b786a21454ec8f41941bfdad502e7763af408e41ca6bc59f`。二进制架构、身份和链接依赖来自 REA `inspect_macho`，Evidence：`ev_4bdf3a0b61f588112ded5b4ceba61cc2ea4cedb45a336ef5ee5627e24b50fc31`。

实际读取的已安装 skill 为 `/Users/bing/.agents/skills/reverse-engineer-anything/SKILL.md`（版本 37），并读取 native / recovery 指南。实际执行使用已安装 `rea-agents 6.4.0` 的公开 CLI；既有 REA MCP 注册保持不变。

根据本轮明确授权，将 [官方 Ghidra 12.1.4](https://github.com/NationalSecurityAgency/ghidra/releases/tag/Ghidra_12.1.4_build) 和 [官方 Adoptium Temurin 21 ARM64 JDK](https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=aarch64&image_type=jdk&os=mac&vendor=eclipse) 放在独立 `work/rea-toolchain`。下载校验如下；两个值都与官方记录一致：

| 包 | SHA-256 |
| --- | --- |
| `ghidra_12.1.4_PUBLIC_20260921.zip` | `ddac49f903da9d5bac833e5cc79395098b9c33cfd3279be5f31bd00387d2d4db` |
| `OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.12.1_1.tar.gz` | `3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998` |

用现有 Apple clang 21 / bison 2.3 构建 Ghidra 原生 ARM64 组件，命令为 `make -j4 ghidra_opt ARCH_TYPE='-arch arm64' OSDIR=mac_arm_64 ADDITIONAL_FLAGS=-w`。成品 `decompile` 为 Mach-O arm64，SHA-256 `0bc01f009db2ca184011ce65f6b27f1469d0a3eda504e49e6055dc5865bb014b`。REA `doctor --provider ghidra --target <实际 Widget 二进制>` 的 Ghidra version / headless / native decompiler / ARM64 / JDK 检查均健康。临时 CLI wrapper 仅为子进程设置 `GHIDRA_INSTALL_DIR` 和 `JAVA_HOME`，不改 Android JDK、已有 MCP 配置或运行中的 Token Monitor。

## 实际 REA 调用和结果

证据目录：`evidence/widget-settings-063/rea-native/`。完整调用参数、用到的公开 MCP schema、JSON-RPC 响应、Evidence、profile 和文件指纹均已落盘。`evidence-index.json` 是可快速复查的索引；`target-toolchain-manifest.json` 记录目标、下载源和安装范围。

| 调用 | 目标 / 地址 | 有效结果 | Evidence |
| --- | --- | --- | --- |
| CLI `inspect-macho` | 实际 Widget Mach-O | arm64、目标 SHA、UUID、链接依赖 | `ev_4bdf3a0b61f588112ded5b4ceba61cc2ea4cedb45a336ef5ee5627e24b50fc31` |
| CLI `inspect-plist` | App 根 + 扩展 Info.plist 相对路径 | 版本、WidgetKit 扩展、UI/schema/revision | `ev_30f79ffbe7c88b26b786a21454ec8f41941bfdad502e7763af408e41ca6bc59f` |
| CLI `search --kind procedures --mode regex` | WidgetSnapshot / timeline / refresh 定向名称 | 查询完成，返回 0 匹配；命名形式和编译内联影响匹配，不能据此宣称功能不存在 | `ev_82dea6019a03f16b3dd2e121bd166bb59cd89faf27dc50b73c0c3254148728d2` |
| CLI `function` | `0x10001faa0` | `WidgetTimelineFactory.entry` 完整 analyzed body、伪代码、调用和引用 | `ev_0d51b0e5965fea69c3f43249553de1d6d5acffa4a8b162c329e766ae0f4250ba` |
| CLI 启动临时 stdio session：`analyze_function` | `0x1000315d4` | Refresh AppIntent resume 调用 `WidgetCenter.reloadAllTimelines()` | `ev_2ba102ae926bc635f75ab0e3c1876860b73ec34c007ca3f1340731810d283424` |
| 同一 session：`analyze_function` | `0x1000036f4` | `status.isStale` 或 `Date.timeIntervalSince > 1200.0` 进入状态视图，否则显示 dashboard | `ev_6814b4dae3f8f9129c7965b74edefa97fbea90973f41924a52b237abcf64de3a` |
| 同一 session：`analyze_function` | `0x1000219c8` | Summary timeline resume：`Date.addingTimeInterval(900.0)` → `TimelineReloadPolicy.after` | `ev_7e51675f674eb294485dd680d0bfcfcd9e3b1a046e4212c58415d754a1d89881` |

最后三个函数在由公开 `rea mcp` CLI 启动的临时 stdio 会话中执行，复用一次 Ghidra 导入；先实际 `initialize` / `tools/list` / `open_binary`，再定向分析、`export_evidence_bundle` 和 `close_binary`。它不写任何已注册服务器设置，关闭成功且无常驻服务。调用记录见 `scoped-session-calls.json`，完整 retained bundle 见 `scoped-evidence-bundle.json`。

所有深层分析实际使用 Ghidra `12.1.4`、`ghidra-default`，profile digest 为 `20565088524a2d0359b9b96f46ac48e679d774c7b0092c56abc06de8fdbec11c`。导入是 `ephemeral-source-immutable`；分析临时数据库关闭后删除，不执行或写入目标应用。

## 原生结果支持的边界

1. **共享缓存和周期选择。** `WidgetTimelineFactory.entry` 在 `0x10001faa0–0x10002097f` 的伪代码和 callees 记录了 `NSFileManager.containerURLForSecurityApplicationGroupIdentifier` → `URL.appendingPathComponent` → `Data.init(contentsOf:)` → `JSONDecoder.decode` → `WidgetSnapshot.selecting(period)`。内嵌 Swift small-string 的两个常量解出 `snapshot.json`，与准确构建源码相互印证。读失败形成 optional nil；预览分支才选择 placeholder；还存在 `TimeZone.current` 和 `WidgetActivitySelection.resolvedDate`。这些是静态分析结果，不能当作运行截图。
2. **刷新和系统调度。** `0x1000315d4–0x10003162b` 的 Refresh resume 直接调用 `WidgetCenter.shared.reloadAllTimelines()` 后返回 IntentResult，没有在这段完整 analyzed body 中发现网络调用。`0x1000219c8–0x100022673` 的 Summary timeline 以 900 秒后的 `.after` 请求后续刷新；这是 WidgetKit 请求策略，不能承诺系统严格每 15 分钟更新。该结论不扩展为“整个 Mac App 不联网”。
3. **过期展示。** `0x1000036f4–0x100003a6f` 明确包含 `status.isStale || 1200.0 < timeIntervalSince` 分支，过期分支使用 `WidgetRefreshButton` 包装 statusState，正常分支才使用 dashboard。准确源码将过期提示文案设为“Data may be stale”，可信更新时间仅取 `status.sourceUpdatedAt`；`generatedAt` 不替代源同步时间。Android 按用户要求保留可信缓存数字并标状态时，应作为原生适配差异记录。

## 准确构建源码辅证

从本机 `token-monitor-hermes` 已有 Git 对象导出 revision `ac4eeb2c4aea239dd94b67f074c73f69aafc46f8` 的定向文件到 `reference-source/`。这是历史源码辅证，**不替代以上实际 REA 原生调用**。下表除明确标注原生确认的部分外，都尚需运行界面核对。

| 维度 | 准确源码支持的参照 | 依据 / 验证边界 |
| --- | --- | --- |
| Widget 类型、尺寸 | Dashboard `systemLarge`；Summary `systemSmall`；Breakdown、Activity、Quota `systemMedium` | `TokenMonitorWidget.swift`；尺寸是 WidgetKit family，不能换算成固定 Android 像素 |
| 配置 | Summary：period；Breakdown：tools/models + period；Dashboard：period + tools/models + 自动/自选额度 + 最多两账户；Quota：自动/自选 + 最多两账户 | `WidgetConfigurationIntent.swift`；没有独立 Widget device selector |
| 统计周期 | day → Hub/App `today`；month → `month`；total → `allTime` | `macWidgetSnapshot.js`；选择周期的原生路径已确认 |
| 趋势 | day：本地今天结束的 7 日；month：本月 1 日到本地今天；total：逐月。当天历史与 liveToday 取 max；月趋势与 monthly/liveMonth 取 max，避免相加重复累计 | `macWidgetSnapshot.js`；统计输入需在总验收中用相同 fixture 校对 |
| 活动 | 保留最近 182 个有效 daily 记录，强度 `ceil(tokens/peak*4)`；medium 最多 26 周、large 最多 16 周；本地墙钟日键 | JS + `WidgetViewModel.swift` / `WidgetConfigurationIntent.swift`；运行日期点击未由本报告验证 |
| 数字和币种 | compact/full；西文 K/M/B；中文/日文/韩文 localized 万/亿等；usage cost = costUsd × currencyRate，2 位小数；quota 仍按字段自己的 currency | `WidgetViewModel.swift` / snapshot presentation；不得把 quota 余额按 usage 显示币种猜测转换 |
| 视觉 | 所有 Widget 强制 dark；底色 RGB 0.035 / 0.043 / 0.055 + 轻微渐变；主数字浅色、次级 muted；dashboard metric 42pt，分区标题/行 label 10pt；vendor palette 来自 snapshot | `TokenMonitorWidget.swift` / `WidgetViewModel.swift`；属于源代码值，不是 Android 必须照抄的 dp/sp |
| 点击 | Refresh Intent 重新读取 timeline；热图 SelectActivityDayIntent 切换/清除日期，在 AppGroup UserDefaults 按 medium/large family 保存；源码无 widgetURL/Link | Refresh 原生确认；热图 state/交互为源辅证。Android 点击进入相应原生页面属于替代，不宣称与 Mac 相同 |
| 额度自动选择 | `(有余额或窗口?0:4)+(status==ok?0:2)+(过期?1:0)` 升序，同 rank 保留 snapshot 顺序，限制 2 个 | `WidgetViewModel.swift`；过期按 provider status / updatedAt / 20min；缺失字段仍缺失 |
| 无数据 / 过期 | 实际放置的 Widget 无快照显示 Waiting for data / Open Token Monitor once；preview 才示例；过期换成状态视图而不是真实 0 | native entry / content 已确认路径；文案及 preview 语义由准确源码印证 |
| 数据归属 | Widget 消费 Mac 应用写出的当前 snapshot；源配置没有独立设备选择。source freshness 优先使用 `stats.devices` 的 receivedAt/updatedAt 和 stale，不能把手机网络状态等同采集设备状态 | `macWidgetSnapshot.js`；App/Hub 的完整来源选择属于 Electron 和接口调查范围 |

## 失败、兼容性和剩余覆盖

- 首次把 Info.plist 文件直接当作 CLI target，导致 REA 追加 `Contents/Info.plist` 后定位失败；原失败保存在 `inspect-widget-plist-first-failure.json`。按公开 CLI schema 改为 App 根 + `--relative-path` 后成功，未略过失败。
- 官网 main 文档描述了 `REA_GHIDRA_DISABLED_ANALYZERS`，但本机 6.4.0 未实现该环境参数。设置 `Swift Demangler` 的那次查询仍执行默认分析；实际 Evidence profile 也仍为 `ghidra-default`。本轮没有假称禁用成功，没有为了这个差异擅自升级 REA。
- Ghidra 会恢复编译后的结构和伪代码；Swift ABI 的参数名/结构显示可能失真，伪代码不可当作可编译 Swift 源。computed/indirect call 和 memory aliasing 不保证完整恢复。
- `WidgetTimelineFactory.entry` 的 native high p-code 达到 3,000 operation 限制，`truncated=true`、至少 1 个 operation 省略、47 条已知边未保留。其 analyzed body、伪代码和直接 callees 可读，但不能宣称完整跨函数 value-flow 证明。其余覆盖以 `evidence-index.json` 对应记录为准。
- Refresh async 返回存在未恢复的 indirect resume/jump，timeline 函数有 decompiler dead-code/heritage 警告；这些限制已保存在完整 dossier。统计输入、布局尺寸、选择日期、真实刷新时机和 UI 可用性仍需要运行观察。
- 本报告没有执行目标进程、attach 调试、关闭证书验证、读取生产凭据或修改 NAS。REA native UI / WidgetKit 宿主运行观察的尝试及实际截图由本轮总调查证据记录；不能把本报告的静态结果当作运行验收通过。

原生调查的必要安装和有效定向 REA 分析已完成。Android 适配、同数据截图对照、模拟器/真机结果、候选签名 APK 和批准后的发布状态须以本轮统一交接为准。
