# Android 对齐桌面 v0.72.0

固定日期：2026-10-16（Asia/Shanghai）。本轮从远端 Android main `b9a8de1` 开始；正式产品基线为 `5994b51b1a528ebe7de60ba3bd3163521bcb597d` / Android `0.71.0-02` / code75，正式 v0.71.0-02 与 latest 兼容 v0.2.66 的 APK 相同。旧主工作区未提交文档保留，本轮独立 `feat/desktop-072-alignment`。

## 目标锁定

使用官方 GitHub 实时 API `/repos/Javis603/token-monitor/releases/latest`，并独立解析 `/commits/v0.72.0`。稳定目标 [v0.72.0](https://github.com/Javis603/token-monitor/releases/tag/v0.72.0)，commit `126e9a43dcc51d3d7b2609231eac9974ba73bed7`，发布时间 `2026-10-15T19:00:09Z`（北京时间 2026-10-16 03:00:09），draft=false、prerelease=false。范围固定，不跟随 main 或后续版本。

实际增量起点是 [v0.71.0 a514e206](https://github.com/Javis603/token-monitor/commit/a514e2063563e318ef73c2a17fad68057c4e300c)，不是已在上一版 UI/更新查询测试中出现的 0.72。桌面 UI 参考版本、Android 实现范围、生产端数据能力分别记录；`fullyAligned=false` 继续保留，不能据版本字段宣称全平台功能完整对等。

[官方 compare](https://github.com/Javis603/token-monitor/compare/v0.71.0...v0.72.0) 共6个提交。源码、字段、验收映射如下；详细定义及测试边界见三个专项文档。

| 上游提交 / 项目 | Android 必须实现或回归 | 代码位置 | 数据依赖 / 验收 |
|---|---|---|---|
| [631c25c #1053](https://github.com/Javis603/token-monitor/commit/631c25cb4947bd7a75aebf5997bbeadd33cc9c98) 分组指标 | 对去重账目求调用数、缓存分子分母与毫秒计时速度；取最大最近活动；展开保留自身数据；未知不作零 | SessionDisplay / SessionFamilyMetrics / SessionListDisplay / SessionRelationshipGroups / SessionListUi | Hub已有messageCount、input/cache/output、timed*，缺失保守不可用。SessionMetrics072Test/ScreenTest、原Session系列回归；[专项](ALIGN_072_SESSIONS.md) |
| 同 #1053 委托角色 | continuation/native关系沿用有效外部parent，内部和冲突证据不误标 | SessionRelationshipGroups.delegatedRole | 可选parent/native/thread字段；嵌套、续接、冲突测试 |
| [ddc4577 #1056](https://github.com/Javis603/token-monitor/commit/ddc457718f7b09207fe5292cd4e4b37833ec4513) Cursor X Subscription | Included usage周期与真实百分比、预付USD金额各自显示；余额无百分比、不与历史费用相加；旧套餐/多账号/过期保留 | LimitDisplay / LimitProviderLayout / HomeQuotaLayout | Agent负责planInfo/unified/get-user-prepaid-billing及cents→USD，手机只消费windows。Cursor072Test/ScreenTest；[专项](ALIGN_072_CURSOR.md) |
| [e38c12c #1060](https://github.com/Javis603/token-monitor/commit/e38c12c908f3271f04d5c0c506f3f3bd33c9b14c) OpenCode 2.x | 回归既有opencode来源、usage、时间、分组/去重；不移植SQLite | HubJsonProjection / HubParser / SessionDisplay / SessionListDisplay | 本地session_v2/session_message恢复属采集端，远端逐轮正文API未新增；NAS源代码生成新旧wire fixture验证Android链路。不是实际采集端到端；[专项](ALIGN_072_UPDATE_OPENCODE.md) |
| [bf2045b #1065](https://github.com/Javis603/token-monitor/commit/bf2045b27d056c07a5456fbc3c29fcd0234acbe0) 更新 | Android仅前台启动/恢复节流查询、共享About状态、保留手动按钮及精确版本说明；桌面最新版查询独立 | AndroidUpdate / AndroidUpdateModel / AndroidUpdateUi / MainActivity | 成功24h、失败1h；后台取消/乱序隔离，无定时唤醒/弹窗；AndroidUpdateTest及更新UI/真实GitHub查询 |
| [d21b087 #1059](https://github.com/Javis603/token-monitor/commit/d21b08758053aaae77c4b372b72c96303400004a) Windows Edge Dock | 排除 | 无 | Windows WM_MOUSEACTIVATE/非激活窗口，Android没有对应窗口消息链 |
| #1065 桌面下载/重启、悬停卡及15s/2h/解锁后台检查 | 按平台排除相应桌面机制，保留说明/下载入口语义 | AndroidUpdateUi | APK安装交系统；手机无Electron热更新/鼠标悬停，禁止因此添加后台任务或下载器 |
| [126e9a4 发布提交](https://github.com/Javis603/token-monitor/commit/126e9a43dcc51d3d7b2609231eac9974ba73bed7) | Android独立正式版本和code按验证后提升 | app/build.gradle.kts / DesktopParityMetadata / VERSIONING | 不搬package-lock/worker版本，不只改版本号 |

## 保留项及兼容边界

关于入口顺序与当前精确版本说明、桌面最新版查询、字体控件对齐、页内设置、只读模型价格、额度比例与间距、订阅去重、真实手机/Hub/采集卡、Xiaomi 13 Ultra硬件型号映射、趋势日历与月份网格均保留。设备名、Hub名和在线状态来自真实字段；没有友好名称时继续诚实回退。Widget不恢复、R8不推进。

Hub缓存/历史/筛选/时区计算及连接隔离未重构。完整Release单元回归涵盖旧新Hub、full/freshness、原加密Store、history coordinator、断网重连、请求revision与binding、账号/设备切换、日/月一致性。界面矩阵及签名覆盖升级需由本轮实际包另行执行，不能从旧版487项等历史数字冒认本轮通过。

## 当前 Hub 证据

配套 NAS GitHub latest 已为 `v0.72.0-01` / `8444a9b1f3aeb88e164fd20b9773a9984d3d33a0`（源代码发布，不等同部署）。默认 DXP4800 的匿名 `/api/health` 当前返回 core84 / node-hub runtime6、4设备；这是构建字段，不等于软件发行版本、友好名称或所有provider能力证明。

通过现有安全配置完成一次只读鉴权stats字段存在性检查，只保存字段名及计数；该样本没有 Cursor provider记录，因此无法验收真实统一套餐/预付余额。未保存任何私有响应、账号值或凭据。宿主读取不代表Android实际联网。本轮不改动/部署NAS、Agent或桌面。旧Hub缺新数据仍可用，手机不增加认证，不伪造不可获得的指标或会话正文。

## 验证与后续基线

构建、首次失败、最终APK/测试APK指纹、界面截图、升级和发布读回统一记录于 [本轮交接](HANDOFF_0.72.0-01.md)。通过的自动化范围与未验证的真机/实网/真实套餐/采集端应明确分列。下一次对齐从本文件固定的v0.72.0 commit开始比较；不要重新扫描所有历史版本，也不要把保留依赖当作已实现全平台能力。
