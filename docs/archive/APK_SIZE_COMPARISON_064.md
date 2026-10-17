# Android APK 体积对比：0.2.64 与 The-Minion-oOo 发布包

2026-10-15，Asia/Shanghai。本轮只下载公开 APK、读取固定提交源码、检查包元信息和 ZIP/DEX；没有安装或运行对方应用，没有修改本项目产品、启用 R8、合入旧瘦身实验、操作手机或 NAS。

## 结论

对方最新正式 APK 实际为 **2,483,408 bytes（2.368 MiB）**；本项目 0.2.64 为 **15,238,142 bytes（14.532 MiB）**，约为对方 **6.136 倍**。压缩后的 DEX 差 **8,243,156 bytes**，字体差 **3,603,855 bytes**，两项共同解释总大小差的 **92.883%**。

对方正式 Release 配置开启代码优化与资源裁剪，字体仅打包两份 JetBrains Mono；本项目代码优化关闭，保留四份已裁剪的 Token UI/Token Data 字体。对方使用平台 HttpURLConnection，本项目使用 OkHttp/Okio，并另外声明 Gson。这些都是可核对的实现差异。**不能把全部 DEX 差额归因于 R8，也不能据此承诺本项目优化后达到 2 MB**：不同产品的实现、依赖和功能仍有差异，本项目收益必须用同一源码的独立 A/B 构建测量。

这不是 ARM 单架构包与通用包的比较，也不是对方把所有翻译都删掉：双方均包含四种 ABI，四份原生 path 库逐字节相同；资源表语言配置数量为 87 与 86。双方还打包了同一份 678,922-byte 品牌图标。对方保留两个 Widget 和一个 Widget Live 前台服务，本项目 0.2.64 已移除 Widget，因而不能把对方较小解释为“少了 Widget”。

## 固定样本与来源

| 项目 | 本项目 | 对方项目 |
|---|---|---|
| 仓库 | Gott-mit-Uns/token-monitor-android | The-Minion-oOo/token-monitor-android |
| 样本 | 0.2.64 候选／正式发布沿用的相同 df7 字节 | latest 正式 Release android-v0.70.0-r1 |
| 产品提交 | ab9a6acee07ddee302f23dd7e033cd19803e9f2e | 3eb949eda132df2c70a0e360437c65923d92c905 |
| 对方调查时 main | — | a1777e66a2c853121b07b124ceaadce12d80f51a |
| APK manifest 版本 | 0.2.64 / code73 | v0.70.0 / code700001 |
| 包名 | dev.bing.tokenmonitor.nativeclient | io.github.theminionooo.tokenmonitor |
| 最小／目标 API | 26 / 37 | 26 / 37 |
| 实际文件 bytes | 15,238,142 | 2,483,408 |
| SHA-256 | df7ab620f0ca9d086ff30da761a232cb5bd99a9053356a3688c61006f1ab8c56 | bfc4882613169f6090eb381c512acb86d3f0ba1228724df52e4c4e4491648ffd |
| 原生 ABI | arm64-v8a、armeabi-v7a、x86、x86_64 | 相同四种 |
| aapt locales 配置数 | 87，含默认配置 | 86，含默认配置 |

GitHub 官方 API 返回对方 latest 为非草稿、非预发行，发布时间 **2026-10-14 20:59:26（Asia/Shanghai）**。公开下载文件的大小、重新计算 SHA-256、相邻 `.apk.sha256` 和资产 API digest 一致。对方 tag 是直接指向产品 commit 的 lightweight tag；main 比该提交多一个文档／发布记录提交，构建配置和产品代码未变。本调查以 tag 的固定源码为准。[对方正式 Release](https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.70.0-r1)、[产品提交](https://github.com/The-Minion-oOo/token-monitor-android/commit/3eb949eda132df2c70a0e360437c65923d92c905)。

两包均未出现 debuggable 标志。对方签名验证通过，其公有证书 SHA-256 为 `eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c`；与本项目的既有证书不同，不能相互覆盖升级。这里只比较安装包，不比较测试 APK，也不把两项目视作可互换版本。

本项目 APK 的内嵌 VCS 字段为 `NO_VALID_GIT_FOUND`，产品提交由既有 candidate-manifest 外部关联；本报告没有把它写成内嵌提交校验。报告写入时发布由主任务独立推进，本调查不代替正式发布回执。

## 同口径 ZIP 盘点

“压缩 bytes”是每个 ZIP 条目的 `compress_size`，“原始 bytes”是其 `file_size`。份额使用压缩 bytes / 实际 APK bytes。文件不是全部采用 Deflate：`resources.arsc`、原生库和部分 PNG 采用 stored，故两列可能相同。ZIP 头／目录、签名块与对齐填充单列为剩余开销。原始条目总和不是应用安装后占用，更不是内存消耗。

| 分组 | 我方压缩 bytes | 我方原始 bytes | 我方 APK 份额 | 对方压缩 bytes | 对方原始 bytes | 对方 APK 份额 |
|---|---:|---:|---:|---:|---:|---:|
| DEX | 9,618,870 | 27,190,716 | 63.12% | 1,375,714 | 2,665,296 | 55.40% |
| 字体 TTF | 3,689,677 | 5,645,964 | 24.21% | 85,822 | 182,624 | 3.46% |
| res 文件，排除字体 | 904,776 | 934,763 | 5.94% | 745,999 | 814,525 | 30.04% |
| resources.arsc | 810,320 | 810,320 | 5.32% | 118,216 | 118,216 | 4.76% |
| assets | 69,832 | 209,533 | 0.46% | 7,878 | 7,878 | 0.32% |
| 原生 lib | 37,392 | 37,392 | 0.25% | 37,392 | 37,392 | 1.51% |
| META-INF 等元数据 | 31,074 | 105,781 | 0.20% | 45,208 | 138,669 | 1.82% |
| Manifest | 1,870 | 6,572 | 0.01% | 2,497 | 9,808 | 0.10% |
| 其他条目 | 777 | 1,728 | 0.01% | 777 | 1,728 | 0.03% |
| ZIP／签名／对齐开销 | 73,554 | — | 0.48% | 63,905 | — | 2.57% |
| 实际 APK | **15,238,142** | — | **100%** | **2,483,408** | — | **100%** |

总差 **12,754,734 bytes** 中：DEX 占 **64.628%**，字体占 **28.255%**，资源表占 **5.426%**，其余 res 文件占 **1.245%**；其他项为小差额，元数据和 Manifest 反而是对方略大。各组条目原始总和我方 34,942,769、对方 3,976,136 bytes。

DEX 组成直接读取 DEX header/class definitions，没有整包反编译：

| APK | DEX 数 | class_defs 总数 | method_ids 总数 | 判断边界 |
|---|---:|---:|---:|---|
| 我方 | 3 | 18,119 | 133,671 | method_ids 是各 DEX 的引用表合计，非去重后的实现方法数 |
| 对方 | 1 | 3,018 | 16,303 | 同一计数定义；大量类使用短名，与已开启 R8 一致 |

我方仍可见 10,138 个 `androidx.compose` class definitions、2,129 个 `dev.bing` class definitions。对方大部分类已改名，不能用它剩余的原始应用命名空间数量来比较功能多少。class 数下降也可能涉及类合并，不能将“少一个类”直接等同于“删一个功能”。

## 可证实的构建与实现差异

对方 [app/build.gradle.kts（固定提交）](https://github.com/The-Minion-oOo/token-monitor-android/blob/3eb949eda132df2c70a0e360437c65923d92c905/app/build.gradle.kts) 的 Release 使用 `isMinifyEnabled=true`、`isShrinkResources=true` 和 `proguard-android-optimize.txt`。其 [项目 keep 规则](https://github.com/The-Minion-oOo/token-monitor-android/blob/3eb949eda132df2c70a0e360437c65923d92c905/app/proguard-rules.pro) 说明 JSON 走非反射 kotlinx.serialization 路径，未额外添加项目 keep 规则。实际单 DEX、小 class_defs 总数和短类名与这套配置一致，但不构成同源节省量的因果实验。

对方 [正式发布工作流](https://github.com/The-Minion-oOo/token-monitor-android/blob/3eb949eda132df2c70a0e360437c65923d92c905/.github/workflows/release.yml) 构建 `assembleRelease` 并验证发布身份；配置没有 ABI split、单语言限制或单架构过滤。本项目 [0.2.64 build 配置](https://github.com/Gott-mit-Uns/token-monitor-android/blob/ab9a6acee07ddee302f23dd7e033cd19803e9f2e/app/build.gradle.kts) 明确 `isMinifyEnabled=false`，本轮没有开启资源裁剪。

主要工具链版本相同：AGP9.4.1、Kotlin2.4.20、Compose BOM2026.09.00，Activity Compose1.13.0、Lifecycle2.11.0、kotlinx.serialization-json1.11.0。对方 [版本目录](https://github.com/The-Minion-oOo/token-monitor-android/blob/3eb949eda132df2c70a0e360437c65923d92c905/gradle/libs.versions.toml) 和依赖列表不含 OkHttp／Gson；其 [HubApiClient](https://github.com/The-Minion-oOo/token-monitor-android/blob/3eb949eda132df2c70a0e360437c65923d92c905/app/src/main/java/io/github/theminionooo/tokenmonitor/data/network/HubApiClient.kt) 实际用平台 HttpURLConnection，解析器用 kotlinx JSON。我方 OkHttp/Okio 的代码与 PublicSuffixDatabase 也在实际 APK 中。**不能为瘦身直接把已经验收的网络栈换掉**：取消、超时、SSE、TLS、端点准入、缓存保留和网络恢复均需要重验。

对方固定源码有 71 个 main Kotlin 文件、635,894 源码 bytes；源码行数／文件数不等于编译产物大小，也不作为功能等价证明。其 Manifest 仍声明 UsageWidgetProvider、SwipeWidgetProvider、WidgetControlActivity、WidgetLiveService，源码和资源与我方当前“无 Widget”职责不同。对方字体／语言偏好、网络与更新实现也不相同。本轮没有运行其界面，没有验证两产品每个功能或交互相等；README 的“桌面伴随客户端”定位不能取代完整差异验收。

## 字体与图标：已知成本和改变代价

| 字体 | 我方原始 bytes | 我方压缩 bytes | 含义 |
|---|---:|---:|---|
| Token UI regular | 2,707,668 | 1,780,408 | Inter + Noto Sans SC 已裁剪合成 |
| Token UI bold | 2,708,312 | 1,800,671 | 同一组合的粗体 |
| Token Data regular | 115,020 | 53,676 | JetBrains Mono 派生 |
| Token Data bold | 114,964 | 54,922 | JetBrains Mono 派生 |
| 对方 JetBrains Mono regular | 90,088 | 42,145 | 仅数字／Widget等需要的等宽字体 |
| 对方 JetBrains Mono bold | 92,536 | 43,677 | 同上 |

本项目字体 payload 与当前 res/font 四文件重新比对一致；sfnt name table 也确认 Token UI／Token Data。现有 [字体生成器](https://github.com/Gott-mit-Uns/token-monitor-android/blob/ab9a6acee07ddee302f23dd7e033cd19803e9f2e/tools/build-bundled-fonts.py) 已做 GB2312、中文／全角标点及公开资源字符子集，未把 17 MB 完整 Noto 原始设计字体放进 APK。不能以“删完整 Noto”作为本项目新收益。

对方 [Theme.kt](https://github.com/The-Minion-oOo/token-monitor-android/blob/3eb949eda132df2c70a0e360437c65923d92c905/app/src/main/java/io/github/theminionooo/tokenmonitor/ui/Theme.kt) 普通文本使用系统 SansSerif/Monospace；[WidgetDeckRenderer](https://github.com/The-Minion-oOo/token-monitor-android/blob/3eb949eda132df2c70a0e360437c65923d92c905/app/src/main/java/io/github/theminionooo/tokenmonitor/widget/WidgetDeckRenderer.kt) 读取两份 JetBrains Mono。它的 main/res 没有本项目中文资源文件，不能把 resource-table 中第三方组件的中文配置理解成相同的完整中英文 App 设置功能。

Token UI 两文件合计 **3,581,079 压缩 bytes**，占我方 APK **23.50%**。如果未来改为更多系统字体后备，可能有明显体积空间，但会改变中文与拉丁混排、字宽、字体菜单、旧偏好和 OEM 一致性。此方案必须单独评估和用户接受，不能默认删除 Inter/Noto、只按固定界面文案进一步裁字、缩小字号或用系统字体替换用户已选字体。

双方品牌 PNG 实际 payload 相同，SHA-256 `c784c1f35966d3526a90694a3d4c6988461afa57eefae2458832b97ba09f488c`，678,922 bytes，分别映射到我方 `drawable/app_icon` 和对方 `drawable/token_monitor_icon`。它是两包共同的绝对成本，不解释两包差距；后续可独立研究尺寸／无损表示，须保持视觉、透明通道及多密度表现。

## 优化优先级与本轮停止边界

1. **先同源测 R8 与资源优化。** 固定0.2.64产品源码、工具链、签名、四字体、所有ABI和语言，分别比较原配置、仅代码优化、代码加资源优化。若新 AGP DSL 耦合两者，明确记录，用受支持的 legacy 配置分阶段，或解释无法独立隔离。看实际 task log、mapping、usage、资源保留／移除报告，不只看开关。
2. **字体方案单独评估。** 第一阶段四字体保持同 bytes；只有后续另行授权才比较字体子集／后备／打包字重，以相同数据对照图、旧偏好、缺字、系统大字、性能和真机反馈决定。
3. **依赖或图片仅按证据处理。** 先利用优化后的残留报告定位收益，再判断未使用依赖／资产。换网络栈是独立功能风险，不是默认包体积开关。品牌图标不是差额主因。
4. **不优先裁 ABI、翻译或通知许可。** 原生库总计只有37,392 bytes（0.245%），裁架构收益很小却会减少安装覆盖；删除许可证、破坏中英文／系统语言或把 GitHub 通用 APK 换成 AAB，不能算相同交付的免费瘦身。

Android 官方当前文档对 AGP>=9.3 推荐 `optimization { enable=true }` 与 `.keep` source set，同时明确 legacy `isMinifyEnabled/isShrinkResources` 仍受支持。AGP9.0起开启资源裁剪时采用优化后的资源流程。后续应按**实际9.4.1工具链**选受支持配置，不能机械照抄对方或为此升级工具链。[官方 R8 配置与验证说明](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)、[官方包体积建议](https://developer.android.com/topic/performance/reduce-apk-size)。

本轮只交调查和 [后续优化任务提示词](https://github.com/Gott-mit-Uns/token-monitor-android/blob/cfab5438ed5dac6537c68affb385517ccfa44c0e/docs/APK_SIZE_OPTIMIZATION_PROMPT.md)。正式 0.2.64 保持 df7 APK；旧 R8／瘦身分支保持隔离，没有被此次调查重新授权合并。

## 工具与证据边界

JSON 与 XML 元资料先使用已安装 MarkItDown 转为 Markdown，转换结果已检查。APK 是二进制 ZIP，Markdown 不保留 `compress_size`、DEX header 或签名／对齐字节，因此精确统计采用 Python标准库zipfile/struct、aapt及apksigner；没有运行仓库脚本、执行APK、整包反编译或安装新引擎。

实际调用 REA `open_binary(path=<下载的对方APK>)`，确认 format=apk、archive 和 SHA-256；随后 `inspect_artifact({})` 获得完整中央目录子步骤的归档图。REA Evidence ID：`ev_01d7f1abf6c915e91e5eb7366a13fcbc047ff7e71eda07f7625b3e0547b844a1`，provider=`rea-artifact-graph` v1，root SHA 与公开下载一致。这里只是归档层静态分析，不冒充 JADX 类／方法语义分析或运行观察；签名验证由独立 apksigner 执行。

证据位于 `evidence/widget-removal-devices-064/size-comparison/`：`apk-inventory.json/.md`、两包 ZIP entry清单、aapt badging/resource表、对方release/tag/source-tree原始元资料及转换文本、固定源码摘录、完整下载APK/SHA文件、`upstream/rea-inspect-artifact.json`。计数脚本为 `work/apk-size-comparison/inspect.py`。证据目录不保存凭据或签名私钥。
