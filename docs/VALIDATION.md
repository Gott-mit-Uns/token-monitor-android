# 当前验收记录

## 本次正式发行验证：0.72.0-02 / code77

检查时间：2026-10-17（Asia/Shanghai）。用户已明确授权发布Android；所有更新均不要求真机测试，真机未测仅标未验证，不阻塞交付或已授权发布。未改NAS、未实施0.73对齐。构建提交 `e308802a9611be1b93c446e7eea3e64447719a7c`；发布Tag提交及GitHub工件身份须在上传后核对，不能把本地构建写成已发布。

| 检查 | 实跑结果与适用范围 |
|---|---|
| 完整JVM | 当前release变体518项、98类，0失败/错误/跳过；包括纯手动检查、旧自动配置失效、重建/取消、单请求、408/503/302无隐式重试、本地别名隔离/冷恢复及Hub网络恢复。本轮完整执行，取代此前仅49项定向结果作为本次JVM证据。 |
| 当前正式包UI | API36 ARM64临时只读克隆emulator-5592，最终包20项通过：正式升级读取、LauncherLabel、AndroidUpdateScreen、SyncDeviceGrid、ConnectionSettings、HubStore、字体行常规深色；另320dp/系统字体2/应用最大字号浅色字体2项通过，共22项。不是实体手机或生产Hub测试。 |
| 正式签名覆盖 | 已安装正式0.72.0-01/code76，SHA256 `db90f7e985cfa14c9f799171c0e114228a3a369ccf55834ab89f602d13c2b977`，用旧包写入独立公开合成加密Hub缓存及四份显示偏好，再install -r到code77；无卸载/清数据。UID10213及五个加密文件哈希前后逐项一致，新包成功解密并还原历史、连接配置和偏好。 |
| 包身份 | 实际APK applicationId `dev.bing.tokenmonitor.nativeclient`，versionName0.72.0-02/code77，min26/target37；应用及启动入口最终标签Token Monitor。新旧正式证书SHA256均 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。 |
| 构建/静态 | Wrapper/JDK17/SDK37正式release与签名测试包构建成功；Release Lint 0错误、102警告、1提示。语言门禁、文档门禁、diff检查通过。无依赖/签名变更或R8优化。 |
| 有界继承 | 本PR此前code76候选APK `5c75594b34146e0985d8f361b37c7a4b17be7b9b8cfd53b5b7219901db7bb038` 的14组/28项真实窗口字体矩阵、深色22场景分批及浅色12项结果仅继承未改UI范围。本次只增发行编号与文档，app/src/main与该候选的Git树相同；未将旧28项写成本包重跑。矩阵为约320/393/411dp × 系统字体1/2 × 两主题，加320系统2/应用最大字号两主题；检查共用列/断点、8dp间距、48dp触控、无缩字/省略和原重置语义。 |

最终应用APK SHA256：`727f78c982174817e6602f5946a47b3c0d3b5590d68bb4bd2ac6563fd3f1154b`；最终测试APK：`163784a73cb3b6574acf2386681589052bb40aeceefbf9ccd0bc21de936f4c9b`。原始本轮日志：`/tmp/token-monitor-release77-build.log`、`/tmp/token-monitor-release77-seed.log`、`/tmp/token-monitor-release77-ui.log`及`/tmp/token-monitor-release77-fontlarge.log`，JVM XML在app/build/test-results/testReleaseUnitTest。仅公开指纹，不保存生产凭据或统计明细。

对比截图：[常规修改前](../design/settings-fonts-before.png)、[常规修改后](../design/settings-fonts-after.png)、[320大字修改前](../design/settings-fonts-large-before.png)、[320大字修改后](../design/settings-fonts-large-after.png)、[长名称深色](../design/settings-fonts-long-dark.png)、[设备卡片](../design/settings-devices-after.png)、[纯手动更新](../design/settings-updates-after.png)。这些是此前相同UI源的公开样例原图，不是小米或生产Hub截图。

首次失败仍可追溯至本PR先前提交的当前记录及原始日志：Debug/正式签名冲突、固定测试Provider冲突、字体测试导入及unmerged语义节点问题、旧域名回退预期均已处理；弹层外点击因模拟器SystemUI无响应遮挡失败，试验坐标修改已撤回，原坐标/断言专项通过。产品弹层没有因此修改。本轮临时模拟器启动同样出现SystemUI弹窗，先关闭该弹窗再执行UI验收，未改应用或清数据。

用户提供的SSH诊断：生产Hub支持TOKEN_MONITOR_HUB_DISPLAY_NAME但未配置，所以当前鉴权响应不带hub.displayName；本聊天未复验该生产响应，未改服务端。客户端真实名称优先及当前连接别名通过公开快照/模拟服务验证；不宣称生产名称已显示。未验证范围还包括实体手机私有数据、OEM生命周期、生产网络/真实切Hub及公网匿名手动更新成功请求；真机未测不是发布门槛。README保持原样，不新建逐版本报告。

## 继承的正式版本与文档治理证据

适用：Android `0.72.0-01` / code76；治理基线：`cfab5438ed5dac6537c68affb385517ccfa44c0e`；检查日期：2026-10-17（Asia/Shanghai）。来源：固定基线的联合交接、发布回执、上轮整理记录及保留的原始证据指针。本次文档治理不重新构建APK，不执行设备/生产网络验收；下面历史结果按各自提交和工件继承，不能改称本轮实跑。

## 现行验证方法

沿用Wrapper/JDK17/SDK37，优先受影响的定向测试；不clean、清数据、换证书或放宽断言。JVM用`:app:testDebugUnitTest`，UI须区分测试编译与实际执行、隔离模拟器与真机、公开fixture与真实账号。可选live/permission/perf/capacity只在明确开关启用时计入范围。正式发行按VERSIONING和RELEASE_SIGNING，签名/源码/应用及测试APK指纹、安装/启动/数据保留分别留证。共享三份home/limit/subscription样例唯一来源为`app/src/sharedTest/resources/`，生成工具只写该目录；本次不改样例、配置和脚本。

## 沿用产品验证（旧提交，未重跑）

| 来源与工件 | 已记录结果 | 适用限制 |
|---|---|---|
| build5b1fb38 / APK db90f7e9…，最终Tag3c4d2d6；2026-10-16旧回执 | 514单元0失败/错误/跳过；Lint0错误/104警告/1提示；受影响9项+明确opt-in冷启动2项 | 本次未重跑，不称零告警。release76原日志、unit/lint-summary和affected-ui/cold各分批记录 |
| 旧候选16f92a7 / APK17b7470e… | API36视觉78次分批通过；10张归属原图 | 仅限定继承未改视觉源；不冒称db90重新跑全78；SharedData首次ComposeNotIdle和未opt-in assumption跳过保持 |
| 正式公开db90 / 同证书code73→75→76 | 旧记录公开升级链及合成加密缓存/历史/四偏好冷保留；公开附件大小/digest/SHA/SOURCE核对 | 不代表真实用户资料或手机升级；早期同code退役候选链与公开链分开 |
| 2026-10-16正式命名更正 / 同字节db90 | 12项JVM、最终公开APK上2项模拟器更新入口检查 | 新正式latest已停止旧兼容发现；旧更新器曾通过记录仅属更名前，需手动安装一次。首次匿名API403保留 |
| 旧0.2.53/code62 / a3f03cf基线 / 已安装SHA dff4af84… | 小米13Ultra人工启动、有限菜单/额度/设置/Wi-Fi HTTPS与前后台恢复 | 未安装/卸载/清数据，不算升级；font2裁切/动态字号疑点、蜂窝/完整SSE/软键盘矩阵仍缺，见档案 |
| 上轮整理da3edc2起点→cfab543 / DOCS_CLEANUP旧记录 | 定向10及完整514 JVM；额度/订阅7浅+7深；构建成功 | 两次并行Gradle失败保留；临时Provider适配测试包不等于最终PR测试包全跑；额外背景3项2过1失败未定位 |

原始 `evidence/desktop-072/`、`evidence/docs-cleanup/`、`evidence/phone-053/` 等不在Git内，本次不删除、不迁移、不宣称新checkout具备这些文件或已复验。首次Lint内部异常、磁盘不足、旧版本harness签名不兼容、截图省略、SharedData虚拟时钟问题及各候选指纹在固定历史提交仍可恢复。沿用结果的详细源路径见[历史索引](archive/INDEX.md)；合成截图/manifest及合法release-records逐字保留。

## 本轮文档治理实跑

检查范围：全文档本地目标/锚点、历史迁移引用、README授权/hash、禁止新增过程命名、档案新增限制、门禁正反例、语言资源与最终差异。实际通过：文档门禁及其正反例（新增重复命名、断链/锚点、README缺授权/旧授权复用、冻结档案新增、合法发布输入/测试夹具）；全仓相对Markdown及HTML图片链接、固定历史Git目标/锚点检查0问题；语言资源670对一致；git diff --check通过。22份保留审计逐字核对，除链接/HTML图片相对路径重定位外正文一致。既有非Markdown跟踪文件零变更：全部app源码/测试/夹具、Gradle输入、字体/截图及manifest、来源/签名和release-records保持字节不变。新增仅文档门禁及独立PR工作流，不触发APK构建。

数量按Git跟踪文件统计：962→854，Markdown156→46；移除114份旧路径（其中2个索引/方法入口原位重写），22份独有证据改为有限同一档案目录，普通逐版本过程用固定Git历史恢复。原主工作区3项未提交内容保持。工作流语法经结构检查；实际GitHub运行与自动审查须另记，未返回不能算通过。

本轮不做APK构建/安装、真机、Hub实网或生产部署。未完成事项与背景图失败只在 [HANDOFF](HANDOFF.md) 持续跟进，不因删历史流水而关闭。

## 远程检查与审查收敛

2026-10-17继续复审：此前3fb6a6a的[文档CI](https://github.com/Gott-mit-Uns/token-monitor-android/actions/runs/38997913257)实际成功。此次补充复验发现子目录README改名为普通文件时，Git rename折叠可能漏掉旧路径授权；改为无rename折叠的全部变更检查，新增辅助README重命名负例，修复前该断言失败，修复后通过。同时补上NUL分隔Git路径解析，避免中文/引号路径漏检；围栏内标题不再产生真实锚点，支持反引号与波浪线围栏，均有拒绝反例。40ec19b复审的两项P2已处理：辅助README删除/改名须记录原路径基线字节摘要及本次授权，既能拒绝未授权删除，也允许精确授权；引用定义统一校验，覆盖完整、折叠及快捷引用。另补仓库外真实文件误通过的拒绝反例，链接解析后必须仍在仓库内。重复记录命名同时覆盖日期/版本前缀，以及空格、点号和紧邻数字版本形式，必要反例通过。合法顶层缩进代码示例不扫描假链接，嵌套列表与HTML表格内的真实链接仍检查，单引号HTML链接断链亦有拒绝反例。标准下划线式标题与重复标题锚点有通过/拒绝反例，避免合法标题误报。记录命名覆盖无扩展名及常用文档扩展名（含adoc/HTML），普通业务代码扩展名保持不误拦，均有反例/例外测试。行内代码中的示例链接不当真实链接，HTML标签/属性大小写不敏感，均有针对性正反例。实际文档树中以保留记录名建立日期目录同样拒绝，普通链接式标题按可见文字生成锚点；均有定向回归。Markdown扩展名与HTML注释有扫描/忽略回归；保留字符路径按百分号编码的既定有限约定验证，复杂Markdown解析仍由人工审查，不宣称完整CommonMark。重复标题后缀分配避开已有slug，隐藏注释不生成锚点，两类小范围回归通过。引用定义保留所有出现位置，避免同名定义掩盖断链；多行Setext按完整段落生成锚点，并在前一标题下划线或分隔线处停止回溯；点分隔纯版本记录不误当扩展名。三类定向回归通过。HTML链接和显式锚点改由标准库解析实际标签属性，覆盖大小写、无引号、等号空格，并拒绝data-id和行内示例伪锚点；正反例通过。双引号链接标题前的空格、制表和换行分隔均有通过回归。当前全部门禁正反例、本地链接/锚点、固定历史目标和git diff检查通过。门禁文件SHA256：`549dcf89aa8565c712671da46d4744c84068264e4cb8b70c39fe25961eee8f4d`。

初始自动审查提出的同页锚点和封存证据保护问题已修复并保留对应反例。此次任务的最新自动审查/CI动态结果以[本PR检查](https://github.com/Gott-mit-Uns/token-monitor-android/pull/12/checks)和review实际绑定的commit为准；不能沿用旧提交结论或空security_review状态宣称最终通过。本文件仅保留有来源的实测与适用边界，不逐轮追加运行流水。

本轮不改应用源码、Gradle输入、依赖、签名、版本或产物；不构建APK，不操作真机/Hub/生产配置。历史真机与背景图未完成项仍在HANDOFF。
