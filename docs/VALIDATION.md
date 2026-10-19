# 当前验收记录

## 当前正式发行验收：桌面 v0.75.0 / Android 0.75.0-01 / code81

检查日期2026-10-20（Asia/Shanghai）；基线 `327d87c8fba2b3898c28796798e36936f58bb86f`，桌面正式Tag v0.75.0解引用 `877fef6002a5fee84aa502fc3454b4f700b55813`；最新Android正式基线0.74.0-02/code80。用户明确授权实现、测试、PR、合并及发布，不包含实际设备安装或生产服务部署。

| 本轮实跑范围 | 结果及边界 |
|---|---|
| 标准构建 | 既有Wrapper/Gradle9.8/JDK17/SDK37离线release及正式签名AndroidTest构建成功，未clean；首次JAVA_HOME少Contents/Home导致启动前失败，无APK产生，修正后正常。 |
| JVM | 完整536项通过，无失败；包含固定v0.75源码输出31组活动/身份样例的HTTP、SSE full、freshness和重新解析对照，以及新full来源替换、Antigravity关系分组、DSH项目模型、精确core89识别。旧074样例继续锁定原commit，不冒称新基准输出。 |
| Lint/资源 | 0错误、113警告、1提示；与已发布07402记录相同。671对英中资源、文档门禁及git diff检查通过；不以抑制规则取得通过。 |
| 签名 | 正式包名dev.bing.tokenmonitor.nativeclient，0.75.0-01/code81，正式公有证书SHA256 e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202一致。 |
| 模拟器 | 临时只读API36 ARM64 emulator-5686，正式code80→81覆盖升级通过；UID10213及五份文件SHA逐项保持，公开合成加密缓存/历史及四份偏好解密成功，安装APK回读SHA与候选一致；未卸载、清数据或降级。24项界面/缓存回归22通过、2项既有专项失败，见下述明确限制。新增Bot状态和冷恢复2项均通过，320dp/系统字号2双主题新增Bot界面2组各1项通过。 |
| 上游旁证 | 固定v0.75的Grok身份、sessionLive、Antigravity与DSH共59项通过；首次参考目录缺少undici导致后两套未执行，复用现有只读依赖路径后通过，未安装新依赖。Android消费测试通过仍不证明生产采集端部署。 |

当前正式签名APK 15,324,082字节，SHA256 `71a8a5a52744a7d5fdcc65562e24e8597a2fe11f4a2e615050c4eeb649ce0b98`；首轮测试APK SHA256 `cdcdc2761f7b2a4a431f6f6c0d21608fd146aa55d3ae3dcae675e6ff3f39d5d8`，新增界面专项后的实际测试APK为 `caebd0843f346a0d18160460a648c6b6dbd0f7b6973cabc91ef5a1de164e22c2`，不能混用指纹。code80旧匹配测试APK SHA256 `b5e393b9d91d67407972e8f803282c922e32b6ecb5d02d944af3ea6d9b20b1f8`。生产/测试及五项Gradle输入607个文件，按路径/原始字节/NUL顺序SHA256 `8b5c0d644dabb5bdd12b5a4a203ecb332d88097ed8a7b5c72c7574cc7c9bcde5`，输入提交 `f5db753c459230c1685af0eaca8f0608ffc0ab77`。原始日志在本机 `/tmp/token075-first-build.log`、`/tmp/token075-second-build.log`、`/tmp/token075-ui-build.log`、`/tmp/token075-upstream-tests.log`；不宣称这些临时文件随Git交付。

本轮首次24项设备运行中，`ProjectDeviceScreenTest.originalProjectCostOrderNfcMergeToolGradientPrivacyAndPinnedTotal`第53行严格像素断言（expected -11951184、actual -10835016）和`HomeQuotaEntryScreenTest.actualHomeStopSettingsAndThreeReductionModesDoNotReplayConsumedEntry`第149行动效断言（expected 0.6、actual 0.09973595）失败；候选各自单项复现。随后独立只读API36 ARM64 emulator-5688安装公开正式0.74.0-02/code80及匹配旧测试包，1080×2400/density420/fontScale1，原断言两项再次失败且数值完全相同。两测试源码自code79、code80至本次均未改；项目条绘制和额度动效产品逻辑本次未改，作为已存在的专项测试/时序限制保留，不扩大为本次修复，不删除测试或放宽断言，不称设备全套通过。相关首次失败与复现日志：`/tmp/token075-ui-first.log`、`/tmp/token075-project-repro.log`、`/tmp/token075-quota-repro.log`、`/tmp/token075-baseline-pair.log`。基线模拟器启动期间的安装尝试被系统拒绝、未形成有效测例；等待boot完成、核实code80后才运行上述比较。

正式升级实跑与五份文件/UID回执见本机`/tmp/token075-upgrade-seed.log`、`/tmp/token075-upgrade-final.log`、`/tmp/token075-upgrade-receipts.log`；曾误用不存在的方法筛选，runner报告0 tests，该次不计验证，后续正确方法1项通过。24项实际回归使用caebd084测试包。窄屏双主题配置及结果在`/tmp/token075-window-matrix.json`，原始公开样例截图已实际查看：[首页Bot不计运行](../design/session-bot-home.png)、[Bot分组](../design/session-bot-group.png)。这些不是生产Hub或实体手机截图。

此前07402四项界面行为只核对保留，未重复改布局；认证、SSE生命周期、缓存/偏好格式及费用未改。未操作真机或NAS/桌面运行服务；不把公开样例或模拟器当生产数据/实体手机。真实生产字段、OEM生命周期及用户私有资料未验证，非本次真机门槛。PR #21最终head `6970ebaa9509752cf2e315a4c90da4a11e22840f` 的自动代码审查completed、has_any_findings=false、has_unresolved_findings=false，reviews/inline comments无发现，文档CI通过；独立security_review未执行，不把空状态称为通过。按本次明确授权已合并并发布，回执如下。

发布复核（2026-10-20）：PR #21合并提交与正式Tag v0.75.0-01均指向 `414ddd3afbc937db8aa0662f2d96a3ba3d966b5b`；607个生产/测试/Gradle输入与已验收buildCommit f5db753逐项一致，沿用同一71a8a5a5…正式签名APK，未重建或重跑未受影响套件。[正式Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.75.0-01)已发布为latest，draft=false、prerelease=false。已登录latest API与匿名latest网页都指向本版。公开无凭据下载APK、SOURCE.json和SHA256SUMS.txt，全部逐字节匹配上传材料；公开APK再次核对正式证书一致。SOURCE SHA256 `5cb00051150c0ff50cb0707c8dcdd71eb4d0febb35cd7b7314dcb565251dc430`，SHA256SUMS SHA256 `263f95d42be4e48298ac1a71ec3ff3c33e4c0676a875bfc84f524b43305e1e49`，GitHub资产digest全部一致。SOURCE区分buildCommit f5db753与Tag/sourceCommit 414ddd3，不冒称APK内嵌VCS来源。发布材料及公开回读仅在本机`/tmp/token075-publication/`，不宣称随Git提供。客户端匿名更新API实际请求未验证；真实手机和生产服务未操作。此后仅原位补发行回执，不改已发Tag、APK、版本或产品输入。

## 当前正式发行验收：Android 0.74.0-02 / code80

检查日期2026-10-19（Asia/Shanghai）；基线 `45a6156b581d5c9ae484bd34bab48824ddc0915b`，开发前GitHub latest正式Android已核实v0.74.0-01/code79。本轮仅额度占位过滤、关于去重、直接日历与两个等宽描边入口；功能和边界见HANDOFF。最终生产/测试输入提交 `f4f732f5034aafc1aba6ac7cefc8814f605b169e`；现已合并并正式发布v0.74.0-02/code80，发布复核见本节末尾，没有操作NAS。

| 本轮实跑 | 结果与证据边界 |
|---|---|
| 标准构建 | 既有Wrapper/JDK17/SDK37离线release及正式签名AndroidTest构建成功；最终包为0.74.0-02/code80，不通过独立编译替代构建。 |
| JVM | 首轮完整531项、100类，0失败/错误/跳过，发生于编号递增及最后两处文字宽度修正前；领域纯逻辑此后未变。编号递增后另跑LimitDisplay/History/RecordCalendarProjection/HistoryReadPresentation四类26项全过，验证未使用占位过滤、配置异常、零额度/缓存、历史范围/空缺与读取状态。本轮没有反复重跑未受影响全套。 |
| 界面回归 | 修正About版本宽度前的同一产品包f51e36af…上24项最终通过：升级读取1、NavigationPolish2、RecordCalendar7、RecordHistoryRead2、TrendsNavigation3、HistoryRange1、HistoryUi1、AndroidUpdate4、LimitDisplay2、English1。保留时间边界、数据源缺失、状态恢复、费用/零值、返回/重入、许可证及手动更新断言；不借删除测试取得通过。 |
| 审查前窗口矩阵 | 7e5785f3…包上320/393/411dp（880/1080/1130px，density440）×真实系统fontScale1/2×双主题，共12组24项全部通过；浅色英文、深色中文，另既有英文深色回归。每组检查记录/分析两入口的等宽/齐边/同高/48dp及完整文字，确认无范围快捷项、无内嵌分析开关，独立页面返回且不发额外读取；关于应用版本/桌面参考完整、重复Hub信息不存在。每组原始截图已拉回，实际查看320dp/fontScale2双主题入口、日历与关于原图。结束恢复1080×2400/density420/fontScale1。 |
| 审查前包复核 | About宽度修正后追加6项通过：正式升级读取1、占位过滤界面1、纯手动更新4。正式0.74.0-01/code79先在独立只读API36 ARM64 emulator-5684用匹配旧测试包写公开合成加密缓存/历史及四份偏好，install -r到code80，未卸载/清数据；最终包仍保留UID及五份文件SHA，并成功解密。安装APK回读与最终候选完全相同。旧包数据准备1项单列，不算新增版测试。 |
| 静态与范围 | 最终Lint 0错误、113警告、1提示；较0.74.0-01多11条，来自已移除界面的未使用文案资源，不宣称全为既有警告，不删除资源或抑制规则掩盖。671对英中资源、文档门禁/self-test、git diff检查通过。无依赖/认证/SSE/数据格式改动；README不变。 |

最终应用APK 15,324,082字节，SHA256 `c2d367caeb161bc06beeaaab905a41e93f2e6c975e2677d098bba5f6d7154032`；包名仍dev.bing.tokenmonitor.nativeclient，min26/target37，正式证书SHA256仍 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。最终测试APK SHA256 `8a68a15d45cc97316483e27d3ef4d0c5e69f8248c5a70d09d9bccc84b90642b9`。生产/测试及五项Gradle输入的既定路径/原始字节/NUL清单SHA256 `5159b2e52a76c61eb48b3e4df2de56ddc86867cf6874327f9fd90d9ee638f119`。

首次失败与修正保留：模拟器尚未boot completed时尝试安装被平台拒绝；确认启动完成后才安装正式旧包并准备数据，没有清数据。按钮文字最初按内容宽度取整触发溢出断言（应用e4601ee5…、测试d7b27435…），改为填满按钮可用宽度并居中，保留原字号/完整文字断言；诊断截图与日志在`/tmp/token07402-action-diagnostic.log`、`/tmp/token07402-actions-first-failure.log`。日历首次7项4过3失败（应用f51e36af…、测试c5a7dfbc…），旧测试仍点击已明确取消的All records并要求范围总量；改为验证这些元素不存在，保留月份/年份恢复、权威日期与具体数值断言，最终7项全过（`/tmp/token07402-calendar-first-failure.log`）。额度既有偏好重建测试首次没有打开实际设置页（应用f51e36af…、测试b98ee46f…），补真实设置导航，原偏好持久化断言保持，最终2项通过（`/tmp/token07402-limit-settings-first-failure.log`）。320dp首个矩阵About版本文字的内容宽度测量触发溢出（应用f51e36af…、测试388cbf7e…），改用剩余/整行宽度后12组通过，未放宽断言（`/tmp/token07402-about-first-failure.log`）。

原始本轮日志：`/tmp/token07402-first-build.log`、`/tmp/token07402-final-tests-build.log`、`/tmp/token07402-about-fix-build.log`、`/tmp/token07402-ui-runner-final2.log`、各类`/tmp/token07402-<类名>-final.log`、`/tmp/token07402-matrix-summary.json`及每组matrix日志、`/tmp/token07402-final-upgrade.log`、`/tmp/token07402-final-quota.log`、`/tmp/token07402-final-manual-updates.log`、`/tmp/token07402-upgrade-receipts-final.log`。临时日志与全部矩阵截图不宣称随Git提供。选定公开原图：[浅色大字入口](../design/navigation-actions-large-light.png)、[深色大字入口](../design/navigation-actions-large-dark.png)、[直接日历](../design/records-calendar-large-dark.png)、[浅色关于](../design/about-compact-large-light.png)、[深色关于](../design/about-compact-large-dark.png)、[仅使用的额度](../design/quotas-used-only.png)。

自动审查追加复核：PR #19在8c4bd0b发现P2——缺少kind但带remainingPercent/remaining/used/limit等合法数值的旧Hub窗口可能被误当空占位。新增六种缺kind数值/零值/余额消费样例，修复前LimitDisplay类8项1失败（`/tmp/token07402-missing-kind-before.log`及对应first-failure.xml）；修复后同时识别实际额度数值，四类领域27项全过。标准构建及Lint成功（`/tmp/token07402-review-fix-build2.log`），一次把--tests放在lint任务后导致选项归属错误的工具准备失败仍保留在review-fix-build.log，未改测试门槛。

首轮审查修正输入为9a296df。全新只读API36实例中重新安装正式code79、匹配旧测试包准备合成资料，再install -r到上列ca3ce244…候选；最终5项全过：SignedUpgrade1、NavigationPolish2（增加缺kind的真实零值账户可见断言）、LimitDisplay2。日志为`/tmp/token07402-review-upgrade-final.log`、review-navigation-final.log、review-limit-final.log，测试包为上列6f939bd6…。实际安装包回读与候选一致，签名不变。审查仅改额度过滤与其测试；About、Dashboard、History及窗口检查方法源码保持，继承7e5785f3…包的12组24项局部布局结果，不冒称ca3重新跑过全部矩阵；此前完整531项和最终26项记录也按各自阶段保留，不冒称新增测试已跑完整532项。已处理该确认问题，后续审查状态以最新PR提交为准。

二次复审追加：3c7a7d6发现另一P2——只保留在displayRecord内的giftBalance/cashBalance、套餐及分时消费可能被过滤。按现有显示合同识别余额数值（含真实零）、套餐过期、可显示的分组/到期信息、tranches、usageSummary、重置次数和balanceUsd；币种/周期等空元信息、畸形或非有限数值不作为实际读数，不递归接纳任意未知JSON。无kind的无限额度明示也保留。16种投影缓存及3种空/异常对照加入回归，修复前该类9项1失败，首次证据为`/tmp/token07402-balance-before.log`与balance-first-failure.xml；修复后四类领域28项全部通过。

最终产品输入f4f732f，上列c2d367ca…APK及8a68a15d…测试APK标准构建/Lint成功，0错误/113警告/1提示不变（`/tmp/token07402-balance-fix-build.log`）。最终包重新执行5项全部通过：升级读取1、NavigationPolish2、LimitDisplay2；在额度页验证缺kind真零账户仍可见，以及仅有赠送/现金余额和套餐数据的缓存账户确实显示100%套餐剩余额度。独立只读模拟器重新用正式code79与匹配旧测试包准备资料，正常覆盖到code80，UID及五份加密/偏好文件保持；实际安装包回读与候选相同，签名再次核对。日志为balance-upgrade-seed.log、balance-upgrade-final.log、balance-navigation-final.log及balance-limit-final.log，均位于`/tmp/token07402-`前缀。本次复审只修改额度存在性判定与相关测试，继续明确继承前述未改布局的矩阵，不重复未受影响整套。

未验证：真机/私有数据、生产Hub与真实账户。额度占位判定基于Hub状态和已有额度/余额，不声称生产响应已联调；不把配置/余额为零的账户误当未使用。与本次无关的既有未使用资源和其他待办不扩入修复。

发布复核（2026-10-19）：用户对先前范围问题明确选择“合并并发布正式版”。PR #19最终fb2abd4的自动代码审查completed、has_any_findings=false、has_unresolved_findings=false，文档CI成功；未执行独立security_review，不把空状态称为通过。合并提交与Tag v0.74.0-02均指向 `96f9c744199774e49d1355b998e2712836edd64c`。逐项核对600个生产/测试及Gradle文件与已验收构建输入相同，输入指纹5159b2e5…不变；沿用同一c2d367ca…正式签名APK，没有重建或重跑未受影响套件。

[正式Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.74.0-02)已发布，draft=false、prerelease=false，设为latest。已登录latest API与匿名latest网页均指向v0.74.0-02；公开URL无凭据下载APK、SOURCE.json和SHA256SUMS.txt全部HTTP200，逐字节匹配上传材料，公开APK正式证书再次核对一致。APK 15,324,082字节，GitHub digest与上列SHA256一致；SOURCE SHA256 `9437ddc2ad1f7bd002e0b6839d2a05d3e35f287a92e8a1404379843be3327f14`，SHA256SUMS SHA256 `138b5ae8b02f24a52a9781de6052dc31094a1cd57c2cfbf488135575c18e8818`。SOURCE明确区分buildCommit f4f732f与Tag/sourceCommit 96f9c74，不冒称APK内嵌VCS来源。发布材料及公开回读在本机`/tmp/token07402-publication/`，不宣称随仓库提供。匿名latest API返回403、remaining=0，为当前出口配额耗尽；不声称本轮实际客户端匿名更新检查成功，不修改客户端认证或重试绕过。

发布准备首次脚本因非UTF-8源字符被Python拒绝，尚未创建Release或上传附件；Tag已正常创建并核对目标。改用ASCII脚本读取既有中文说明后创建草稿、上传三件附件并发布，未更改产品APK。发布前核对也发现命令行PR快捷入口指向其他同名仓库，撤回误发的审查请求，最终审查、CI、合并及Release全部使用明确的Gott-mit-Uns仓库路径及提交号验证，没有向其他仓库发布产品。

## 上一正式发行验收：桌面 v0.74.0 / Android 0.74.0-01

检查日期2026-10-19（Asia/Shanghai）。开发前最新正式发行Android为v0.73.0-01/code78，桌面为v0.74.0，annotated Tag解引用到 `cbed5915e294abb4a210f7ebb385e9415f706821`。产品与测试输入提交 `73ba7b57f6b42e010a4dac594e9d630460b8b637`。用户随后明确授权“合并，发布”：PR #17已合并至 `69a77f8b78cf3eae9bd98ba5434854f6afba70d1`，Tag v0.74.0-01指向同提交，正式0.74.0-01/code79已发布为latest。发布沿用以下同一已验收APK，未重建；下表为此前候选实跑，本次发布仅复核原材料，未操作NAS。

| 本轮实跑 | 结果与边界 |
|---|---|
| 标准构建与完整JVM | 既有Wrapper Gradle9.8.0/JDK17/SDK37离线执行release单元测试、Lint、assembleRelease及release AndroidTest；最终构建成功。100类530项JVM，0失败/错误/跳过。没有使用独立编译替代正式构建。 |
| Lint与静态 | Release Lint 0错误、102警告、1提示，与已发布基线数量一致；671对中英资源、文档门禁/self-test及git diff检查通过。测试截图的滚动定位修正后另构建最终测试APK，未重跑未受影响的产品套件。 |
| 固定桌面来源 | 本轮Git fetch正式Tag并固定cbed591；上游projectRows/history/usage共121项通过。生成器校验三个源文件SHA；新增Unicode等价排序ID样例，共9组项目样例。固定输入receivedAt/now，仅规范化输出外壳updatedAt，全部usage保持真实固定生产函数输出；重复生成逐字节一致。 |
| 分类边界修复 | 新增NFC/NFD不同模型ID参考后，定向7测首次1失败；原sorted map将排序等价键覆盖，改为稳定排序entry列表，保留各来源ID与总量。完整530项最终通过；不改变模型原始字段、费用或Hub合同。 |
| 正式签名覆盖 | 独立只读API36 ARM64模拟器emulator-5684，先安装公开正式0.73.0-01，匹配旧版接口的正式测试包准备公开合成加密缓存、历史及四份偏好；install -r到code79，无卸载/清数据。升级测试通过，UID10213及五份文件SHA前后逐项一致；最终安装APK回读与候选逐字节一致。 |
| Compose定向回归 | 最终测试包上ProjectDeviceScreenTest 4项全过，覆盖项目顺序/合并/隐私/工具颜色、设备/系统信息、展开后工具模型切换与跨项目共享、中英未知模型、48dp触控、V16主偏好字节不变、新模式旁文件降级/清理、合成加密项目models冷读。另SignedUpgrade读取1项通过；旧包数据准备1项通过，分开计数。 |
| 实际窗口矩阵 | 约320/393/411dp（880/1080/1130px、density440）×系统fontScale1/2×浅/深色，共12组12项切换测试全部通过，每组含英中显示及公开截图。结束恢复模拟器1080×2400/density420/fontScale1；不是替换Compose Density。已实际查看320dp/fontScale2浅色英文及深色中文原图。 |

候选应用APK：15,324,078字节，SHA256 `6f28ca11eeb6ef19f2d8673c42c90da4633956b5f2249c4e63b9b9711e44e299`；applicationId `dev.bing.tokenmonitor.nativeclient`，0.74.0-01/code79，min26/target37。正式证书SHA256仍为 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。最终测试APK SHA256 `b5e393b9d91d67407972e8f803282c922e32b6ecb5d02d944af3ea6d9b20b1f8`；旧版匹配测试APK SHA256 `cdce2c4b8b9d7fd3855d1ef6cab6523b8ea675018657afcd02495e976b1eae97`。生产/测试/构建输入清单（app/src/main、androidTest、test和五项Gradle输入，按路径/原始字节/NUL顺序）SHA256 `e4d72a7fcefa429957161a478e9fe73c575cb3e22647ba44ab06a89b7d56170d`。

公开界面原图：[320dp大字体浅色模型分解](../design/project-models-large-light.png)、[320dp大字体深色中文未知模型](../design/project-models-large-dark.png)。本轮原始日志位于本机临时路径：`/tmp/token074-verification-build.log`（首轮530全过）、`/tmp/token074-verification-final2-build.log`、`/tmp/token074-verification-ui-test-build.log`、`/tmp/token074-verification-upstream.log`、`/tmp/token074-upgrade-receipts.log`、`/tmp/token074-upgrade-final.log`、`/tmp/token074-projects-standard.log`、`/tmp/token074-ui-summary.json`及`/tmp/token074-ui-<宽度>-font<字号>-<主题>.log`。不宣称这些临时文件随Git交付。

首次失败保留：原接续前SocketException记录见下节，本轮同一工具链标准构建/ADB可用，未切换到其他主机。新Unicode样例失败在 `/tmp/token074-collation-repro.log` 与 `/tmp/token074-collation-first-failure.xml`；一次定向调用遗漏release测试开关导致任务选择失败在 `/tmp/token074-collation-before.log`。首跑升级准备误用新接口测试包调用旧APK，发生NoSuchMethodError（`/tmp/token074-verification-upgrade-seed.log`；旧应用SHA3c5b6c00…、测试SHA739d050e…），改用固定旧Tag构建的匹配测试包后成功，不改产品/断言/旧包。新增尺寸断言首次缺unit.height/width导入，编译失败在 `/tmp/token074-verification-final-build.log`，补导入后通过。UI首跑3过1失败，原因是对合并语义树中不存在的model子Tag执行滚动；保留原中英文字断言，改为滚动实际列表项，最终4项及矩阵通过（`/tmp/token074-projects-first-failure.log`；应用SHA6f28ca11…、测试SHAec04f9fb…）。

未验证：真机/私有数据、生产Hub、真实账号及新采集器部署效果。Grok推理和固定范围分类修正仍依赖生产端；本轮只验证固定源样例的正确消费。OMP逐轮正文仍缺远程合同，macOS窗口/玻璃/刘海不适用；fullyAligned=false保持。真机按AGENTS为可选，不作为PR门槛。README未修改；自动审查及CI以PR #17最新提交实际状态为准。

## 本次发布复核（2026-10-19，Asia/Shanghai）

用户“合并，发布”为本次明确授权。PR #17合并前最终提交24889bc的自动代码审查completed、has_any_findings=false、has_unresolved_findings=false，文档CI成功；独立security_review未执行，不将空状态称为通过。Tag v0.74.0-01解引用 `69a77f8b78cf3eae9bd98ba5434854f6afba70d1`。逐项比较app/src/main、测试树及五项Gradle输入，Tag与已验收73ba7b5对象ID全部一致。正式APK沿用SHA6f28ca11…原工件，签名、包名和0.74.0-01/code79再次核对一致，没有重建。

[正式Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.74.0-01)已发布，draft=false、prerelease=false；已登录latest API及匿名公开latest页面均指向v0.74.0-01，页面HTTP200。附件仅规范APK、SOURCE.json和SHA256SUMS.txt；APK 15,324,078字节，GitHub资产digest与本地SHA一致。SOURCE区分buildCommit73ba7b5和Tag/sourceCommit69a77f8，不冒称APK内嵌提交信息。公开URL无凭据下载三份附件，与上传材料逐字节一致，并复验下载APK正式证书。SOURCE SHA256 `6239eb2b5936c86b5ef157c55a93fb1bbd2f81bdb02248ea257ad48094369d86`；SHA256SUMS SHA256 `77d774589a85da9f725f3c92ce60218d64fcf5c637940609a3a1e37b79eebe87`。发布材料及公开回读在本机 `/tmp/token074-publication/`，不宣称随仓库提供。

限制与首次失败：匿名latest API返回HTTP403，响应标明x-ratelimit-limit=60、remaining=0，属于当前出口匿名GitHub API配额耗尽；没有修改客户端认证、重试或更新逻辑来绕过。已登录latest、匿名latest网页和公开附件下载已验证，不能声称本轮实际客户端匿名更新检查成功。此前候选单元/界面/升级结果按上节提交与工件继承，本轮仅复核来源、原记录、公开工件与发行状态；真机、真实账号、生产Hub和新采集器仍未验证。没有卸载/清数据、NAS操作或其他生产部署。回执仅原位更新当前文档，不扩写README。

## 此前候选补充检查（2026-10-19，本轮接续前）

检查日期2026-10-19（Asia/Shanghai）；源码基线 `d22209bbf45032f7f0af3588ae6259519dbe635d`，候选code79。固定桌面Tag/提交和逐增量处理见HANDOFF；本轮未产生候选应用APK或测试APK，故没有可填写的候选APK SHA、签名或安装记录。正式版仍为0.73.0-01，本节不得解读为已发版。

| 实跑项目 | 结果与证据边界 |
|---|---|
| 上游身份与本地源码 | GitHub latest=v0.74.0，正式非预览；Tag解引用cbed591，收尾再次查询latest仍为v0.74.0。比较数据中的103个变更文件逐个重新计算Git blob SHA，全部与固定比较响应一致（0不匹配）。未用浮动main。 |
| 固定来源样例 | `tools/capture-project-models-074.cjs <固定源码目录>` 对renderer、usage、history三个输入先核验SHA256再生成公开8组项目样例、Grok/历史/真实aggregateDevices项目wire。新完整registry逐项对比；旧0.73标题/计时样例保留其自己的来源提交。 |
| 上游定向测试 | Node24.21.0执行固定源码projectRows、history、usage三组共121项，全部通过；不是生产Hub联调。 |
| Android全量源码编译补充检查 | Gradle受限后，使用既有JDK17、Kotlin/Compose编译器2.4.20及现有runtime-artifacts精确依赖，直接编译全部main Kotlin源码；平台37.0 AAPT2先编译/链接当前资源并生成真实R。全部通过。此为独立编译检查，未打包APK，不能替代Wrapper构建、Lint或签名验收。 |
| Android定向JVM补充检查 | 同编译器编译并由JUnit4.13.2直接执行Desktop074Compatibility、Desktop073Compatibility、ProjectDevice、DesktopParityMetadata、HubBuildInfo五类共30项，0失败。覆盖固定renderer输出、HTTP式JSON投影、full/freshness业务隔离、旧Hub、缺失/异常/超额模型、会话回退与不完整禁用、别名、未知项、大整数、Grok不重复计数、三固定范围分类和registry。不是Gradle完整523项重跑。 |
| Android设备测试源码编译 | ProjectDeviceScreenTest及VisualEvidence直接Kotlin/Compose编译通过。新增展开/折叠、共享选择、中英、无额外读取、V16偏好主文件一致与可选模式降级、加密缓存冷恢复断言；尚未实际运行，不能声称这些断言已通过。 |
| 静态门禁 | 英中671对资源检查通过，git diff --check通过；文档门禁0发现，self-test全部通过。README未修改。 |

首次失败与修正：Wrapper构建在创建FileLockContentionHandler时因 `java.net.SocketException: Operation not permitted` 终止；ADB启动本地监听也报Operation not permitted。没有改工具链、移除锁或降低断言绕过。首次独立编译准备曾误用不存在的android-37路径和旧工作区classes，改用实际android-37.0平台/当前完整源文件后通过；这一工具准备失败不是产品构建失败。定向JUnit首轮28项有1项失败：旧futureBuild样例core86已成为当前已知修订，改为仍未知的未来core89并保留remoteNewer断言。新增固定范围样例首跑误按周一推断Locale.US周起点，实际美国周日，按既有合同修正为WEEK/7D/30D都应保留200且分类为0；最终30项全通过。没有应用/测试APK可关联这些首次失败。

当时尚未完成（标准构建、Lint、签名、覆盖安装及受影响UI现由上节补齐）：标准Wrapper构建、完整必要回归/Lint、正式签名与覆盖安装、实际Compose/截图/大字体验收、真实Hub字段及生产新采集器效果。旧0.73的设备/签名/缓存结论仅作历史参考，本轮未把它们写成候选已通过。后续在具备本地通信权限的环境补齐；不要求提供真机，不清数据、不操作NAS。当前交付草稿PR，不能合并/发布为已验收版本。

## 上一正式发行验收：桌面 v0.73.0 / Android 0.73.0-01

检查日期：2026-10-18（Asia/Shanghai）。分支起点 `20d6df77cd0fc759a96386e658422a448e097649`；上游latest正式版Tag固定为 `v0.73.0` / `1190260afdcacd8998938de311e0435c306a18ed`，收尾再次查询仍为同版。用户随后明确授权合并PR #15并发布客户端、不要求真机测试、不操作NAS；PR已合并，v0.73.0-01/code78已正式发布为latest。构建生产输入提交ac96e168与Tag合并提交a06c822b逐项相同；本次复核此前同一工件与原始验证记录，未重跑不受影响套件。逐上游增量处理及合同限制只见 [HANDOFF](HANDOFF.md)。

| 本轮检查 | 实跑证据与范围 |
|---|---|
| 正式公开发行 | [v0.73.0-01](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.73.0-01)，draft=false、prerelease=false且为latest；Tag指向 `a06c822b6ce17afbca442d6d8564453862336a37`。匿名latest API返回正式Tag与唯一规范APK入口；公开URL无鉴权下载APK、SOURCE.json、SHA256SUMS.txt并逐字节匹配上传材料，APK版本/code78和正式证书再次核对一致。SOURCE区分构建与Tag提交，不冒称包内嵌VCS元数据。PR #15最新代码自动复审完成、无发现，文档CI成功。 |
| 完整JVM | 最终523项、99类，0失败/错误/跳过；Wrapper/JDK17/SDK37 release变体离线执行。固定桌面sessionRows直接生成48个标题参考，覆盖私用区三平面、正常/非匹配前缀、非OMP工具、隐私隐藏及原始元数据保留；完整registry逐项比较。固定桌面生产代码生成6个Hub wire样例，覆盖部分计时、明确零、旧二进制行为、无duration、reasoning及上限，验证period/session/model消费，不在手机补算。 |
| 业务更正 | stats完整替换旧正值为明确零，经JSON投影、解析及带恶意旧业务字段的freshness后仍为零；旧会话不重现。模拟器独立加密缓存冷读保留原始OMP标题、零值与计时字段。不是生产采集端或生产Hub联调。 |
| 正式签名覆盖 | 临时只读克隆API36 ARM64 emulator-5592，正式0.72.0-02/code77先写入独立公开合成加密Hub历史和四份显示偏好，install -r覆盖到候选code78，无卸载/清数据。UID10213、五份文件哈希逐项一致，升级包成功解密还原；实际安装APK回读SHA与候选完全相同。实体手机私有数据未验证。 |
| 既有要求回归 | 候选APK上17项通过：SignedUpgrade、AndroidUpdateScreen、LauncherLabel、SyncDeviceGrid、FontRowAlignment和新增OMP首页/列表/摘要/缓存检查；包括纯手动更新、前后台不自动检查、重置语义及Hub别名隔离。此次测试包SHA为007087e4…（下文完整值）；后续仅在新增测试类增加长标题像素检查，产品APK与上述测试类既有逻辑不变。 |
| 真实窗口矩阵 | 约320/393/411dp（880/1080/1130px、density440）× 系统字号1/2 × 浅/深色，共12组36项；新增测试类检查首页/列表/摘要标题一致、长标题全文换行与可见行边界、原始缓存保留。矩阵不通过缩小字体、截断数据或清应用数据取得结果。结束还原1080×2400、density420、系统字号1。 |
| 构建/静态 | 正式release APK和测试APK构建成功；最终Lint 0错误、102警告、1提示，与原基线告警数量相同。670对中英资源、文档门禁、git diff --check通过。未改依赖、签名、加密存储格式或R8。 |

正式应用APK（此前候选同字节）：versionName0.73.0-01/code78，applicationId仍 `dev.bing.tokenmonitor.nativeclient`，15,307,246字节，SHA256 `3c5b6c00c85bb362a67036c1b7bab97f448b24c10f0eb3b4bdd973fecb814453`；正式证书SHA256仍 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。17项专项测试包SHA256 `007087e4e8537ad78f42e0c47cfdd5914c947a636f5cefecb7774a7e67c5a1cf`；最终矩阵测试包1,801,737字节、SHA256 `cdce2c4b8b9d7fd3855d1ef6cab6523b8ea675018657afcd02495e976b1eae97`。产品源码在上述两批测试间不变。

本轮原始日志及公开指纹：`/tmp/token-monitor-073-build.log`、`/tmp/token-monitor-073-final-unit-lint.log`、`/tmp/token-monitor-073-ui-after-standard.log`、`/tmp/token-monitor-073-upgrade-receipts.log`、`/tmp/token-monitor-073-matrix/summary.json`及每组instrumentation.log，最终摘要在`/tmp/token-monitor-073-final-evidence/`；本地临时路径不宣称随Git发布。

原图：[OMP修改前](../design/sessions-omp-before.png)、[OMP修改后](../design/sessions-omp-after.png)、[首页浅色](../design/sessions-omp-home.png)、[320dp字号2长标题浅色](../design/sessions-omp-large-light.png)、[同配置深色](../design/sessions-omp-large-dark.png)。前后列表两图均为1080×2400/density420/字号1深色、相同公开样例，时间为各次实际渲染时刻；其余是明确配置的产品原始渲染，长标题图截取实际摘要区域。均不是生产Hub或小米截图，已实际查看前后列表及大字体双主题摘要；没有P图补字。

首次失败保留：新增JVM测试错误假设HubParser不trim标题边缘，实际既有解析规则会trim；修正为验证获准元数据后，通过全部48个桌面参考，未修改解析规则。首次UI在打开摘要后的截图onRoot匹配两个窗口根节点而失败，标题断言已通过；改为定位已有摘要Tag，未修改产品弹层或断言标准。首次日志为`/tmp/token-monitor-073-unit-first.log`、`/tmp/token-monitor-073-unit-first-failure.xml`、`/tmp/token-monitor-073-ui-before.log`，首次UI应用为正式code77/SHA727f78c9…，测试APK SHA256 `101fafbc3166fd1c146e3f57c8be343680dd419f2ef0832272c52a3a595aebb9`。修正后旧包浅/深基线共5项（含升级资料准备）通过。

本轮未操作NAS、桌面运行服务或生产连接；OMP逐回合正文缺Hub合同，Cursor新采集逻辑和新scanner实际运行效果未验证。实体手机、生产Hub、实际账号、真实切网与OEM行为未验证，按项目规则不构成真机发布门槛。fullyAligned=false保留；不把本轮模拟器或固定源码样例称为生产端到端对齐。README未修改，既有背景图和其他历史待办未扩入本轮。

## 已发布基线验证：0.72.0-02 / code77

检查时间：2026-10-17（Asia/Shanghai）。用户已明确授权发布Android；所有更新均不要求真机测试，真机未测仅标未验证，不阻塞交付或已授权发布。未改NAS、未实施0.73对齐。构建提交 `e308802a9611be1b93c446e7eea3e64447719a7c`；PR #13已合并，发布Tag `v0.72.0-02` 指向 `8917d8032acd2d460ceb0db025c0f86a3c765c2a`，其生产构建输入与构建提交逐项相同。

| 检查 | 实跑结果与适用范围 |
|---|---|
| 正式公开发行 | [v0.72.0-02](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.72.0-02) 已发布为latest，draft=false、prerelease=false；公开URL无鉴权下载APK、SOURCE.json和SHA256SUMS.txt，三者逐字节匹配本地上传材料。APK共15,307,246字节，SHA256与下文一致；下载包正式证书及0.72.0-02/code77再次核对一致。SOURCE区分构建提交和Tag提交，未冒称包内嵌VCS元数据。PR #13最终代码自动复审完成、无发现，文档CI通过。 |
| 完整JVM | 当前release变体518项、98类，0失败/错误/跳过；包括纯手动检查、旧自动配置失效、重建/取消、单请求、408/503/302无隐式重试、本地别名隔离/冷恢复及Hub网络恢复。本轮完整执行，取代此前仅49项定向结果作为本次JVM证据。 |
| 当前正式包UI | API36 ARM64临时只读克隆emulator-5592，最终包20项通过：正式升级读取、LauncherLabel、AndroidUpdateScreen、SyncDeviceGrid、ConnectionSettings、HubStore、字体行常规深色；另320dp/系统字体2/应用最大字号浅色字体2项通过，共22项。不是实体手机或生产Hub测试。 |
| 正式签名覆盖 | 已安装正式0.72.0-01/code76，SHA256 `db90f7e985cfa14c9f799171c0e114228a3a369ccf55834ab89f602d13c2b977`，用旧包写入独立公开合成加密Hub缓存及四份显示偏好，再install -r到code77；无卸载/清数据。UID10213及五个加密文件哈希前后逐项一致，新包成功解密并还原历史、连接配置和偏好。 |
| 包身份 | 实际APK applicationId `dev.bing.tokenmonitor.nativeclient`，versionName0.72.0-02/code77，min26/target37；应用及启动入口最终标签Token Monitor。新旧正式证书SHA256均 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。 |
| 构建/静态 | Wrapper/JDK17/SDK37正式release与签名测试包构建成功；Release Lint 0错误、102警告、1提示。语言门禁、文档门禁、diff检查通过。无依赖/签名变更或R8优化。 |
| 有界继承 | 本PR此前code76候选APK `5c75594b34146e0985d8f361b37c7a4b17be7b9b8cfd53b5b7219901db7bb038` 的14组/28项真实窗口字体矩阵、深色22场景分批及浅色12项结果仅继承未改UI范围。本次只增发行编号与文档，app/src/main与该候选的Git树相同；未将旧28项写成本包重跑。矩阵为约320/393/411dp × 系统字体1/2 × 两主题，加320系统2/应用最大字号两主题；检查共用列/断点、8dp间距、48dp触控、无缩字/省略和原重置语义。 |

最终应用APK SHA256：`727f78c982174817e6602f5946a47b3c0d3b5590d68bb4bd2ac6563fd3f1154b`；最终测试APK：`163784a73cb3b6574acf2386681589052bb40aeceefbf9ccd0bc21de936f4c9b`。原始本轮日志：`/tmp/token-monitor-release77-build.log`、`/tmp/token-monitor-release77-seed.log`、`/tmp/token-monitor-release77-ui.log`及`/tmp/token-monitor-release77-fontlarge.log`，JVM XML在app/build/test-results/testReleaseUnitTest。仅公开指纹，不保存生产凭据或统计明细。

对比截图：[常规修改前](../design/settings-fonts-before.png)、[常规修改后](../design/settings-fonts-after.png)、[320大字修改前](../design/settings-fonts-large-before.png)、[320大字修改后](../design/settings-fonts-large-after.png)、[长名称深色](../design/settings-fonts-long-dark.png)、[设备卡片](../design/settings-devices-after.png)、[纯手动更新](../design/settings-updates-after.png)。这些是此前相同UI源的公开样例原图，不是小米或生产Hub截图。

首次失败仍可追溯至本PR先前提交的当前记录及原始日志：Debug/正式签名冲突、固定测试Provider冲突、字体测试导入及unmerged语义节点问题、旧域名回退预期均已处理；弹层外点击因模拟器SystemUI无响应遮挡失败，试验坐标修改已撤回，原坐标/断言专项通过。产品弹层没有因此修改。本轮临时模拟器启动同样出现SystemUI弹窗，先关闭该弹窗再执行UI验收，未改应用或清数据。

用户提供的SSH诊断：生产Hub支持TOKEN_MONITOR_HUB_DISPLAY_NAME但未配置，所以当时鉴权响应不带hub.displayName；用户最新明确确认服务端未配置名称的问题已经解决，本聊天未独立复验生产响应，未改服务端。客户端真实名称优先及当前连接别名通过公开快照/模拟服务验证；不宣称生产名称已显示。未验证范围还包括实体手机私有数据、OEM生命周期、生产网络/真实切Hub及公网匿名手动更新成功请求；真机未测不是发布门槛。README保持原样，不新建逐版本报告。

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
