# 0.2.60 联合候选增补：删除主页周期日期说明

用户已确认侧任务完成并要求与主任务一起发布0.2.60/69。下文为侧任务交付时证据；主任务整合、补验和最终正式APK以 HANDOFF_0.2.60.md 与Release四附件为准。

2026-10-13，用户在侧对话明确要求删除总 Token 下的“时间范围 · 按设备本地日期汇总”一行。本次只做局部源码及定向验收，不接续主任务的提交、版本、Tag、推送或 Release 操作。

## 改动

- 删除 `FixedPeriodStatus` 中的日期说明，以及中英文对应资源。7 天、本周、30 天共用该显示路径。
- 成功得到完整周期数据时不再创建状态列表项，避免留下空项和额外间距。
- 主趋势页面只为真正的数据缺失提示计算状态高度与间距，避免沿用已消失的提示高度。
- 保留周期汇总算法、缺失历史提示、手动读取按钮、设备详情中的来源窗口和图表日期轴。没有修改网络、缓存、签名或版本配置。
- 复用 `FixedPeriodScreenTest`，保留原有数据、费用、菜单键盘操作、周期偏好及历史恢复断言；补充日期行不存在、固定总量区域和首模块位置一致的断言。几何检查使用减少动效的公开 fixture，不据此宣称运行性能提升。

## 本次新执行的验收

| 范围 | 环境与结果 |
|---|---|
| 周期算法 | Release `FixedPeriodsTest`，4 passed，0 failure/error/skip |
| 周期菜单、页面与缺失历史 | 新建 API36 自有模拟器 `emulator-5594`，411dp/系统 1.0/App standard、320dp/系统 2.0/App largest 的浅深主题，2 项各执行 4 次，8 passed。最大 App 字号用于成功数据场景；缺失历史场景使用默认 App 字号及实际系统字号 |
| 受影响趋势及导航 | `TrendsMainScreenTest` 3 项和 `TrendsNavigationScreenTest` 2 项，5 passed |
| 构建及签名 | 现有 JDK17、SDK37、Gradle wrapper，Release 与 ReleaseAndroidTest 构建通过；应用和测试包均验证为既有正式证书 |
| 首次复现 | 在保留的旧候选应用包上运行新增断言，明确因 `fixed-period-range` 仍存在失败；首次截图、输出和应用/测试 APK SHA256 留存 |

构建源基线：`79677fb254ed8b51e345e8a859046233d8cfd0f7` 加本次尚未提交的局部差异；完整源码指纹见 `work/side-home-period/source-receipt.json`。验证副本与主工作区逐文件核对一致，不混入额外未提交修改。

应用 SHA256：`824266122f7dbf348dc69034bf14ddb09d59ad92769e72e5bafd111fb7a86385`；测试包 SHA256：`645743fac77f6620da004a974654250347916bf2b462cec4c7abb606ec0d3e85`。应用及测试证书 SHA256：`e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`。

证据：`work/side-home-period/build.log`、`build-summary.json`、`validation-summary.json`、`results/first-red/`、四个 `results/after-*/` 窗口主题目录及 `results/after-trends/`。每轮保存应用/测试指纹、实际窗口/系统字号、原始测试输出与截图。截图为合成公开数据，不是真机。

## 联合发布接续边界

本次修改已加入联合候选工作区，尚未创建 Commit，也未发布 APK。主任务最终提交需包含本文及本次源码/资源/测试差异；必须从包含该变化的最终提交重建正式包，并重新执行适用的最终包验收、同版本/签名/Release 字体包体差额与发布资产回读，不能沿用此次变化前的 APK 指纹作为交付结果。

本次未执行实体手机、蜂窝切回 Wi-Fi、真实生产网络或真机性能测量。原 0.2.59 真机自动恢复失败结论保持；本次 UI 调整不提供改写该结论的依据。原有验收模拟器 5556/5592 和生产服务均未操作，仅停止本次自建的 5594。
