# 0.2.54 — 大字体修复与并行任务整合

2026-10-12，Asia/Shanghai。用户授权修复真机P1-10、发布GitHub并合并另一任务。正式候选0.2.54/code63，发布回执见GITHUB_PUBLICATION.md。

## 最终整合与交付验证

大字体提交2070edf950a6d6e2e836a15fbfbd96ddfacf1812；另一任务P1-09提交55d682f6f6aa448535ebb57a7e636ecc7026582f，完整合并f517232。P1-09只改测试/验证工具和文档，按脱敏身份区分同周期重试与新目标读取，没有改生产网络策略。两轮各75秒NAS只读观察保留于该任务交接，不等同新版手机/公网验收；旧r10仍无法追溯。

整合通过：12项请求身份工具、7项桥接、42项Release单元、8项Release界面回归、7项历史页面及4项浅深/系统0.9和2.0字体矩阵。最后恢复常规字号期间按钮原高度，大字体自动增高；最终APK再通过4项数字动效和4项字体检查。阶段复跑不累计为独立测试数。

最终APK SHA256：65d629702762298b6cf56842e61fd252069a5c386f754b26028de1090bfdb9f5。测试APK SHA256：c5735cce366d60a309cd164951f8d085a7f058ee2a690b995fbc6ffb07af0892。初始候选31cfa2c6阶段日志保留，不将所有阶段测试声明为同一APK。Release Lint为0 errors/93 warnings/1 hint，与0.2.53原报告按ID/消息/文件比较，无新增问题。

正式证书SHA256：e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202，与0.2.53一致。独立Release053/API36模拟器从0.2.53/code62覆盖至0.2.54/code63成功，appId10213保持，最终安装base.apk哈希匹配交付，实际启动成功；不能据UID保持声称所有偏好/加密缓存完整验证。临时字号恢复1.0，原5556字号/密度恢复。手机未被ADB识别，本轮没有操作手机。

文件在dist/release-0.2.54/：正式APK、SHA256SUMS、release-manifest和VALIDATION。签名配置在仓库外，不上传测试APK、截图或私人数据；统一递增一次，不覆盖旧交付。下一项建议重连手机检验0.2.53→0.2.54覆盖、设置保留和0.9/2.0布局。P2动态字号刷新、蜂窝/Lucky、真机SSE待独立验收，本轮停止。

## 修复范围

起点18e0c0b，包含正式0.2.53、首次真机报告及P1-09首轮22项测试。独立工作区/分支fix/large-font-054，不覆盖原工作区AGENTS-original.md或另一个任务的未提交修改。

- DesktopHubScreen：总量按真实测量宽度决定是否将近似值放到下一行，保留显示字号和所有精确数字，超长数字允许完整换行；期间选择大字体独立一行并取消22dp固定高度。
- DesktopPeriodIndicatorMotionUi：选择层随实际期间文本高度绘制，动画索引和生命周期未变。
- HomeUsageUi：首页工具/模型大字指标另起行；数值/百分比预留实测字形宽度和取整余量，百分比保持完整单行。
- HomeLargeFontScreenTest：合成199,470,859及≈1.99亿/81%，验证真实系统fontScale、完整TextLayoutResult、单行百分比及零Hub读取。

不修改数据解析、统计、网络、SSE、本地采集、服务或新增依赖。运行中系统字号刷新P2仍未证实根因，不在本轮调整语言/配置生命周期。

## 开发阶段证据

证据目录evidence/large-font-054（忽略于Git，保留本机），首次环境构建因独立工作区缺SDK定位失败，补充被忽略local.properties后成功；没有clean。首次修复前UI失败：hub-total-tokens.hasVisualOverflow，APK指纹before-apks.sha256。中间断言暴露自然文本宽度取整问题，日志均保留；没有降低断言。

最终Debug：20项定向单元测试（HeadlineNumberMotion8、DesktopPeriodIndicatorMotion3、HomeSession4、MainDisplay5）通过；浅/深色×系统0.9/2.0共4项真实系统字体矩阵通过，375dp/font2浅色额外1项；既有数字/初始动效/额度/导航15项回归通过。Lint0 errors/93 warnings/1 hint，既有警告不等于新增问题。正式合并后的Release另行验证，下节将记录最终结果。

手机重连请求已由用户确认，但当前adb只识别模拟器，无法执行新版手机覆盖安装；不声称其通过。正式签名、旧版同证书升级和安装状态须分别留证。原0.2.53真机失败见PHONE_ACCEPTANCE_0.2.53.md。
