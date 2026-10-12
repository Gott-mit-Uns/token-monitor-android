# 第二阶段：Antigravity 双周期独立交接

2026-10-12。唯一产品目标为 Antigravity 单可见模型组的双周期选择和标签对齐；本轮到本地提交结束，不继续其它任务。

## 本地 Git 基线

基线 commit：`288b7dfcc8831b9ce5c4a8c50fb3be4b150ae66b`；带注释标签 `v0.2.51-dev`；开发分支 `fix/antigravity-dual-windows`。本地未配置 remote，没有 push，没有修改旧 GitHub Android 仓库。

提交前检查根目录和相关父目录无 Git；复核 .gitignore 并排除缓存、build、dist、evidence、work、日志、数据库、APK/大型归档、凭据及签名私钥。保留源码、资源、所有测试/合成输入、工具、root构建配置与wrapper、工程文档。680项基线文件指纹加清单自身，共681个文件；必要的约2MiB测试JSON保留。三个URL-userinfo扫描命中属于虚构fixture，没有真实凭据回显。未清理原文件。详见 [GIT_BASELINE_0.2.51](GIT_BASELINE_0.2.51.md)。

## 行为与最小修改

依据固定桌面commit `3f106ae3d4712b26cced8272f0d488eaab0de1e6` 的 `src/electron/renderer/limits/providerPresentation.js`，直接执行未修改的函数形成选择/标签参照；没有重做全量上游审计。

| 条件 | 修复前 | 修复后 |
|---|---|---|
| 一个可见模型组，有两个有效周期 | 每组选一个窗口，可能只剩5-hour或Weekly | 各取最紧session/weekly，按5-hour→Weekly排序；不复制原数据 |
| 只有一个周期 | 通用Session/Weekly标签 | 只显示真实存在且可用的周期，标签如Gemini 5-hour / Gemini Weekly |
| 同周期有重复读取 | 原单组选一个窗口 | 每kind取最小剩余，平手保留输入中第一项；不会多造卡片 |
| 隐藏其他模型后只剩一组 | 仍仅选一个周期 | 先隐藏过滤，再按单组双周期选取 |
| 缺重置时间 | 无周期前缀可能难区分 | 周期已包含在标签，仍能区分；不虚构重置时间 |
| 缺额度/空kind/空windows | 缺额度已有不可用语义，空kind可能进入显示 | 缺额度不变成零；Antigravity空kind不作为周期显示；空provider的状态占位仍保留在parser，非额度窗口 |
| 多可见模型组 | 取各组代表窗口及原紧急程度/顺序 | 保持原选择规则与模型标签，必要的周期信息仍在重置行 |
| 其他Provider | 原规则 | 保持；原有source-backed fixture及Codex隐藏additional检查通过 |

单组标签包含模型和 `5-hour` / `Weekly`，重置行不再重复周期；多组标签仍只有模型名，重置行继续携带周期。额度数值、重置时间、used/left模式分别引用原窗口，parser/Hub数据/缓存格式没有修改。

产品文件只有 `LimitDisplay.kt` 和 `HomeQuotaLayout.kt`。没有修改HomeQuotaRow视觉组件、字号、动效、依赖、架构或采集/网络行为。Antigravity缺失/空kind的准入采用Android保守规则，不宣称桌面对畸形行的准入完全相同。

## 实际验证

| 验证 | 结果与范围 |
|---|---|
| 受影响单元 | 15项通过：HomeQuotaLayoutTest 3、LimitDisplayTest 6、LimitTest 2、LimitProviderLayoutTest 4；0失败/错误/跳过 |
| 新逻辑场景 | 15组合成输入，used/left两种模式：单session、单weekly、双周期、反序、重复/平手、缺reset、缺/空reading、used-only、缺/空kind、缺label、空windows、多group；另含hidden先过滤与其他provider保护 |
| 编译 | 应用及普通Android测试工程编译/组装通过；最终UI测试宿主修改后再次编译通过 |
| Lint | 0错误、97警告、1提示；按issue id/message/file与原diagnostic97警告基线比较，新增0项；不拿封包93警告摘要作同一来源 |
| 既有Compose交互 | 两项原HomeQuotaScreenTest方法分别在浅/深执行，合计4次通过；应用APK相同，属于前一候选测试包，不写成最终测试包一次全套5项通过 |
| 新Compose窗口 | 最终测试包1项通过，内部8组：浅/深×fontScale1/2（大字叠加largest应用字号）×文字/条形；360dp组件宽度、自有API36模拟器真实Compose Activity |
| 文字/布局 | 逐行字形边界、无ellipsis、无高度裁切、标签/数值/重置在窗口内、周期窗口不重叠、单张provider卡片；复用项目原额度测试的glyph-bound合同 |
| 截图 | 8份公开fixture截图，主题背景由Surface正确绘制；在布局断言和绘制同步后抓取。截图不含真实Hub、标题或凭据 |
| 交付保护 | 原dist APK SHA仍为`ef830691261dcd38d322eafd4e286068856871ea52e1347efbfd4b2123a4d805`；没有覆盖历史交付，没有清除模拟器应用数据 |

首轮失败记录全部保留：build-unit-lint-1为新测试的可空key类型；第2轮为空kind边界，第3轮为测试把空provider状态占位当成quota window。分别修正类型/Antigravity准入/测试输入映射，没有移除case或把缺失值改为零。

首轮UI将paragraph可用宽度与wrap-content文字宽度比较，误报溢出；诊断证明paragraphWidth415px、文字size128px、字形right128px、高度无溢出。随后采用既有HomeQuotaEntryScreenTest同样的逐行字形边界断言，不忽略裁切/省略/重叠。第一次矩阵截图宿主漏画主题背景并且抓图早于新画面，已补Surface并移到断言/绘制完成后抓图；旧截图另存`first-capture-host-diagnostic`。

证据目录：`evidence/antigravity-dual-windows/`，含unit XML、编译与Lint日志、所有首次失败、最终UI日志/截图、reference receipt、最终测试及源码指纹。早期UI失败日志保留，但早期测试APK未独立冻结指纹，不能完整回链；最终验证记录绑定实际应用/测试APK与源码指纹。汇总及最终commit SHA见该目录`validation-summary.json`、`git-delivery.json`。

## 开发验证 APK 与未验证边界

生成了编译/模拟器验证用Debug应用及测试APK，保存为 `evidence/antigravity-dual-windows/antigravity-dual-windows-validation-app.apk` 和 `antigravity-dual-windows-validation-test.apk`。应用SHA256为`8eb8d79be4754db62e2c32889421f0d5329ad8d02a89a270c62cf6d7f29da161`。versionName仍0.2.51-dev、code60；不是新正式Release，原dist归档仍对应修复前交付内容。

没有重跑385单元/252普通Android/18组全局矩阵/升级全套；没有新正式版本号或GitHub上传。实体手机、大字体系统配置、实际Hub读数和公网Lucky仍未验证，本轮大字矩阵是LocalDensity合成fontScale的Compose检查，不冒充真机系统字号。LAN桥接间隔异常、LiveHttpsEntryTest和SSE网络策略仍保留原任务状态。

建议下一个独立任务为P1-02：明确已验证LAN HTTP优先与安全回退的连接策略；开始前单独确定验收状态机。本轮不执行。
