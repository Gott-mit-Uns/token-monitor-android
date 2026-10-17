# V4 桌面0.69功能与UI交接

起始提交9f45fd270ba7d790d90e50ddb1ee7dda2057baa5，分支feat/desktop-069-parity-ui。继承Antigravity双周期和V3连接策略，不修改生产服务，不推送或发布GitHub。

## 阶段A：适用性边界

依据现有FEATURE_MATRIX、固定上游3f106ae、sessionLive/sessionRows/shared usage的相关函数和当前Android代码，不重复16提交审计。当前生产私有字段仍未验证；Android支持可选v0.69合同不等于生产Hub已升级。

| 功能 | 当前核查与本轮范围 |
|---|---|
| waitingForInput/liveActivity | Android缺失，v0.69合同允许；增加可选消费和30秒租约，旧字段回退。部署字段存在性E |
| 等待批准区别 | 远程合同只有generic waiting及Codex输入等待，无法证明approval原因；不推断批准状态 |
| Codex/native/T3/parent关系 | Android缺失，v0.69合同允许；纯展示分组、成员账目只计一次。缺失/冲突/循环保守处理 |
| Grok Bot | 旧Hub sessionId/models足够支持基本分组；桌面跨历史overlay不上传，不补本地采集 |
| Antigravity标题/重命名 | 已读取title，验证full快照替换；真实生产端授权/更新未验证 |
| Antigravity原生速度 | 已消费服务器timed counters，验证缺失/有效值；不估算官方读数，实际产出E |
| Antigravity推理Token | 已按Hub权威total/history读取；验证不额外加reasoning，实际生产端修复E |
| Antigravity双周期 | 已完成，保留定向回归 |
| Cursor结束 | 既有turnEnded消费，补状态测试；生产端检测不移植 |
| 设备名称 | 展示入口分散，统一本地别名→Hub displayName→deviceId→hostname→device |
| Lucide导航 | 静态图标可对齐固定上游，保留八入口和触摸目标 |
| 零Token本地活动/Windows Dock/collector | 不适用；当前上传合同不提供zero-token/local overlays，不造数据 |

## 阶段计划与验收

A文档→B可选元数据/状态/分组/设备与导航→C左下导航→D设置布局→E关于版本→F最终构建，每组独立提交。左下DesktopViewSwitcher实际是页面轮换/菜单，当前没有全页compact模式；常规文字与窄屏图标呈现复用同一组件，不移植窗口控制。

以下为完成记录。

## 实施与本地提交

| 阶段 | 提交 | 结果 |
|---|---|---|
| 起始 | 9f45fd270ba7d790d90e50ddb1ee7dda2057baa5 | 最新V3/发布文档基线；未退回288b7df |
| A | 71af4f5 | 界定可选合同、桌面专属与部署证据边界 |
| B | 52944e9 | 状态/关系/Grok分组、设备名称、五项Lucide资源 |
| C | 388ec9c | 现有导航自适应、每页查询与滚动保存、单SSE导航测试；复数资源修正 |
| D | 01a135d | 设置标题/摘要堆叠与选项换行 |
| E | 21a9ca926cb72765dc5b446242eb47cdb1374754 | 真实包版本、参考版本、部分对齐、未知Hub版本与UI证据 |
| 边界修复 | dd3254a30ee66e0597d161e1ef20b36e8e73d863 | 设备内部占位值不显示，最终产品源码提交 |

结束提交为本文件与管理文档的最后提交；完整SHA另记evidence/desktop-069-ui/final-receipt.json，避免在提交内自引用。分支feat/desktop-069-parity-ui，无push/Release/生产部署。

## 实际行为

- liveActivity只接受Claude/Codex合法状态和观测时间；30秒后、unknown、未来观测按桌面退回transcript。Codex waitingForInput显示等待回复；归档、turnEnded及10分钟界限按固定桌面函数处理。通用waiting不推断为批准等待。
- 先native/T3展示身份，再parent链接。记账key仍独立；同身份续接与子代理有成员角色，缺失父、循环、自环、人工fork、冲突父保守处理。tokens/models/unpriced用BigInteger，费用用BigDecimal。Home/Sessions复用状态，独立关系/Grok/review组均能打开成员并返回；组内分页可访问。
- Cursor按sand-subagent或正数grok-bot模型分组，不补跨历史本地overlay。Antigravity title重命名、timed output/duration及权威total/history已有消费，补定向验证，不重复加reasoning。
- 设备显示采用本地别名→Hub displayName→id→hostname→fallback，空白不覆盖有效名字，内部__unnamed_device__不展示。导航五项图标使用固定v0.69 Lucide来源，MIT许可保留。
- 用户确认只优化现有导航。文字装不下时采用图标呈现，两个48dp触摸区仍分别支持轮换/菜单，长按菜单保留；没有新增全页模式。完整菜单依旧八入口；PageBrowseState保存每页查询/滚动，与HTTP/SSE无关。
- 180dp设置标题不可见的真实Compose布局失败已复现。修复后空间不足时标题/摘要上下排列，选项可换行，保留原字号与样式、锚点/动效/展开状态。
- 关于页实际显示0.2.51-dev (60)、桌面参考v0.69.0、部分对齐（仅支持的Hub字段）、Hub版本未知（未提供）。PackageInfo读实际包，不把health.version=1、build指纹或Agent版本当成Hub应用版本。

## 验证与证据

证据根evidence/desktop-069-ui/，均为自有API36模拟器与合成数据，不读取私有Hub响应。

| 检查 | 实际结果与边界 | 路径 |
|---|---|---|
| B受影响单元 | 42项过滤范围：首轮41通过/1费用浮点差异；改为精确表示参考输入后全部通过 | phase-b-tests-2/3/4.log；固定12分组+12状态fixture |
| 最终合同/额度/网络策略单元 | 26项通过；与B重叠，不相加为独立数量 | final-build-1.log、unit-results/*.xml |
| 最后设备边界单元 | 10项通过；Device069Test先失败后修复，包括占位值、空白、各名称优先级 | device-sentinel-before.log/xml、device-final-build.log、candidate-3/unit-results |
| 新增UI/缓存/浏览状态 | 6个JUnit方法最终通过；浅深/中英/合成字体1/2矩阵，独立组/返回/full标题更新/缓存过期；PageBrowseState保存与恢复 | candidate-2/ui-result.txt、ui-revised-result.txt、ui-visual-result.txt |
| 网络与既有展示回归 | 36项通过；八页导航不增SSE、后台关闭/前台恢复；V3回退/网络变化、生命周期、旧缓存、会话、额度与连接设置 | candidate-2/regression-result.txt |
| 实际系统窗口 | 393dp/font1深色运行5方法；360dp/font2浅色运行3方法；导航/语言各覆盖中英与standard/largest。About实际文字与主题另含浅深；无LocalDensity伪装系统字号 | system-normal/result.txt；system-large/result.txt及两份public.json |
| 最终视觉检查 | 修正独立测试宿主背景后About浅深正常，主/次文字颜色对背景对比≥4.5；360dp/system2的About追加1方法通过；模拟器尺寸/字号已恢复 | system-large/result-visual.txt及PNG；candidate-2/ui-visual-result.txt |
| 最终APK收尾 | 9项通过，含设备2项、新增展示/缓存/导航流程；实际安装APK校验与最终候选完全一致 | candidate-3/result.txt、install-check.txt |
| 编译/Debug包/Lint | 成功；0错误、97警告、1提示，与V3基线相同。两条Snapshot unnecessary-safe-call编译提示在V3 build-3.log已存在 | device-final-build.log、lint-final.xml/txt |

计数是JUnit方法，不将一个参数矩阵展开为新增测试数，不将重复运行相加。不同候选、测试APK和源码指纹均区分；最终产品只在candidate-2之后额外修复设备占位名，并执行10单元+9Android定向检查。

首次失败保留：测试解析入口类型错误；JavaScript三笔0.1费用得到0.30000000000000004；180dp标题不可见；测试Resource重组时序、无效空地址与未挂载LazyListState等待布局；48dp测量浮点差异改按实际132像素核对。未删除测试或减小触摸要求。最早settings-before构建失败后曾误用旧测试APK，ClassNotFound结果仅属无效尝试，不计验收；有效复现是settings-before-2。早期source.diff仅包含当时已跟踪文件，精确APK/测试APK均保留；最终完整源码冻结和指纹在candidate-3/validated-source.zip及source-manifest.json。

## Debug开发验证包

- 文件：dist/token-monitor-native-0.2.51-desktop069-ui-debug-verification.apk。
- applicationId dev.bing.tokenmonitor.nativeclient；versionName 0.2.51-dev；versionCode 60；没有公开版本递增。
- SHA-256：dfcf2e0dae3108bede30b7ca73e6ba1b5d2ceee505b22838a65ac3899ea25d4b。
- 签名证书SHA-256：560f4beedb200ebd4ac974702d3f5417e57929f1293f203c8d20f28b5d732107。
- 自有模拟器-r覆盖安装成功、最终9项运行通过、设备实际base.apk校验匹配。最终源码dd3254a，测试源码21a9ca9（设备新单元位于dd3254a），冻结目录包含二者。
- 原dist/token-monitor-native-0.2.51-dev.apk仍为ef830691261dcd38d322eafd4e286068856871ea52e1347efbfd4b2123a4d805，未覆盖；其既有Debug签名与新包相同。未知Release签名APK不能据此承诺兼容。

## 未完成边界与后续

| 优先级/项目 | 证据与影响 | 独立下一步/验收 |
|---|---|---|
| P1生产字段与实际Hub版本 | 仅固定0.69合同/fixture；历史公开health core74不是本轮升级证据。Android支持不等于生产产出 | 建议第一个独立任务P1-03，只读验证部署、字段存在与标题/状态时效，两入口关联依据；服务升级需另行任务 |
| P0真机覆盖/旧数据 | 本轮仅emulator-5556；未触达手机 | 小米13 Ultra等授权连接后确认原包/签名，覆盖后UID、设置、旧记录解密及启动；不卸载/清数据 |
| P1手机触摸/字体/弹窗/IME/动画 | 合成与真实系统模拟器结果均不证明实体触摸、键盘遮挡或帧率 | 浅深/中英/系统大字，导航返回与滚动、设置展开/菜单/键盘、会话组及额度双周期实测 |
| P1公网与网络切换 | 合成HTTP/TLS及SSE状态检查通过；真实Lucky/蜂窝未验收，LiveHttpsEntryTest旧环境问题保留 | 实际公网HTTPS、LAN优先、后台/恢复、跨Wi-Fi/蜂窝的单SSE及全量一致性 |
| P2局部排序（后续0.2.55已完成） | 本阶段保留的review末尾差异已在独立任务按固定0.69完整混合排序修复 | 10组上游goldens及原账目/成员断言、36 Release单元和15 UI通过；见HANDOFF_0.2.55.md，不扩展采集 |
| 协议受限 | 无批准原因/zero-token/local overlays/Grok历史身份；Hub应用版本未提供 | 不造字段/不本地采集；仅待服务端可验证合同任务 |
| D桌面专属 | collector、SQLite watch、Cursor本地DB、窗口Dock/Electron修复 | 无Android移植动作 |

LAN历史1.685秒读取间隔遗留P1-09未展开；横屏/平板/读屏与实体性能未做新的专项完整验收。本轮到此停止，不推进v0.70、GitHub发布或NAS升级。

## 实际文件清单

以下相对项目根，由起始9f45fd2至本轮结束工作内容生成（不纳入APK/证据/缓存）：

- `AGENTS.md`
- `THIRD_PARTY_NOTICES.md`
- `app/src/androidTest/java/dev/bing/tokenmonitor/Desktop069UiScreenTest.kt`
- `app/src/androidTest/java/dev/bing/tokenmonitor/NavigationStreamScreenTest.kt`
- `app/src/androidTest/java/dev/bing/tokenmonitor/PageBrowseStateTest.kt`
- `app/src/androidTest/java/dev/bing/tokenmonitor/Session069ScreenTest.kt`
- `app/src/main/java/dev/bing/tokenmonitor/AboutSettings.kt`
- `app/src/main/java/dev/bing/tokenmonitor/DesktopHubScreen.kt`
- `app/src/main/java/dev/bing/tokenmonitor/DesktopParityMetadata.kt`
- `app/src/main/java/dev/bing/tokenmonitor/DesktopSettingsUi.kt`
- `app/src/main/java/dev/bing/tokenmonitor/DesktopViewSwitcher.kt`
- `app/src/main/java/dev/bing/tokenmonitor/DeviceDetails.kt`
- `app/src/main/java/dev/bing/tokenmonitor/DisplayNames.kt`
- `app/src/main/java/dev/bing/tokenmonitor/HomeSessionUi.kt`
- `app/src/main/java/dev/bing/tokenmonitor/LiveTokenRate.kt`
- `app/src/main/java/dev/bing/tokenmonitor/LiveTokenRateUi.kt`
- `app/src/main/java/dev/bing/tokenmonitor/PageBrowseState.kt`
- `app/src/main/java/dev/bing/tokenmonitor/ProjectDeviceDisplay.kt`
- `app/src/main/java/dev/bing/tokenmonitor/ProjectDeviceUi.kt`
- `app/src/main/java/dev/bing/tokenmonitor/SessionDisplay.kt`
- `app/src/main/java/dev/bing/tokenmonitor/SessionListDisplay.kt`
- `app/src/main/java/dev/bing/tokenmonitor/SessionListUi.kt`
- `app/src/main/java/dev/bing/tokenmonitor/SessionRelationshipGroups.kt`
- `app/src/main/res/drawable/desktop_view_device.xml`
- `app/src/main/res/drawable/desktop_view_model.xml`
- `app/src/main/res/drawable/desktop_view_session.xml`
- `app/src/main/res/drawable/desktop_view_tool.xml`
- `app/src/main/res/drawable/desktop_view_trends.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/dev/bing/tokenmonitor/DesktopParityMetadataTest.kt`
- `app/src/test/java/dev/bing/tokenmonitor/Device069Test.kt`
- `app/src/test/java/dev/bing/tokenmonitor/Session069Test.kt`
- `app/src/test/resources/session-069-parity.json`
- `docs/ARCHITECTURE.md`
- `docs/FEATURE_MATRIX.md`
- `docs/archive/history/HANDOFF_DESKTOP_069_UI.md`
- `docs/HUB_API_CONTRACT.md`
- `docs/NEXT_TASKS.md`
- `docs/UI_PARITY.md`
- `docs/UPSTREAM_SYNC.md`
