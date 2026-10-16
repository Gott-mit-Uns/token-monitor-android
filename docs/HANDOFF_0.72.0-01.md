# Android 0.72.0-01 联合交接

2026-10-16 用户后续要求：正式Release直接作为latest，当前APK统一命名token-monitor-android-0.72.0-01.apk，停止旧编号兼容发行。下方首次双编号发布与旧更新器通过记录是命名变更前的历史证据；当前策略及复验以本文末尾命名回执和VERSIONING.md为准。

**当前状态：Android0.72.0-01/code76正式Release与latest兼容0.2.67已于2026-10-16公开，两Tag同源3c4d2d6，最终db90字节保持。两Release共8附件已匿名完整复核、两APK同字节db90；实际公开新旧更新器与官方64→75→公开76升级／冷保留已通过。历史候选／失败／视觉继承边界保留，完整最新回执见文末。**

本轮目标固定桌面 v0.72.0 `126e9a43dcc51d3d7b2609231eac9974ba73bed7`，Android `0.72.0-01` / code76，latest兼容计划为 `v0.2.67`。包名、正式证书、历史版本及未提交原工作区保持；Widget不恢复，R8不实施。完整提交/排除/数据依赖清单见 [逐项对齐](DESKTOP_072_ALIGNMENT.md)。

## 联合范围

用户追加确认：侧边“优化 Token Monitor Android 设置与明细 UI”与“安全对齐 NAS Hub/Agent 上游版本”两项都纳入本轮联合验收与交付说明。

- Android设置/明细任务已于0.71.0-02/code75完成并发布，对应产品5994b51、文档main b9a8de1。当前分支以b9a8de1为起点，已包含全部产品改动，无需重复cherry-pick或分别发布。设置、字体、只读价格、额度说明、订阅去重、两列真实设备卡及日/月日历在本轮回归。
- NAS任务已发布v0.72.0-01，固定产物源8444a9b1f3aeb88e164fd20b9773a9984d3d33a0；侧边最终文档main005bbd2455dcc66c8c3268b23cc89e047db2395f。定向git diff确认两提交的OpenCode reader、usage和sync运行源码没有差异。本轮直接复用该审计源码生成旧/新OpenCode wire样例，未再次修改或发布NAS。
- Android最终仅一份联合APK与Release；配套NAS继续使用其独立仓库的固定发行。已发布镜像不等于默认NAS已部署，Hub现网字段与新provider样本分别记录。不修改生产NAS、Agent或桌面设置。

## 当前实现

分组调用次数、原始缓存分子/分母、duration加权速度、最近活动最大时间、委托角色续接及组/成员缺值提示已实现。Cursor Included usage / USD prepaid /历史spend分离，预付无虚假百分比，旧账户与缺值/数据过期降级已实现。OpenCode本地SQLite恢复属生产端，Android兼容归一化wire，不承诺远端逐轮正文。更新仅前台触发，成功24h/自动失败1h退避均持久化；手动可重试，后台取消，不弹窗、不唤醒。

## 验证进行记录

冻结版本前完整505单元通过；完整Lint0错误、103警告、1提示，首次Lint Kotlin/UAST内部异常保留，独立低并发重试通过。版本提升时DesktopParityMetadataTest旧固定期望导致一次失败，已更新固定目标断言并再次505/0/0/0通过。

交叉审查随后修复：非法字符串messageCount不再与“未知”同时显示；自动失败退避跨进程持久化并增加2项测试；双语更新说明与前台机制一致；Cursor stale中文用“数据过期”，缺失/无效/过去重置日期的Home和完整卡一致。

上述审查修复后的首次统一执行因宿主磁盘空间不足，在测试事件报告写入阶段失败；这不是最终通过结果。保留原日志，并暂停自有模拟器，使用同JDK/SDK/Wrapper以单worker和较低构建heap继续，未删除/降低测试，未清应用数据。任务自有AVD数据压缩因加密数据不可压缩且空间不足未成功，原数据未替换；失败临时文件和RAM临时卷已释放。其他聊天工作区及原模拟器5556不动。

真实旧0.2.64官方APK的原解析器/UI已接受拟发布0.2.67元数据，实际APK为官方df7ab620…；这是拟发布fixture，不是公开Release读回。第一次误用新版测试宿主调用旧版新增前的Compose签名而NoSuchMethod，改用原0.2.64专用harness后通过，没有改变旧产品。该模拟器只生成一次公开测试缓存及四份偏好，再覆盖至官方0.71.0-02/code75，保留验证通过。最终76覆盖、最终截图及公开更新元数据仍需后续回执，不能提前称正式交付。

## 证据与未验证边界

本轮本地原始证据：`evidence/desktop-072/`，含baseline锁、API范围、首次失败与后续运行、源码生成OpenCode样例及43项生产端源测试、签名候选、旧版更新器和升级记录。最终发布材料须附明确源码commit/APK和测试APK SHA、原正式证书、实际安装及验收结果。

现网只读：公开health core84/runtime6、4设备；安全配置鉴权stats仅提取字段名和计数，未保存私有响应或凭据。样本没有Cursor provider，所以真实X Subscription/余额未验收。宿主读取不冒称Android联网。REA工具可见但本轮没有建立可用桌面调试连接，采用固定源码证据，没有安装或启动调试工具。

真机未连接，Xiaomi触摸/键盘、真实用户数据升级、真实Hub LAN/HTTPS/蜂窝和OEM后台没有本轮执行证据；原有未验证状态不被合成或模拟器结果覆盖。GitHub Actions查询当前工作流/运行均0；自动PR审查与本地测试需另记，不称远程CI通过。

## 用户确认后的联合整合

侧边NAS聊天在此前发行后继续进行Hub显示名称修复，Android独立提交`da65dc371523b0e3bcad7a59402bd0d6bd8152ee`已三方合入为`d5b78da`。当前可选`hub.displayName`仅来自鉴权完整快照，字符限制、旧Hub/DNS/未知降级、freshness不可改名和加密缓存路径明确；不改变Hub身份、collector ID或在线状态。配套新增合同目前在[NAS PR #11](https://github.com/Gott-mit-Uns/token-monitor-nas/pull/11)，未合并、未发布、未生产部署。公开发行NAS0.72.0-01与该可选后续补丁必须区分。

用户又明确确认“一并删除刷新订阅按钮”，故原并行六项改动经`6142b81`保留提交；不是以来源不明文件覆盖产品。未加载时保留读取订阅入口，已有数据和自动同步继续，完整已加载区域不再显示手动“刷新订阅”按钮。

合并前7c951cb对应正式签名候选453476…实际完成63次UI（普通及真实320dp系统font2、浅深色、分组/额度/日历/设置/更新）以及原64缓存与四偏好经75→候选76、真实默认Store75→候选76冷主页保留。首次截图检查发现大字体指标速度被省略，因此这63次不是最后联合包完成声明；后续将活动行自然换行，补无ellipsis/无视觉溢出/无祖先裁切断言，并在联合包复验。

跨绑定名称测试补充真实ViewModel A有名→B旧Hub无名、同地址更换credential、旧有名响应迟到不复活、缓存跟随新绑定；实际snapshot→forged freshness→无名stats链保持计数及费用并清除旧名。名称不另存于独立设置或未隔离的缓存。


## 历史前候选f7验收回执（随后已被17b7替代）

生产 APK 对应源码 `434c5110d4fb62677ca32dc02b7661a62ffc20ee`，app tree `131c8f1282d71cc4ddbc4afab1a302b2bea73e00`；`evidence/desktop-072/joint76/app-release.apk` 为 15,305,354 bytes，SHA-256 `f7bdecde08f7a4bab32d21ef94988a64afa8a67a51e800c7da998cf45530f2e1`。包名 `dev.bing.tokenmonitor.nativeclient`、版本 `0.72.0-01` / code76、既有正式证书保持。最终文档或纯测试提交若后续变化，必须在发布材料记录最终 Tag commit 与上述生产 app tree 的关系；用户随后新增范围触发新封包，旧f7保留为前候选证据，后续新包必须另记身份和复验结果。

- 完整单元 **511 通过，0 失败／错误／跳过**；完整 Lint **0 error、104 warning、1 hint**。这些现存告警没有被隐去，不宣称零告警。
- 最终 f7 生产包的模拟器验收 **78 次通过**：`ui-joint/summary.json` 中成功40次，`ui-joint-remaining/summary.json` 成功34次，`session-geometry-final/summary.json` 成功4次。包括日／月日历、紧凑字体／只读价格／额度设置、订阅入口与去重、Hub两列卡及离线名称、Cursor独立额度和预付、Android更新、加密缓存与会话指标；普通和真实320dp系统fontScale2、浅深色范围以各测试实际记录为准，不把每项都扩称四种配置全通过。
- 前74次使用测试 APK SHA `7aa0b789699b99df9f9665ae98f3247524d38f1bb5f5ea5c5b97f62255a7db12`。会话用例首轮在测试几何断言处失败，保留原日志／图；后续仅修正测试断言，生产APK仍为同一 f7，最终4次使用测试 APK SHA `4504878671a74df71ce28cc60070dde92c91cd9d9ab942161553319c51ee6411`。最后普通及320dp/font2两轮各2项通过，实际大字体会话调用／缓存率／速度自然换行且不省略。不能把初次失败写成一次性78全绿。
- 最终 f7 已实际覆盖安装，`joint76/app-release.apk-install.txt` 为 Success，`joint76/cold/installed-sha.txt` 回读安装包与f7一致。独立冷启动记录 `LaunchState: COLD`，`ColdProcessAcceptanceTest` 通过；初始64公开fixture及四份偏好在冷启动后由 `SignedUpgradeTest` 再次检查通过。路径 `evidence/desktop-072/joint76/cold/`。
- 真实匿名Android OkHttp桌面最新版查询另1项通过，记录 `joint76/desktop-public-client.txt`；这是桌面Release查询，不能代替发布后Android自身latest链路验收。
- 当前产品源码 `434c511` 的自动代码审查 completed、无发现／无未解决发现；security_review 返回空状态，不能称安全审查通过。仓库未配置 GitHub Actions，不存在本轮远程CI成功记录。

### 必须保留的升级链边界

真实官方旧 APK `0.2.64` / code73（SHA `df7ab620f0ca9d086ff30da761a232cb5bd99a9053356a3688c61006f1ab8c56`）先生成一次隔离公开测试缓存和四份偏好，再覆盖至官方 `0.71.0-02` / code75（SHA `d66aaa321ed8de6c8021f709141266018ee50a8c06114f05fe0f431518320ef3`）。随后覆盖到退役候选 code76（SHA `4534763381303b08e71cf4580929b3c9cc78428f0b4ae0b15bc2b673a67605b3`），再原位覆盖到最终 f7 code76，并实际校验缓存／偏好保留。全程保留原应用数据，不卸载／清数据。退役候选从未公开发布，最终相同versionCode的原位替换只属于本地候选验证；正式75→76版本顺序与同证书安装仍成立。此前63次UI属于退役候选，不计入最终78。

真实默认HubStore的75→退役76冷主页检查是此前独立路径；最终f7又实际冷启动并执行默认Store主页及原64隔离fixture保留检查，不能把两类存储或真实用户历史混为一谈。这里证明合成缓存与选定偏好的保留，不证明所有用户私有历史已真机迁移。

### 发布前后仍需收尾

目前是本地验收完成，正式Release未完成。仍须核对最终Tag提交、同字节正式／兼容APK和SHA附件、更新说明、公开Release/附件下载回读，并分别用旧更新器和当前候选读取实际公开metadata。拟发布metadata的旧APK验收不能代替发布后验收。现场Hub只读观察、主机端网络读取与模拟器fixture均不能冒称真实手机Hub/公网验收。

可选友好Hub名称的 NAS PR #11 仍未合并／部署，本次Android支持字段及明确降级不以该服务端功能已上线为前提。新版真机、真实Cursor X账号／余额、真实OpenCode采集和公网Hub、Wi-Fi/蜂窝/OEM后台仍未验证。关键截图仅选最终f7成功记录，见 [最终候选界面证据](UI_072_VALIDATION.md)。


## 历史阶段：0cf041e补丁纳入并触发重新封包

前述f7、511单元、78专项、冷启动及真实桌面查询均属于生产源码434c511的前候选，证据保留。侧边新增 `0cf041e`：会话Text使用fillMaxWidth，两个Hub fixture补上主题背景；这三处修复按用户联合范围纳入。生产APK即将重新构建，当前不能将f7称作待发布最终附件，也不能把其78次直接改写成新包执行通过。后续追加新源码／app tree／生产APK及测试APK指纹、单元Lint与受影响模拟器／冷启动／升级回执，再定最终发布材料。截图文档暂保留明确标记的前候选来源，Hub深色需以新主题背景图补充。

NAS PR #11当前头为 `1a0e570`，仍open，未merge／部署。此状态不因Android重新封包而改变。


## 历史17b7候选：511／78和冷启动通过，后续P2触发重新封包

本节取代前述阶段性的“重新封包待收尾”状态；原始失败及历史候选记录保留。最终生产APK位于 `evidence/desktop-072/joint-final76/app-release.apk`，**SHA-256 `17b7470e0a52b23a5e624d88f66999f9a3f91197b0cc964edab814dc70ac1991`**，15,305,354 bytes，Android0.72.0-01/code76。buildCommit为 `16f92a759c62522da94070f8acd5dd04e5ab5b45`，构建时整个app树 `17ca03a297a669515f58796af4fef03c43e9d8ad`（含androidTest，仅作构建记录）；包括会话完整行宽与Hub测试主题背景修复。包名和正式签名连续性保持。

- **511单元通过，0失败／错误／跳过；Lint 0错误、104警告、1提示**。证据 `joint-final76/unit-summary.json`、`lint-summary.json`。
- **同一17b7生产APK的78次API36模拟器专项完成**：`ui-joint-final` 成功18，`ui-joint-final-remaining` 成功54，`ui-joint-final-shared` 成功6。18＋54＝72次使用测试APK SHA `c116eb6e3b968b4ec183a6eba06efc478c75d082c8c143d8805aeadd17dfc200`；最后6次使用 `6439190bbc9a405a48b8bfca870ac2f77100acc9a29a419f5baec195f2354502`。生产APK始终17b7，不能把f7的78移记到本包。
- SharedData首轮及独立复跑两次 `ComposeNotIdle` 失败原样保留。测试未注入仓库时钟，retarget的 `System.nanoTime` 与Compose虚拟 `withFrameNanos` 混用；仅在SharedData测试补已有 `CompositionLocalProvider` 时钟，未关闭动画、修改产品或放宽断言。校正后普通浅深各2项和系统320dp/font2浅深各1项，共6项通过。成功78为明确分批结果，不称一次运行全绿。
- 最终包**真实匿名Android OkHttp桌面Release查询1项通过**（`joint-final76/desktop-public-client.txt`）；原64合成缓存／历史及四份偏好在最终包的 `SignedUpgradeTest` **1项通过**（`joint-final76/cold/original64-retained.txt`）。这些独立于78，不膨胀UI用例数；桌面查询不能替代公开Android自身latest检查。
- 最终包独立启动为 **LaunchState: COLD**，安装包SHA回读与17b7一致。明确带 `ownedEmulator=true` 的默认Store主页检查 `default-home-opted-in.txt` 和设置重建／后台恢复 `settings-recreate-opted-in.txt` 各实际 **OK(1 test)**，均有执行点号。早先不带opt-in的 `default-store.txt` 输出OK3，但三项受assumption跳过，**不计作三项冷启动通过**；原记录保留。证据均在 `joint-final76/cold/`。这些是模拟器公开fixture和选定偏好，不代表真实用户全部私有历史。
- [最终界面证据](UI_072_VALIDATION.md) 的10张图已全部更新为17b7成功记录：会话来自新包，Hub深色补正确主题背景，订阅来自校正时钟后的成功测试。来源及每张SHA见 `docs/images/alignment-072/manifest.json`；无旧候选图冒充新包。

正式v0.72.0-01／latest兼容v0.2.67尚未在本记录宣称发布。最终Tag提交若随后增加纯测试／文档，须对比 `app/src/main` 与全部Gradle生产构建输入的对象ID和buildCommit16f92a7一致，不能要求含测试的整个app树不变；上传同字节17b7正式／兼容APK后执行公开元数据、附件下载校验与新旧更新器读回。公开回执追加后才能更新“正式发布完成”。NAS PR #11仍open未合并部署，真机／真实Cursor账号与生产Android网络边界保持。


17b7历史阶段的源码绑定以 `joint-final76/production-source-trees.json` 的六条输入为准：`app/src/main`、`app/build.gradle.kts`、`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`。当时计划Tag与16f92a7比较；后续5b生产修复改变了输入，必须重新记录新封包六路径对象ID并由最终Tag逐项比对，发布SOURCE.json以新记录为准。SharedData测试变更会改变整个app树，故17ca仅为build时历史树记录，不作最终Tag必须相等的门槛。APK未声称嵌入源码commit：AGP报告 `NO_VALID_GIT_FOUND`，绑定依据是封包SHA、构建记录及上述生产输入比对。


## 历史阶段：自动审查P2配置重建修复并启动新包验证

最终自动审查在 `53d25bf` 确认P2：MainActivity.onStop无差别取消retained AndroidUpdateModel的在途请求，旋转／语言／主题配置重建后新Activity立即重请求；慢网络反复recreate可产生无界请求，违背节流目标。该问题不是用假失败时间戳掩盖，而是由 `5b1fb38` 最小修复：Activity传入isChangingConfigurations，Model仅对配置变化保留同一viewModelScope在途；真正后台仍取消，最终Owner.clear也由scope取消，没有后台唤醒或新认证流程。公开Application单参数构造兼容默认factory，internal测试source注入不改变默认请求。

新增 `AndroidUpdateLifecycleTest` 三项：同ViewModelStore连续8次配置stop/start只一慢请求，完成后正常成功节流且无伪时间戳；配置后真正后台立即取消、后台不请求、前台只一重试；最后stop为配置变化时清除Owner仍取消请求。**此处只是测试范围，完整单元／assemble／测试assemble／Lint正在统一执行，不预称新增3项或514已过。** 新包Android更新UI／安装版本fixture、冷主页／保留及真实桌面查询按受影响范围复验，最终新SHA及生产输入对象记录待追加。

本次生产修改只在MainActivity与AndroidUpdateModel生命周期，不改变视觉UI源；17b7上实跑78（18＋54＋6）和对应10张原图继续作为明确版本的UI参考，未受影响场景可限定继承。它们不证明新生命周期修复已通过，也不能改写为新APK全78已实际执行。17b7、f7及退役候选的原始指纹／失败／通过记录均保留，gallery图片原样不重绘。公开Release与新旧更新器真实读回仍待最终验收后执行。


## 最终候选db90：514单元与受影响9＋2通过，等待最终审查和公开读回

生产APK `evidence/desktop-072/release76/app-release.apk`，**SHA-256 `db90f7e985cfa14c9f799171c0e114228a3a369ccf55834ab89f602d13c2b977`**，15,305,354 bytes，Android0.72.0-01/code76；buildCommit `5b1fb385b731bd7081fcf54e108510df4f6943c7`。测试APK SHA `6439190bbc9a405a48b8bfca870ac2f77100acc9a29a419f5baec195f2354502`。原包名和正式证书保持；已安装APK回读SHA与db90一致。

- **完整514单元通过，0失败／错误／跳过**，包括新增3项Robolectric更新生命周期测试；完整Lint **0错误、104警告、1提示**，完整应用／测试APK构建成功。证据 `release76/unit-summary.json`、`lint-summary.json`。配置重建保持单一在途、真后台取消、最终Owner清除均有本轮逻辑证据，不再只是实现声明。
- **db90实际受影响验收9项通过**：AndroidUpdateScreenTest浅色3＋深色3，当前安装版本／拟发布兼容metadata不重复更新fixture1，真实匿名Android桌面Release查询1，原64合成缓存／历史与四偏好保留1。证据 `release76/affected-ui/summary.json`。这是本轮db90实际执行，不从17b7继承。
- **db90实际opt-in冷启动检查2项通过**：`release76/cold/home.txt` 默认Store主页1、`settings-recreation.txt` 设置重建／后台恢复1，均明确ownedEmulator true并实际执行；launcher记录COLD，`installed-sha.txt` 匹配db90。早期未opt-in的OK3仍属于assumption跳过，不计入。
- **78次视觉专项只作为17b7历史候选的限定继承**，不称db90重跑全78。`ui-inheritance-proof.json` 证明17b7→db90的生产main源码仅MainActivity及AndroidUpdateModel两文件生命周期变化，视觉UI源无变化；未受影响的分组／Cursor／设置／日月日历／卡片与合成主题大字场景沿用明确17b7版本及截图。更新／生命周期等受影响部分以上述db90新执行补足。gallery10张原图继续明确其17b7身份，不重绘／改标新包。

最终Tag与生产源码绑定改用 `release76/production-source-trees.json` 六条输入（main树 `e269bdee3683af6f72ddba43bd57c703079e886d`，其余Gradle输入按该文件），逐项对比buildCommit5b1fb38；测试／文档提交可改变整个app树。SOURCE.json须携带新六输入及buildCommit、最终Tag commit、APK／测试APK指纹。APK不声称内嵌commit，AGP的NO_VALID_GIT_FOUND边界保持。

最终源码自动审查尚待实际回执，本节不沿用53d25bf的旧审查结论作为通过。正式Release、同字节兼容v0.2.67、公开附件下载与旧／新更新器读取实际latest仍待发布后验证；当前仍是本地验收完成的候选。现网NAS没有因此改变，PR #11仍未合并部署，真机／真实Cursor／生产Android实网未验证范围保持。


## 正式公开发布回执（2026-10-16）

[Android v0.72.0-01](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.72.0-01)于 `2026-10-16T14:05:41Z`（北京时间22:05:41）公开；[latest兼容v0.2.67](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.67)于14:05:44Z（22:05:44）公开。两固定Tag均指向PR #7合入源码 `3c4d2d673605ba9359d66a577dce4488c4bfc083`，buildCommit `5b1fb385b731bd7081fcf54e108510df4f6943c7`。正式与兼容标题均为Token Monitor Android v0.72.0-01，latest保持三段Tag供旧更新器识别，并非另一个产品版本。

[正式APK](https://github.com/Gott-mit-Uns/token-monitor-android/releases/download/v0.72.0-01/token-monitor-android-0.72.0-01.apk)为15,305,354 bytes，SHA-256 **`db90f7e985cfa14c9f799171c0e114228a3a369ccf55834ab89f602d13c2b977`**，沿用原包名和正式证书。每条Release各有APK、SHA256SUMS.txt、SOURCE.json、VALIDATION.md四附件；**两Release共8附件均已完成匿名下载，文件大小／GitHub digest／SHA256SUMS／SOURCE sourceCommit全部核验通过，两APK byte-identical并匹配db90**。[SHA校验文件](https://github.com/Gott-mit-Uns/token-monitor-android/releases/download/v0.72.0-01/SHA256SUMS.txt)、[SOURCE.json](https://github.com/Gott-mit-Uns/token-monitor-android/releases/download/v0.72.0-01/SOURCE.json)记录APK、build/final源码和六条生产输入绑定；不声称APK内嵌commit。最终源码审查针对 `847b6d35ab64698fb398923b67158b9318f0ea73` completed、无发现／未解决项；security_review空状态不称安全审查通过。GitHub Actions0工作流，未宣称远程CI通过。

公开后执行 `public-updaters/official64-public-updater.txt` **1项通过**：实际安装官方0.2.64，其原解析器／UI接受匿名下载的实际v0.2.67 latest JSON；这是实际公开metadata输入，不是声称旧app自行发HTTP。`public-updaters/final76-public-updater.txt` **3项通过**：真实匿名Android自身HTTP更新源、真实桌面HTTP查询、当前说明与版本比较不循环。此前拟发布fixture与当前公开回执明确分开。

另重新执行真实公开升级链：**官方0.2.64/code73 → 官方0.71.0-02/code75 → 匿名公开下载的最终db90/code76**。仅在64生成一次隔离公开合成加密缓存／历史和四份偏好，UID、缓存／历史及偏好在75、76与独立冷启动后分别保留；未卸载／清数据。该公开链不是把早先4534／f7／17b7同code候选链重命名，证据 `public-upgrade/receipt.json` 及对应安装、保留、cold记录，最终已安装SHA为db90。

最终产品514单元、Lint0／104／1、db90受影响9＋2实际通过与17b7视觉78限定继承维持原范围；上述公开测试独立记账，不扩大为db90重跑全部视觉矩阵。真实手机、真实Cursor额度／余额、生产OpenCode采集及Android生产LAN／公网HTTPS／蜂窝／OEM后台未验证状态保持。

NAS后续 [PR #11](https://github.com/Gott-mit-Uns/token-monitor-nas/pull/11)已合并，可选鉴权Hub名称合同随 [NAS v0.72.0-02](https://github.com/Gott-mit-Uns/token-monitor-nas/releases/tag/v0.72.0-02)于2026-10-16T13:35:40Z发布，source `0f748427b7c75171d1c2fcaf40c0ef552bfb2f2d`。历史OpenCode／协议审计基于NAS0.72.0-01运行源码，新增名称合同与之分别记录；**实际NAS部署未验收，不宣称已部署或生产配置已生效**。旧Hub仍保留DNS／名称未知降级。


公开附件最终复核收尾：`public-readback/verified-assets.json` 与 `latest.json` 确认全部8附件匿名下载、大小／GitHub digest／SHA256SUMS／SOURCE sourceCommit通过，正式／兼容APK字节相同且为db90；公开APK签名／包名／版本由apksigner及aapt复核通过。兼容VALIDATION.md首次下载RemoteDisconnected原日志保留，retry1仅补缺失文件后全过，不重写第一次结果。正式Release与兼容latest及公开升级／更新器验证现均已完成；文档回执PR不重建、不重新发布或覆盖产品字节。


# 后续用户要求：统一正式编号并停止旧兼容命名

2026-10-16，按用户后续明确要求，停止旧编号兼容发行，正式 [v0.72.0-01](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.72.0-01) 直接作为GitHub latest。默认下载及本次两个发行入口中的APK均统一为 `token-monitor-android-0.72.0-01.apk`；旧兼容入口标记为已停用，不再承担更新发现。历史Tag保留追溯，未改写提交，更早已发布版本仍属于历史档案。

- [统一的latest下载链接](https://github.com/Gott-mit-Uns/token-monitor-android/releases/latest/download/token-monitor-android-0.72.0-01.apk)；[正式Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.72.0-01)。
- APK仍为15,305,354 bytes，SHA-256 `db90f7e985cfa14c9f799171c0e114228a3a369ccf55834ab89f602d13c2b977`，versionName0.72.0-01/code76，沿用正式签名。仅更正发行名称、latest选择和校验清单；没有重建产品APK或增加一套应用版本。
- 原兼容附件通过GitHub资产改名保留同一asset ID及digest；对应SHA256SUMS已同步新文件名，SOURCE与VALIDATION历史快照保持追溯。两条当前入口的APK名称及校验清单均经公开读回核对；实际匿名latest下载返回正确Content-Disposition文件名，APK完整下载SHA与原包一致。
- 当前更新相关12项JVM测试通过；最终已发布APK上2项模拟器检查通过：真实匿名Android客户端读取新的正式latest及精确正式APK链接，当前版本说明与检查无重复更新循环。真实HTTP结果与输入官方实时元数据的UI检查分开记录。首次宿主匿名API403限流保留，随后公开下载/API及模拟器实际匿名查询完成通过，没有用鉴权替代模拟器匿名结果。
- 证据目录 `evidence/desktop-072/canonical-naming/`，含改名前后元数据、资产改名、公开下载与校验、测试包指纹及模拟器结果。更新入口验收默认使用正式Tag/文件名；历史桥接解析测试保留用于已有版本比较。

仅支持三段数字旧编号的旧客户端无法解析新的正式latest，需要手动下载上述APK覆盖安装一次。包名和正式签名兼容，不需要卸载或清数据。该影响是用户明确停止旧编号策略后的边界，不能继续声称旧更新器当前仍能自动发现。未来发布遵循 [VERSIONING.md](../VERSIONING.md) 与AGENTS正式编号规则，不再生成旧编号兼容发行。

以下原双编号发行、旧更新器通过结果和附件命名均是此次改名前的历史验收快照。
