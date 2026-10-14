> 后续变更：用户实际试用后明确取消 Android 桌面小组件，0.2.64 已移除此功能及常规设置。本文是 0.2.63 历史调查与验收记录，原小组件待验收项不再推进；当前交付以 [0.2.64 交接](HANDOFF_0.2.64.md) 为准。

# Android 0.2.63 / 72 统一候选交接

2026-10-14，Asia/Shanghai。本轮统一范围为 **Mac 系统桌面 Widget 重新对齐 + 关于应用整理 + 多设备同步整理**。产品实现已经整合在同一个 0.2.63/72 候选中；本地测试与候选指纹以本文最终验收表和 `dist/candidate-0.2.63/candidate-manifest.json` 为准。尚未合并主线、推送、创建 Tag 或正式 Release，未安装实体手机，未修改生产 NAS。

## 当前基线与隔离

本轮开始实时读取 GitHub/main、最新交接、版本和本地工作区，确认正式基线 **0.2.62/71**，main `567022709646aac52279874889b97656cfe7b5d5`，正式 Tag 产品提交 `aefe855c700cb7cac69ab43dac4df64e8e8ff677`。没有沿用聊天中的旧版本号。正式 0.2.62 APK SHA-256 `1cf84bfef6633915a6d1cf26a65c548d61a5bc37fccd370023ca1b560b7cb08e`，15,238,714 字节。

独立 worktree：`/Users/bing/Documents/Codex/项目分类/AI与软件/work/token-monitor-widget-settings-063`，分支 `feat/widget-settings-063`。原 `token-monitor-android-native` main/3731975 的 `docs/NEXT_TASKS.md` 修改和两个未跟踪文件 `AGENTS-original.md`、`docs/REA_MOBILE_GAP_AUDIT_0.2.60.md` 保留；未 reset/stash/清理。已读 AGENTS、TAKEOVER、最新 HANDOFF/NEXT_TASKS、紧凑 UI、签名和回归规范。用户本轮的“先候选、正式动作待确认”优先于仓库默认自动发布流程。

## 实现清单

- **原生桌面 Widget**：保持两个旧 provider/package/signature，采用现有 RemoteViews；用量支持 Summary、Dashboard、Activity、Tools、Models，额度支持自动或最多两真实记录。Dashboard 顶部主数/趋势、中部额度、下方活动/分解沿用 Mac 信息组织；不是普通 Compose 卡片或截图贴图。
- **每实例配置和路由**：今日、自然月、全部独立于 App last30；取消不落盘，按 id 删除，restore 先快照再迁移避免重叠 ID 串项。PendingIntent 含 id/模式/周期，打开正确页，隐藏页可直接进入；旧实例无配置仍有默认摘要/自动额度。额度有稳定产品/来源标识时可跨顺序变化识别；没有标识且完全同身份的旧记录按 ordinal+cardinality，不能承诺任意重排仍可识别，歧义旧 hash 不猜选另一项。
- **真实统计**：复用 Hub total/cost/history、别名和 modelNames；趋势 history/live 取 max，模型显示名 NFKC/大小写合并，每个原计数只贡献一次，溢出未知不钳制为 0。中文万/亿或 K/M/B、费用两位小数和用户 usage 汇率；额度本币独立。缺口/缺字段明确未知，完整值保留无障碍语义。
- **状态及生命周期**：原加密缓存，只读 Widget；30 分钟系统缓存重绘及保存/偏好/开机/升级事件，不建立 Widget 后台网络连接或常驻服务。失败保留可信缓存，源/缓存过期明确。接收时间无效时回退有效设备更新时间，合法未来接收时间保留不可信判断。旧小尺寸去掉重复行、保留根点击与 Edit 48dp；font2 且高度<240dp 显示真实状态和扩大提示，避免裁切/不可信 0。
- **关于应用**：保持检查更新、当前版本说明、Android 发行页、来源与开源许可四个真实入口，正常宽度一行四列、极窄/大字体两列；统一图标、文字基线、间距与 48dp 点击区域。应用名/Android 版本有主次层级，桌面参考与 Hub 构建单独分组；完整可选择许可仍可打开。
- **多设备同步地址**：HTTPS/HTTP 同屏独立行，缺失协议未配置，地址仅显示 scheme/host/port。当前边框+背景+文字来自接受的 HTTP/SSE 端点 receipt，停流/失败清当前，缓存可标上次连接；旧缓存没有 receipt 不推断。移除旧切换按钮，保留编辑、LAN 许可/明确关联验证和原有安全故障恢复；没有新增协议降级或证书绕过。
- **设备信息**：独立手机查看端、Hub、采集设备角色；复用实际 Device 的名称、OS/版本、运行类型/版本、stale 和 receivedAt/updatedAt。同步时间遵循 Mac 有效 receivedAt 优先，旧接口缺失仍未提供/未知。没有新增服务端字段、修改 Hub/Agent 仓库或部署。

## REA 与实际 Mac 参照

用户确认目标是“编辑小组件”添加的 **系统桌面 Widget**。实际安装 App 0.70.0 arm64，WidgetKit/SwiftUI/AppIntents 扩展 UI47/schema10/revision ac4eeb2。REA skill v37 已实际读取，MCP/CLI 6.4.0 版本和入口已检查，真实 JS/Apple/native 调用及有效结果均已保存。

ASAR 严格检查首先失败于三个 unpacked 原生依赖；随后显式 record-and-continue 静态提取，保留不一致且未执行。大图预算限制后对七模块定向分析 Widget bridge、snapshot、sync 的事件/数据路径；`syncDevicePanel` 与实际设置界面确认设备字段及 receipt 时间。原生引擎最初缺失，经用户明确授权在独立 rea-toolchain 安装官方 Ghidra12.1.4/JDK21并构建 ARM64组件，隔离CLI的doctor健康；真实函数分析确认 AppGroup/snapshot.json/JSONDecoder/周期选择、Refresh reloadAllTimelines、900秒后timeline、1200秒stale分支。原注册MCP未写入新工具链配置，本轮深原生能力由授权CLI/临时stdio会话提供，不能声称原MCP环境已全局修好。已导出/关闭分析会话，不改注册MCP或AndroidJDK。Evidence和局限见 [原生报告](REA_NATIVE_WIDGET_063.md)及 [完整差异清单](MAC_ANDROID_WIDGET_COMPARISON_063.md)。

Mac 主窗口关于、同步以及系统 Dashboard 已实际观察并截图。Widget实际点击刷新后仍显示 2526.5万/$5.03，当前授权 AppGroup快照是1.58亿/$29.53；注册只有该安装路径的0.70扩展，旧render/timeline原因未确认。未把点击记成数据已刷新，未把它当准确源码placeholder，未更改AppGroup求同图。实际系统 Widget **同输入截图尚未取得**。

同输入对照使用用户授权的单份快照，敏感账户身份脱敏，临时原始副本删除。原 Mac SwiftUI 组件的六张 ImageRenderer 导出图与 Android host 六图使用同一统计输入；导出图不是 WidgetKit 现场截图，不包含系统外框。今日 1.58亿/$29.53、月33.8亿/$1077.32、全部74.3亿/$2739.64，另核 Codex81%、DeepSeek21.17/7.93、增长36%及各已知趋势点。Mac源/导出与Android截图边界见对照文档。

## 最终候选与执行结果

最终 APP/测试 APK、源码提交、签名、大小及 SHA 在 `dist/candidate-0.2.63/candidate-manifest.json` / `SHA256SUMS.txt`。AGP 内嵌VCS信息在此worktree报告 NO_VALID_GIT_FOUND，不能用内嵌元数据证明源码；本轮用真实Git提交/源树、构建日志、文件和设备端SHA读回关联。签名证书必须与原0.2.62完全相同，不能以Debug签名替代。

候选产品提交 `003085c724ac39ede74b4103d031806af8702678`，原包名 `dev.bing.tokenmonitor.nativeclient`，版本0.2.63/72，Release非debuggable，原正式证书SHA-256 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。APK **15,337,998 bytes（14.63 MiB）**，SHA-256：

`f7907c8d7c7728f3ab893ba23c585d9a46996cbb811b0668224d950897ad8f63`

| 门槛 | 实际结果 / 范围 | 证据 |
| --- | --- | --- |
| Release单元 | 478，0失败/0错误/0跳过 | build-host-space-final.log、build-validation.json |
| Lint/编译/正式签名 | 0错误/152警告/1提示；app与测试APK构建，原证书一致 | final-candidate-signature.txt、candidate-manifest.json |
| 最终Widget/配置/导航/缓存 | f790+209两包设备端SHA一致，13/13，9.904秒；32组新宿主布局含浅深/font1/font2/旧160与190/所有模式；长数单行/行宽/无省略号；多个实例/删除/restore/冷路由/不联网 | final-tests/widget-f790/result.json及fingerprints.json |
| 同输入 | 六张Android host图；三周期主数/成本、额度/Spend、增长和全部已知趋势点通过；Mac源组件六图，同输入真实Mac系统截图未取得 | CommonWidgetInputHostTest、images/widget-063/provenance.json |
| 设置UI | fbff+97包，20/20 DARK55.434秒 +5/5 LIGHT10.683秒；四入口、完整许可、双地址/状态/旧字段、长主机/设备名font2 | final-3897652-settings-launcher/SETTINGS_RESULT.md |
| 原功能回归 | fbff+246包49/49，97.269秒；主页/趋势/额度/字体/会话/旧缓存/TLS/SSE/网络恢复/八页导航 | final-tests/regression/result.json |
| 最终端点安全/恢复 | f790+209包10/10，6.244秒；真实fixture请求目标receipt、明确关联与准入、401/403/TLS无降级、超时回退、晚响应/单SSE、前后台 | final-tests/endpoint-f790/result.json |
| 正式签名覆盖升级 | 专用5622，.62/71→各.63候选→精确f790；2/2专门回读，UID/加密缓存/四偏好/旧IDs2/3/options完整；实际进程恢复和190dp截图通过 | signed-upgrade-5622/final-overinstall-f7907c8d7c77/receipt.json |
| OS重启 | 首轮.62→.63覆盖后实际重启并保留数据/IDs；最终只补实际进程恢复，未把旧重启写成f790重复执行 | signed-upgrade-5622/README.md |
| 真机/实网 | 本轮小米安装、OEM Launcher、锁屏调度、实际LAN/HTTPS/蜂窝均未做 | 明确待验收，不以模拟器或编译替代 |

设置及49项回归在最后一次Widget留白/主数测宽修复之前的fbff应用包执行，精确指纹全部保留。两版之间只有Widget绘制留白/数字测量及测试断言变化，About、Sync、Hub网络/存储、主页/趋势/字体源码不变；依第8.1节继承这些已验证且未受影响的范围，不冒称49/25项在f790重新执行。最终f790独立执行了受影响13项Widget与10项网络安全、覆盖升级、真实进程恢复。后续Launcher改动只在测试APK，产品f790不再变化；每轮测试APK指纹独立保存，不能把不同测试包混成同一包。

**真实Launcher结果为部分通过，完整流水线未通过。** 最终f790应用已通过系统Add渲染Models/自然月50M、实际Resize（min255→346dp、max447→600dp、height340/538不变）、实际Move/Remove删除以及onDeleted清理；最后remaining-owned为空。最后完整单方法使用测试90d60e...，7.692秒在原“This month”AX断言失败，原图实际显示该周期与正确数值。没有删除/放宽原断言或声称最后全套通过。实际Launcher usage/quota点击、两个实例同屏、删除一项保留另一项尚未取得通过；独立绑定AppWidgetHost的实例/路由/恢复结果不替代此完整Launcher链。原始失败、实际resize/删除证据见final-003085c-launcher/RESULT.md与lifecycle-test8cf56d8/RESULT.md。此后停止验收工具改动。

## 首失败与修复记录

按项目规范保存第一轮失败、对应 APK/测试 APK 指纹和复测，不删除或降断言：

1. 首次 Widget host 11项中8过3失败：font2 摘要标题、通用布局自然高度、额度Open触控被挤压。自然标题/短重复行/额度底部并排操作修复；48dp与真实文本高度断言保留。
2. Lint receiver UAST分析空body崩溃；改显式Unit块体，检查恢复成功，没有禁用/抑制lint。
3. 只读复核发现同provider/account多记录哈希碰撞；按真实记录标识/必要的无歧义兼容alias修复，测试自动两不同值、自选产品、完全相同旧记录、重排和歧义缺失。升级旧160–200dp下缓存时间被测为16px/约6dp，紧凑布局修复后真实190dp host可见39px完整时间行；原宿主padding与旧options保留。
4. 0885pair设置19项有3个fullHub测试Compose idle超时，同包单例复现。生产动画起点/测试帧时钟域不同；只给测试宿主注入既有compose.mainClock同域，原动画/断言/超时不变，d73设置20项已通过。第一次无背景暗色截图不能记为视觉通过，后来补测试根同主题背景，未改产品色。
5. Launcher 首轮未识别真实 Add to home screen/HOME 被包解析为 FallbackHome；按实际 HOME 与真实按钮修测试。第二轮实际添加并渲染成功，已进入Resize，随后尺寸控制调用返回false，Remove无可用动作，保存首失败及owned id；后续仅针对本轮实际创建的自有实例做UI诊断/清理，包括IDs8/25/26/27，不卸载、清缓存或改宿主options冒充尺寸调整。
6. 一次升级模拟器遇 System UI ANR遮挡；前序缓存/UID读取通过，模态画面保留，仅点实际Wait恢复再核无遮挡宿主。不能把环境遮挡称为业务数据失败或跳过视觉门槛。
7. fbff/246 的13项最终Widget候选测试11过2失败：旧160dp中文时间38px不足自然42px；MAX_LONG文本因未扣系统宿主边距发生换行/裁切。补6dp紧凑留白、按真实宿主+组件留白测宽，并增加实际TextView单行/行宽/无省略号断言；原纵向和48dp断言保持，最终新指纹复测独立记录。

日志、逐轮包和截图在 `evidence/widget-settings-063/`；初版设置、Lint失败包、widget首失败、0885、d73均独立保存。只有最终指纹对应的执行才算最终新版本实测；继承证据须明确来源版本/范围。

## 平台差异、待验收与停止边界

- Mac数字转场、单个活动格点日期AppIntent、系统WidgetKit外框/着色/字体无法等价迁移；Android为静态RemoteViews、打开原生趋势页、原生浅深色及48dp操作。系统刷新不保证准点，不能承诺Mac式实时。
- Dashboard额度用紧凑文字行，重置详情保留完整无障碍说明、独立额度组件和点击后的额度页；未照搬Mac额度进度条/图标。独立工具/模型组件使用现有供应商图标，Dashboard分解采用份额条。此为紧凑布局选择，仍有视觉差异，不能把它宣称为完整逐项视觉复刻。
- Android Widget 数据来自Hub已接受的缓存；缺history/账户/源状态无法从其他字段编造。App关闭期间不会后台修复业务数据，打开App前台恢复。完全缺稳定identity的重复旧账户受snapshot顺序限制。
- Pixel/Nexus Launcher完整点击/双实例链仍待验收；工具未稳定读到周期字段，不能据可见截图把未执行步骤补写成通过。真实pin/resize/delete及system host的32组合已分别记录。
- 小米13Ultra和其他实体设备：本轮未安装，覆盖升级、厂商Launcher拖动/尺寸、锁屏/省电/后台调度、字体/主题和点击仍待确认。没有用模拟器替代真机结论。
- 真实LAN/LuckyHTTPS/蜂窝→Wi-Fi、公网认证/证书/恢复本轮未联调；自动化TLS/网络fixture与实网分开。历史0.2.59小米网络失败保留，不能被此模拟器结果改成通过。
- 关于入口的布局、点击处理、目标URL与完整许可已由UI测试覆盖；候选0.2.63的“当前版本说明”仍按原行为指向正式v0.2.63发行说明。该Release按用户要求未发布，远端页面可用性待发布后回读，不把回调/URL断言称作真实浏览器联网验收。检查更新与Android发行页保留原入口和当前正式0.2.62元数据来源。
- Mac刷新后仍旧值及实际同输入系统截图仍待核对；源码导出图只用于同输入组件对照。未修改或重启Mac应用来绕过此限制。
- 本轮没有导入R8/资源缩减，没有无关重构或生产NAS/Hub/Agent/路由操作。后续合并、推送、Tag、正式Release、安装手机和生产操作均等用户确认。不要推进历史待办。

具体后续真机验收：覆盖安装且不清数据→确认版本/旧缓存和旧组件；依次添加/删除两类组件、两个独立周期实例并拖动调整尺寸；浅深/font1/font2/长数/长设备名；逐个关于入口；双地址连接/断开/恢复、缺协议/缺设备字段；进程被杀/锁屏/重启后状态。通过后再处理正式发布门槛。
