# 0.2.55 — 会话 Review 分组混合排序

2026-10-12，Asia/Shanghai。起点main / 9049bf3，已发布0.2.54/code63；开发分支fix/session-review-order-055。本轮只处理最新桌面对齐交接明确保留的P2局部排序（NEXT_TASKS索引P2-04），未处理动态字号、真机、生产字段或其他任务。原有AGENTS-original.md保持未跟踪且未修改。

正式产品提交/Tag为a1ba688203684cbf58103fd8027f22ac38f343ef / v0.2.55；已快进合入并推送main，沿用既有remote。[GitHub Release](https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.55)非草稿/非预发行，四个资产uploaded，草稿阶段全部下载回读一致，正式发布后APK再次下载哈希一致。上传CLI曾返回APK资产重名，先核对草稿四份资产内容后才发布；没有覆盖资产。最终回执见GITHUB_PUBLICATION.md，本轮已停止。

## 定位与最小修改

SessionListDisplay.grouped原先将Auto Review摘要追加在已排序的关系/Grok/普通会话列表末尾，因此最新Review仍落在旧会话后。上游固定3f106ae3d4712b26cced8272f0d488eaab0de1e6的groupSessionRows会在所有摘要加入后统一排序：时间降序，Token降序，费用降序，名称比较。

从GitHub读取该固定提交的sessionRows.js blob，并与本地参考核对一致：939c418a6cfd0aa24b943653bf12167fe126b670。独立生成器tools/capture-session-group-order-069.cjs直接调用原桌面函数，产出10组公开goldens，包含Review最新/中间/最旧、Token/费用/名称同时间比较、关系/Grok混排、无Review、仅Review及缺时间。复用旧goldens的账目、文案及成员断言，只有排序预期改用0.69完整分组函数，未降低断言。

产品只修改SessionListDisplay与SessionRelationshipGroups：复用原raw排序规则，所有摘要使用同一比较器，Review加入后参加最终排序。继续以BigInteger/BigDecimal比较数值，成员顺序、聚合、未定价Token、分组ID、分页、详情、数据解析和网络/SSE不变；没有新依赖或Hub合同变更。

## 验证与证据

首次参考目录缺少依赖，保留初始日志；改用同blob且包含依赖的既有参考目录，未修改上游。修复前5项SessionList单元中2项失败，新est Review次序与上游不符；真实会话页专项也失败，截图和对应Debug应用/测试APK双指纹已保留。修复后原值/成员断言和新的顺序goldens通过。

最终正式签名Release：36项会话相关单元通过；15项Android通过（SessionList3、Session0692、SessionSettings3、SessionBreakdownMotion6、HomeSession1），含最新Review在旧会话前、成员展开、分页/隐私/上下文和数字动效。没有将阶段复跑累计成独立测试数。Release Lint最终0 errors/93 warnings/1 hint，与0.2.54按ID/消息/文件比较无新增问题。

独立API36模拟器TokenMonitor_Release055/emulator-5588：安装已发布0.2.54，在其实际存储实现中写入独立合成加密历史和偏好；覆盖0.2.55后应用UID及两份文件SHA256保持，旧记录解密及English偏好读取成功，两阶段各1项检查通过。没有卸载或清数据；这验证独立合成store，不冒充所有真实用户设置/旧Debug迁移验收。正式包和测试包已安装base.apk哈希匹配，主Activity冷启动成功。

最初拟用Release053 AVD时发现已有另一实例，未抢占或清理其锁，改为新建独立AVD。原5556及手机未操作。修复前Debug专项在已有独立P109/emulator-5584运行；上述升级和最终专项均在新建5588运行。所有图片都是公开合成数据，无私有Hub响应或真实账户。

证据在本机evidence/session-review-order-055/：首失败日志/截图/双APK指纹、修复后排序与成员截图、Release单元/XML、15项UI原始结果、Lint及比较、签名/版本、两阶段升级文件指纹/UID、安装包核对和冷启动回执。目录不入Git。生产NAS/Hub/Agent/Lucky/路由/桌面客户端均未访问或修改，没有真机或公网验收。

## 版本与正式交付

版本0.2.55/code64，applicationId仍为dev.bing.tokenmonitor.nativeclient。沿用0.2.53/0.2.54正式证书，SHA256 e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202。

应用APK SHA256：88589cbf84318ca426e38897430da1d2d93c36d4236eeb18bc47ad40c21e07fd。测试APK SHA256：d003a74f9abcf2473462f18db7e980cc336e1d888de54923fe5d370315974f25。正式交付目录dist/release-0.2.55/，上传仅包括正式APK、SHA256SUMS、release-manifest和VALIDATION；不上传测试包、密钥、截图或私人配置。发布成功及回读校验见GITHUB_PUBLICATION.md，产品提交和Tag通过发布清单绑定。

新版真机、旧Debug迁移、生产新字段及长期性能仍未验证。下一项只建议从剩余待办独立选择；本轮到正式发布及下载校验后停止，不自动开发下一项。
