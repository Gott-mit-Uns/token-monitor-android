# Android 0.71.0-01 正式交付补充

本文件继续 `HANDOFF_0.71.0_ANDROID_0.2.65.md` 的候选开发与验收记录。用户随后授权正式 GitHub 发布，并要求参照 NAS 编号；本次 versionName 改为 `0.71.0-01` / versionCode 74，固定桌面 v0.71.0，未追随后续 main。原候选文件及测试记录保留为历史证据。

## 改动

- 版本命名规则与 NAS 一致，Android 修订独立；见根目录 VERSIONING.md。
- 新版更新解析器接受严格两位修订号，比较上游基础与修订号，兼容历史纯数字与先前候选组合编号。
- 正式入口 v0.71.0-01 与旧更新兼容入口 v0.2.65 提供相同签名 APK；兼容入口设 latest。新版从严格标题识别正式编号，并打开正式说明。老版无需预先升级更新检查器即可获取 APK。
- 未改变已验收的 Goose / Notion / WorkBuddy 数据展示、会话列表、Widget 移除、布局、网络恢复或采集边界；未启用 R8 / 资源压缩。

## 验收范围

候选阶段 464 个单元测试、主页等 13 项、设置等 22 项、缓存/网络等 22 项、0.71 展示 4 项和大字体/主题验收结果见前份交接；这些是先前候选字节对应的验收，本次只继承不受更新检查和版本字段变化影响的功能结果。正式包会重跑全部单元测试和 lint、更新 UI、0.71 展示以及原始签名 0.2.64 覆盖升级。最终字节、次数和发布读回另附回执。

旧版验收用真实下载的 0.2.64 APK，运行旧包内解析器及设置 UI，并以组合标签拒绝为阴性对照；新测试 APK不替换旧主 APK的逻辑。点击说明、APK入口验证确切 GitHub 地址，URI 捕获避免外部浏览器环境影响。发布后还会将实际公开 API 的完整元数据送入相同测试。

## 剩余差距

Pi / CodeBuddy 的远端会话摘要已支持，聊天明细尚无 Hub 接口；本次无伪造内容或 Android 本地采集。Notion fixture 验证不代表所有真实账号登录/资格已验证。生产 NAS 的新供应商字段与真实手机网络需要独立证据，本次不操作 NAS；手机由用户自行安装。

两条固定 Release 共用相同源提交和 APK，仅文件名按旧解析器需要不同。后续须保持兼容入口的标题、版本映射与正式入口一致，不重写固定 Tag。

## 最终包验收

最终源码 `453ec2a31cb1feb766c33be1abebe29cccc00300`，APK 15,239,578 bytes，SHA-256 `2e62a0239f6f6917d321d30152ccb6986e77a95e36fa693accb26d5594c3ccbb`。包名 `dev.bing.tokenmonitor.nativeclient`；原证书 SHA-256 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202` 不变。

全部 466 项单元测试通过；lint 0 Error/Fatal，101 Warning、1 Hint。API36 ARM64 emulator-5660：真实旧包更新界面 fixture 1/1，旧包数据准备 1/1，最终包升级保留数据＋更新 UI＋0.71 展示 8/8，冷启动成功。两份不同文件名的 APK 完全相同。证据 `evidence/publication-065/` 在独立工作树保留；公开可审阅摘要随 Release 的 VALIDATION.md / release-manifest.json 交付。此前候选全回归和真实手机验收边界维持上述区分。

## GitHub 正式发布回执

2026-10-15：PR #4 已自动合并，merge/source release commit `ac37728cd79d8bdb9b4e6104fb25586a3b6f7d11`，与 build commit 的 `app/` 内容完全一致。正式 Tag `v0.71.0-01` 和旧更新兼容 Tag `v0.2.65` 均指向该提交，未重写历史 Tag。

- 正式 Release：https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.71.0-01 （id 413274582）
- 旧版兼容 latest：https://github.com/Gott-mit-Uns/token-monitor-android/releases/tag/v0.2.65 （id 413274713）
- 两条 Release 均非草稿、非预发布，各有 APK / SHA256SUMS.txt / release-manifest.json / VALIDATION.md 四个附件。两条严格标题均为 `Token Monitor Android v0.71.0-01`，兼容入口由 GitHub latest API 实际返回。
- 两条草稿的 8/8 附件、正式发布后的 8/8 附件逐字节核对通过；两份 APK 匿名下载 SHA-256 均为 `2e62a0239f6f6917d321d30152ccb6986e77a95e36fa693accb26d5594c3ccbb`。匿名 latest API 也验证通过。
- 实际公开 Release 元数据在真实旧 0.2.64 APK 内解析和更新 UI 测试 1/1，通过后使用匿名下载的正式 APK 覆盖安装，再验加密缓存/历史/UID/四组偏好和新版更新 UI 2/2，均通过。首次将公开 JSON 放在公共 Download 目录因 Android 分区存储被拒绝（测试输入路径问题，保留失败记录）；改放本应用外部专属目录后通过，产品权限与代码未修改。
- 手机由用户自行安装，本任务未安装手机；NAS 未修改。真实账号/Hub/手机网络与远端聊天明细的差距维持上述边界。

完整本地证据保留在独立工作树 `evidence/publication-065/`；正式附件清单与摘要可从上述 Release 下载。原候选 APK、指纹和之前阶段的交接记录不覆盖。
