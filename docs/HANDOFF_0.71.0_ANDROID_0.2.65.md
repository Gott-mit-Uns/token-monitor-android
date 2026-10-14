# v0.71.0 / Android 0.2.65 候选交接

2026-10-15 Asia/Shanghai。固定上游 `a514e206`，Android从 `77f2ff9` 开发独立分支。版本 `0.71.0-android.0.2.65` / code74，原包名及正式签名。正式Release/tag/手机安装等待用户确认；代码验证后自动推送及合并。改动与协议边界见 [差异与NAS协调需求](DESKTOP_071_DIFF.md)。

本轮实现 Goose标识与goose-db健康、累计用量/调用未知；Notion双窗口、多hash账户、未知reset/不可用及非money语义；WorkBuddy独立身份、失败精确绑定与保守合计。新字段可选，旧Hub缺字段继续可用。上游真实Notion登录/资格仍未证实。Pi/CodeBuddy远端聊天接口仍缺失，仅回归已有摘要刷新状态，不伪造聊天或加入本地解析器。

保持Widget删除、关于四入口紧凑布局、设备双列、HTTP/HTTPS同屏和真实端点、主题字体动画趋势与网络恢复。未合入R8/瘦身、未改NAS或第三方凭据。

Evidence is in `evidence/desktop-071/`; final candidate acceptance and retained first failures are recorded below. Added tests and compilation alone are never acceptance evidence.

## 完成验收与候选

候选 `dist/candidate-0.71.0-android.0.2.65/token-monitor-android-0.71.0-android.0.2.65-candidate.apk`，15,239,574 bytes / 14.53 MiB；正式非debuggable，minSDK26/targetSDK37，包名 `dev.bing.tokenmonitor.nativeclient`。SHA-256：

`039825b43a713a70d123abc4e27d7311ff4a01418bed35404c3d17b43186bfa4`

正式证书 SHA-256 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`，与重新下载并校验的正式0.2.64/73相同。产品提交 `6cf7a0c`；后续只改测试与文档，产品源码和build.gradle不变。最终测试源码 `6f149ff`，最终测试APK SHA-256 `4003fa5fc7e1b656ce34c587e84db5286a5a51c724752948b261288e33d1511e`。APP设备端SHA读回等于候选；来源按外部manifest/Git源树关联，不声明APK已内嵌正确Git提交。

所有设备门槛在本轮独立 **API36/ARM64 emulator-5660**，使用公开合成fixtures；不是真机、NAS或公网Hub验收。

| 门槛 | 实际结果 | 证据 `evidence/desktop-071/` |
| --- | --- | --- |
| Release单元回归 | 464 tests，0失败/错误/跳过；含9项0.71增量合同 | build-final.log、unit-final.xml |
| 最终Lint | 0 Error/Fatal，101 Warning、1 Hint；最终测试包装修改后重新检查成功 | lint-final-test-source.log、app/build/reports/lint-results-release.xml |
| 主页/趋势/额度/会话/导航 | 13/13；6类多场景测试，含旧额度/隐私/模型/上下文/列表与主题字号 | regression-pages.json/log |
| 设置/设备/Widget删除/更新/语言 | 22/22；6类，紧凑About四入口、HTTP/HTTPS并列与实际端点、两列设备、真实PackageManager无Widget、语言真实重建 | regression-settings.json/log |
| 缓存/网络/恢复/TLS/真实Activity | 22/22；旧新加密缓存、端点、三次受限恢复、单SSE、真实Home/Back/重建/刷新生命周期 | cache-network-wifi-final.json/log |
| 新provider与摘要刷新 | 4/4；Notion双窗口/reset未知、账户展开与滚动保留、Pi/CodeBuddy已有摘要Dialog保留并更新、WorkBuddy兼容合计及失效撤销、cache/freshness/旧Hub | providers-071-theme-final.json/log |
| 新额度窄窗口大字 | 320dp、系统font_scale2.0；一方法含浅/深两状态，通过且原scroll/expanded断言保留；组件滚动视口400dp，不裁字体 | notion-320-font2-theme-final.json/log、最终PNG |
| 0.2.64→候选正式签名替换 | seed与替换后两阶段各1/1；UID、加密Hub/历史/四类字体及显示偏好完整保留；最终测试包又1/1重验解密/字节不重写 | upgrade-seed-064、signed-upgrade-064-071、signed-upgrade-final-test-restore |
| 实际冷进程启动 | force-stop后COLD/Status ok，MainActivity启动成功 | cold-launch-final.txt |
| 资源/包身份 | 670组中英文主资源匹配，版本/code/签名/非debuggable/设备SHA一致 | language resources check、package-identity.json、installed-apk.json |

同一产品APK贯穿所有设备测试。原回归使用5e6测试包，新场景最终使用4003测试包；初次新场景/升级使用0994。每轮精确pair在其JSON/log中，不能把旧测试包结果写成4003全套重跑。

截图均真实采集、原字节复制，无编辑。来源/校验见 [provenance](images/providers-071/provenance.json)。[实际额度页及展示合计](images/providers-071/provider071-workbuddy-total.png)；[Notion浅色组件](images/providers-071/provider071-light.png)、[深色组件](images/providers-071/provider071-dark.png)、[320dp/字号2浅色](images/providers-071/provider071-320-font2-light.png)、[深色](images/providers-071/provider071-320-font2-dark.png)；[已有会话摘要](images/providers-071/provider071-session-summary.png)。组件截图是400dp视口测试包装；系统栏属于测试宿主窗口，不作为产品深色系统栏验证。大字展开内容自然滚动，截图不代表完整内容均在同一屏。

## 首次失败和修复边界

- 初次单元455项有5项旧目录/registry/泛邮箱匹配期望失败，原log与XML保留；第二轮464项有2项未更新ordering oracle及泛邮箱匹配失败。使用固定0.71原版providerOrder纯函数重采集ordering；WorkBuddy精确键与歧义邮箱规则只作用WorkBuddy，其他provider保留原绑定行为。最终464项全部通过，不删旧断言。
- 并行外部构建造成16GiB主机严重内存/交换压力；第二轮单位已完成后，Lint运行异常，停止本任务自己的daemon，以一worker/1536MiB heap重新执行，不动其他任务、不clean。最终Build/Lint成功。
- 新账户滚动测试普通窗口内第二卡片仍可见、scroll=0，正向scroll断言失败；改成真实可滚动400dp测试视口，原expanded/精确scroll保持断言保留，4/4重测成功。随后视觉复核发现组件测试没有背景和safe drawing包装，修复的只是测试包装，原图与重取图独立保留；没有改产品主题或像素。
- 早期模拟器System UI在内存压力下留有ANR弹窗，截图原样保存；实际点击Wait后恢复，再取最终图。不是Token Monitor ANR证据，不把有覆盖弹窗的图当视觉通过。
- 网络整组22项初次21通过/1失败，原Activity单独重跑仍在第一SSE等待失败。实际ConnectivityManager默认CELLULAR，因此LAN HTTP正确被安全策略拒绝。仅连接模拟器内虚拟AndroidWifi，默认WIFI后原22项全部通过；不改产品策略、超时或断言，不重启NAS/路由。现场证据与失败log保留。

## 剩余差距、版本与发布边界

- Goose仍遵循上游累计归属创建日的局限；继续会话跨天无法表达真实每日调用。不能用Android重分配。无真实调用数量，显示未知；缓存输入不重复加，reasoning不推算叠加，费用沿用Hub提供的估算。
- Notion只有fixture/schema展示验证；真实登录、资格和内部API响应未验证，未知shape/status为不可用。不同用户/工作区仅消费采集端hash，原ID/名称/Cookie不进入原生显示模型或截图。结构化fixtures/指纹/数值计数按原始类型精确核验，避免Markdown转换改变数字、null或字段边界。
- Pi/CodeBuddy聊天正文远端协议仍不存在；本轮只回归原有摘要，无聊天阅读、Android本地collector或解析体系。
- 已向现有聊天“对齐 NAS 项目至 v0.71.0”发送最小字段/隐私/缺口协调说明。当前生产NAS和新版采集链未在Android本轮验证或修改。
- 新versionName为上游与Android组合；更新解析器同时支持纯Android及组合标签，比较Android序列。0.2.64等已安装旧解析器无法识别新的组合Tag，正式发布时须选兼容渠道/命名或明确手动APK升级；本轮未创建Tag/Release，候选自身发行说明Tag链接也尚不存在。
- 本轮手机未安装，实体手机覆盖升级、真实LAN/公网HTTPS/蜂窝恢复、OEM后台锁屏与性能未验收。API26/31等额外平台矩阵未重新执行。
- 代码推送/合并完成后更新下方Git回执。正式Release、正式Tag及手机安装均等待用户确认；不将候选声称为已发布或全部对齐。

## Git delivery receipt (2026-10-15 Asia/Shanghai)

[PR #3](https://github.com/Gott-mit-Uns/token-monitor-android/pull/3) is merged into GitHub main. Merge commit: `5680a4aeb4a6f56bc78b0bbd235a7dcba1d0f17e`, validated head `46a84d580636eb9bba7893def723345e1842601d`. This follow-up changes only delivery documentation. The source checkout's original uncommitted NEXT_TASKS and untracked AGENTS-original/REA audit files remain protected; it was not reset or overwritten.

Latest formal Release was rechecked as v0.2.64. No new release/tag, phone installation, NAS operation or third-party credential action occurred. APK SHA remains `039825b43a713a70d123abc4e27d7311ff4a01418bed35404c3d17b43186bfa4`; byte content is unchanged from all candidate device checks. Product source remains `6cf7a0c`; final native test source is `6f149ff`.
