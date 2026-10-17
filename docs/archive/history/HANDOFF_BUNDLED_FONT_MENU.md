# 内置字体菜单补充 — 纳入本次联合更新

2026-10-13 用户在本次四项功能更新期间追加授权：“菜单里列出来，然后推送到 GitHub，在这次更新中一起更新一下”。此项补充同一个联合版本，不单独递增版本号、不创建独立 Tag 或 Release。

## 变更

界面字体与数字字体两处菜单新增 `Inter + Noto Sans SC` 与 `JetBrains Mono`。前者选择已有 Token UI 中英文字体组合，后者选择已有 Token Data；不新增字体文件或下载。保留应用默认、系统字体、原系统等宽字体、自定义和数字字体跟随界面选项，不重写旧偏好。菜单选中项按保存值准确识别，新值走既有 DisplayChange.Font 和偏好存储。

## 本侧任务验证

- 独立工作区 `work/token-monitor-font-menu`，分支 `fix/bundled-font-menu`，基于已发布0.2.60后的文档提交3731975；没有提交主任务其他未完成功能。
- Release定向单元：BundledFontMenuTest 1项、DesktopAppearanceTest 4项，5项通过，0失败/错误/跳过。
- Release AndroidTest Kotlin 编译通过。新增 BundledFontMenuScreenTest 覆盖两处菜单、两种字体选择、选中名称、底层FontFamily与截图保存；**本侧任务未执行设备测试，不能把编译称为UI验收通过**。
- 首次任务选择错误和测试调用签名编译失败日志保留于独立工作区 evidence/bundled-font-menu/，修正后的 build-check-run2.log 保留。没有修改测试断言取得通过。
- 本侧任务未安装APK、未访问Hub、未操作主任务模拟器、未进行真机验收或正式签名升级验收。

## 联合版本接续门槛

相同源码、资源与测试将加入主任务工作区 `work/token-monitor-four-features`；请以实际 diff 为准，避免重复 cherry-pick。GitHub字体分支只交付可追溯源码，**不是正式发布**。

主任务应把此追加范围写入联合发布说明与NEXT_TASKS，运行新增 BundledFontMenuScreenTest（浅/深主题与大字体，保存实际截图），必要时沿用Appearance/FontFallback/偏好存储受影响验收，然后将字体菜单与四项原功能合成同一个正式版本，沿用既定签名/升级/四附件下载回读流程。无独立版本号、Tag、APK或Release。
