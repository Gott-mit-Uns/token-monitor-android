# v0.72 Cursor The X Subscription 对齐

固定桌面目标：v0.72.0 `126e9a43dcc51d3d7b2609231eac9974ba73bed7`。上游变更 [#1056](https://github.com/Javis603/token-monitor/pull/1056)，合并提交 `ddc457718f7b09207fe5292cd4e4b37833ec4513`。2026-10-16 通过 GitHub API 定向读取该固定目标的 `src/shared/providers/cursor/limits.js`，及 PR 发布说明。

## 逐项合同及 Android 处理

| 上游规则 | Android 位置 / 实现 | 数据依赖与验收 |
|---|---|---|
| `planInfo.isMerged` 判断统一套餐；planInfo 不可用才用 sand `isUnified`；统一 Ultra 显示 Ultra | 采集判定属 Agent；Android 只读 `planLabel`、账号身份、windows，不推断套餐 | Hub 必须提供已映射的套餐/窗口；不把旧 `Cursor Models` / `Other Models` 自动合并 |
| 统一套餐共享 Included usage，`usedPercent` 来自 totalPercentUsed，缺失时窗口省略；显式零是真实零 | 既有 HubParser 支持 usedPercent，LimitDisplay 计算 `100-usedPercent`；Home 保留 Included usage 标签 | Cursor072Test 覆盖缺失、零、25% used / 75% left。无有效百分比为 `—` / Home 无此窗口，不补成 0 |
| Included 默认 weekly / 10080 分钟，只有明确 monthly 才 billing；weekly 缺重置时间不能借用账期结束 | LimitProviderLayout、HomeQuotaLayout 对未知重置显示“重置时间未知 / Reset time unknown” | 直接使用 Hub resetsAt，不根据手机日期或套餐估算 |
| 预付 availableCents / 100 转 USD；正余额为独立 billing `metric=credits`, remaining, showMeter=false；零/缺失采集端省略 | 修复完整额度卡之前显示裸数；LimitDisplay 输出真实 currency 金额。Cursor 余额无分母，不用余额+历史月费用推算百分比；完整/Home 均不显示虚假余额百分比 | Cursor072Test 覆盖金额、未知currency、恶意/异常usedPercent不制造余额百分比。上游省略余额不能推定手机读取到了零 |
| Unified 不显示 legacy on-demand / 独立 Grok Bot / 双模型池；legacy 不变；team pool仍由源数据决定 | Android 不作认证或采集判定，按 Hub windows 逐项显示，credits 与 spend 保持独立 | 旧模型池 + On-demand spend 回归；不会把 spend 加到 prepaid 或 Token 历史成本 |
| 每账号独立 accountKey / accountEmail | 既有 recordIndex / accountKey分组和去重名称；Cursor stale 的 Home 套餐行额外标明“数据过期 / Stale” | 合成多账号、过期meta、JSON冷恢复、freshness不覆盖windows、full replacement清除旧账号回归 |

## NAS 依赖与部署边界

只读核实配套 NAS Release `v0.72.0-01` 源码 commit `8444a9b1f3aeb88e164fd20b9773a9984d3d33a0`：

- `app/src/shared/providers/cursor/probe.js` 已有 get-plan-info 和 unified-only get-user-prepaid-billing；availableCents按整数/100转换USD，totalPercentUsed保持未知与显式零的区别。
- `app/src/shared/providers/cursor/limits.js` 已有 Included usage、weekly/monthly、prepaid、unified禁止legacy spend等规则。
- `app/src/shared/limits/core.js` 的 normalizeLimitWindow 保留 kind、label、remaining、usedPercent、currency、windowMinutes及账号identity；`app/src/shared/usage.js` 从规范化device limits构建 aggregateLimits。
- 以上仅证明该 NAS Release 源码能提供此合同，不证明用户 NAS 当前部署了该版本，也不证明真实统一套餐响应可用。Android 未改 NAS、未部署、未获取桌面Cookie/密钥、未新增手机认证流程。
- 旧 Hub 无新字段继续展示真实旧窗口；完整额度卡在空windows时显示“额度数据未提供 / Allowance unavailable”，Home不制造窗口。有余额但缺Included时，仅显示源提供的余额；手机不能据此判定用户必有未返回的套餐，不臆造新套餐。失败状态由既有账号状态展示；过期快照保留时间及stale标记。

## 验证边界

新增 `Cursor072Test`：6 项定向单元用例；`Cursor072ScreenTest.includedAndPrepaidRemainDistinctAcrossThemesAndLargeFonts`：Home/完整额度卡 × 深浅色 × 1/2 倍字体，关键文字无截断、预付无meter，输出8张截图。执行结果由统一发布交接记录（本文件不在运行前宣称通过）。

数据为模拟 Hub fixtures，非真实Cursor账户。缓存用例覆盖完整JSON冷解析与SSE freshness/full replacement投影，真实加密Store/乱序请求/断网重连/连接切换由仓库既有网络和Store集成测试统一执行，不将本文件6项当作所有链路通过。真实统一套餐、team seats、真实Hub输出新Cursor窗口以及实体手机均需独立证据；上游PR本身也明确没有真实统一套餐验证。

首次统一 build2 的 `Cursor072Test` 为 5 通过 / 1 失败，失败为 `accountsAndStaleStateSurviveCacheRoundTripAndFreshnessCannotReplaceWindows` 的 Home stale 标记断言。定向读取该轮测试 XML 与实际编译 class 的行号表/字节码发现：测试 class 已包含 stale 断言，而产品 HomeQuotaLayout class 仍直接传入旧 plan，没有当前源码中的 freshnessPlan 分支；这是开发期间编译输入时点不一致，不能据此宣布当前代码通过。保持断言，须由统一重建复测确认，首次失败证据保留在发布证据中。
