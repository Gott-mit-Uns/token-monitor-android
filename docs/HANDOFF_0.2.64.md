# Android 0.2.64 / 73：删除桌面小组件、设备两列

2026-10-14，Asia/Shanghai。用户在实际试用 0.2.63 后明确取消 Android 桌面小组件，要求完整删除功能及常规中的相关设置，并将多设备同步设备分两列。本轮覆盖该反馈，不继续 0.2.63 的 Widget 对齐或未完成验收。

## 基线与范围

实时 GitHub 正式基线仍为 main `567022709646aac52279874889b97656cfe7b5d5` / Release `v0.2.62`。此次从本轮已交付的 0.2.63/72 本地候选 `b73a86238db19d377aed9703f5bbcfce8306ef2a` 创建独立 `fix/remove-widgets-devices-064`，递增至 0.2.64/73，以便覆盖用户刚试过的候选。原工作区 main/3731975 的未提交 NEXT_TASKS 与两个未跟踪文件保留，没有 reset/stash/清理。

本轮保留 0.2.63 的关于应用四入口、HTTPS/HTTP 同屏、真实成功端点与设备信息。生产 Hub/Agent 协议、网络选路、认证、证书验证、缓存格式和显示偏好不扩展；没有合入 R8／资源瘦身实验。

## 完整移除的功能

- 两个 Android Widget provider、配置 Activity、全部 provider metadata、开机恢复权限与事件。
- 8 个产品 Kotlin 文件：提供器/额度、配置与 Activity、缓存投影、图表、Dashboard 和设置组件。
- 17 个专属 XML 文件及两套主字符串中的专属文案；没有保留只被禁用的组件或可执行旧代码。
- 常规设置中的添加、配置、实例列表、操作说明及所有相关入口。
- 缓存写入、偏好更新、忘记连接后的三个 Widget 重绘钩子；MainActivity 中的 Widget Intent 消费与周期／页面路由参数。
- 4 个专属单元测试、7 个专属 Widget/Host/Launcher UI 测试与共同输入资产。混合缓存、Provider、币种、网络和容量测试只删除已取消功能的部分，继续保留其非 Widget 断言。新增加负向移除及覆盖升级验证，不放宽剩余功能门槛。

旧系统 Widget 绑定随 APK 替换撤销，Launcher 外观清理由宿主管理；没有通过卸载/清数据强制清理桌面。旧私有 Widget metadata 如果仍在 noBackup 目录，只是不会再读取或写入的惰性旧文件；本轮不为删除功能增加清除业务数据的迁移路径。

远端 Mac 的 `electron-widget` / `Widget v0.70.0` 是采集设备真实运行类型，继续展示；它与已删除的 Android 桌面小组件不同。普通 `android.widget.*` Android UI 控件也不属于此删除范围。

## 多设备同步布局

采集设备保持现有排序，使用等宽两列、成对行和 8dp 间距；奇数最后一个保持半宽，空位没有虚构设备或无障碍标签。卡片使用现有主题和字号，长名称、OS、运行版本与状态自然换行，行高按真实文本增长，不裁切、缩小或省略。

本机手机查看端与 Hub 保留独立角色说明；设备名称、真实字段、未知／未提供、离线上次报告和同步时间语义均不变。没有将手机联网状态用于推断采集设备在线。

## 实际验收与候选

正式签名候选：`dist/candidate-0.2.64/token-monitor-android-0.2.64-candidate.apk`，**15,238,142 bytes / 14.53 MiB**，版本0.2.64/73，原包名 `dev.bing.tokenmonitor.nativeclient`，Release非debuggable。SHA-256：

`df7ab620f0ca9d086ff30da761a232cb5bd99a9053356a3688c61006f1ab8c56`

正式证书 SHA-256 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`，与原0.2.63候选及正式0.2.62一致。产品构建提交 `ab9a6acee07ddee302f23dd7e033cd19803e9f2e`，主源码树 `5e4703784f62bc2e59735c6283b9c54cb1ff6dfe`；后续变更仅在测试。最终测试提交 `fc206065daf9b9fc29fd1c9b3136b619f83ae2bf`，测试APK SHA-256 `70004533b05c0c1f175605e5c48ac09f60d03d0dcf694c946aee1bd351c46dac`。最终两包均设备端SHA读回一致。AGP内嵌VCS信息仍是 `NO_VALID_GIT_FOUND`；源码关联依Git对象、源树、构建日志和外部候选manifest，不冒称APK内嵌了提交。

所有执行均为任务自有 **API36 / ARM64 emulator-5620**，不是实体手机。静态检查670对核心中英文资源一致，主XML/Manifest可解析，产品没有Android Widget实现/资源/配置/刷新/路由/boot权限。执行记录归 `evidence/widget-removal-devices-064/`。

| 检查 | 结果与精确范围 | 证据 |
| --- | --- | --- |
| Release单元 / Lint | 455 tests，0失败/错误/跳过；0 Error/Fatal、96 Warning、1 Hint | build-unit-lint-release.log、build-validation.json |
| 两列与统一设置浅色 | 同一最终df7产品+ad1e测试，10/10，27.164秒；320/393、字体1→2、240dp长设备、等宽/顺序/奇数半宽/自然行高/完整文本/空态、双地址/About | ui-light-first/result.json |
| 两列与统一设置深色 | 最终df7产品+7193测试中两个类各5方法均通过；包含它们的12项整组另有常规测试选择器失败，不能写整组12通过 | ui-dark-repair/scoped-grid-unified-result.json及原log |
| 移除负向与真实常规 | 最终df7产品+b1ec测试，2/2，6.206秒；含禁用组件的真实PackageManager、类加载、151项旧资源、旧ID、系统provider、boot权限全部撤销；中英文真实常规无入口、语言/About/完整许可保留，0 Hub读取 | removal-final/result.json及截图 |
| 原功能回归 | 最终df7产品+b1ec测试，49/49，104.487秒；主页/趋势/额度/数字/字体/会话/八页导航/缓存/TLS/SSE/网络恢复 | regression-final/result.json |
| 设置与真实网络生命周期 | 最终df7产品+b1ec测试，八类18方法均通过；连接事务、更新、认证/TLS/故障恢复/真实HTTP/SSE端点与实际Activity前后台、Back/重建/显式刷新第四SSE | network-settings-final/scoped-network-settings-result.json |
| 语言与真实重建 | 最终df7产品+7000最终测试，5/5，10.784秒；真实HubViewModel、真实ActivityScenario.recreate、实际偏好文件和迁移、原断言/超时保留 | language-clock-final/result.json |
| .63→.64正式签名覆盖升级 | 先在精确f790/0.2.63上保留加密Hub/历史/四类偏好/UID，实际系统绑定两个旧provider；覆盖最终df7后两个聚焦方法通过，旧provider/绑定撤销且旧数据/UID完全保留 | upgrade-before-fingerprints.json、upgrade-seed-063.log、upgrade-bindings-seed-063.log、signed-upgrade/result.json |
| 实际进程恢复 | 最终df7实际force-stop、COLD启动Status ok，MainActivity处于resumed且进程保持 | cold-launch-064.txt |

所有通过记录使用相同最终**产品APK**。测试APK的ad1e/7193/b1ec/7000指纹逐轮保存；后续只修测试导入、真实标签和语言测试时钟，设备网格/About/端点及产品源码不变。按AGENTS第8.1节继承未受影响的已执行方法，不把此前测试包冒称最后7000包全套重跑。network-settings原23项中18网络设置方法和4旧语言方法通过，1语言idle失败；该整组失败保留，最后仅对受影响语言类5方法复测通过。没有将失败整组改写为全绿。

实际系统绑定升级验证使用真实AppWidgetHost和系统服务，不冒充小米或实际Launcher拖动放置。不同Launcher覆盖升级时可能短暂保留失效卡片/空占位；系统provider与可执行代码已撤销，如OEM桌面仍保留占位由用户/宿主管理删除，本轮不操作实体手机桌面。

## 截图与首次失败

原图均逐字节复制，使用公开合成设备/地址，没有改像素或录入生产认证信息。每张图的执行包与来源见 `images/settings-064/provenance.json`。

- [设备两列浅色](images/settings-064/devices-two-columns-light.png)、[深色](images/settings-064/devices-two-columns-dark.png)、[320dp字体2长字段](images/settings-064/devices-font2-long-light.png)。大字体长字段可自然超出单屏，完整内容通过滚动查看，未缩小或省略。
- [真实常规中文无小组件](images/settings-064/general-no-widgets-chinese-dark.png)、[英文](images/settings-064/general-no-widgets-english-dark.png)。
- [双地址与设备](images/settings-064/sync-live-light.png)、[离线上次连接](images/settings-064/sync-offline-light.png)。

首次失败和修复另存，不覆盖、不删断言：

1. 初次单元调用漏带项目Release测试开关，任务未找到，未执行测试；补 `-PtokenMonitorReleaseTest=true` 后455项/Lint通过。原选择失败日志保留。
2. 新网格测试缺DpRect宽/高扩展import，AndroidTest编译失败。未产生新APK对，当时输出仍为旧0.2.63，不冒称失败包是0.2.64。只补test import后构建成功，编译日志、源码提交与产物状态保留。
3. 新负向常规测试误用不存在的 `settings-language` tag；先加滚动仍失败，核对真实组件及已有语言测试后改为 `settings-language-select`。保留可见/可用、语言、无Widget、About、完整许可及0网络读取断言，最终2/2通过。两个首次失败整组及精确包指纹保存；组后截图明确仅为组后状态，不能称断言当时画面。
4. 追加既有真实Activity语言回归在展开常规后ComposeNotIdle：默认System.nanoTime起点与仪器Compose虚拟frame时间域混用。只为该测试初始/实际recreate后的根注入已有mainClock同域动画时钟，仍真实收集Activity持有HubViewModel的state/display/startup、权限路径及实际偏好；不fake、不关动画、不增加超时、不改产品。原23项组失败及后续5/5复测独立保留。

## 交付边界

本轮不合并主线、不推送、不创建 Tag 或正式 Release、不自动安装实体手机、不修改 NAS/Hub/Agent。候选审查后由用户确认后续操作。模拟器验证与小米真机结果分别记录，不能把编译或模拟器测试称作真机已通过。当前候选发行说明页面在发布前仍待远端可用性核验。

实体小米覆盖升级、OEM桌面旧卡片清理、实网LAN/HTTPS/蜂窝和锁屏后台本轮未做。大量容量/perf/live专项未重跑，不能据删除钩子或模拟器自动化推断生产结果；历史失败证据不改写。0.2.63的Mac同输入Widget、完整Launcher点击等旧验收任务因用户取消功能不再推进。
