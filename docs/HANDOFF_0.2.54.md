# 0.2.54 — 大字体修复与并行任务整合

2026-10-12，Asia/Shanghai。用户授权修复真机P1-10、发布GitHub并合并另一任务。正式交付信息与合并提交将在发布验证后补齐。

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
