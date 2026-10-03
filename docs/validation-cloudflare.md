# v0.65.0-cf.2 中文与设备别名验证

日期：2026-10-03。versionCode 650102，包名和签名沿用 cf.1。

- JVM：126 项，0 失败。
- Android Lint 检查通过（无错误；保留上游兼容属性等警告）。
- Android 12 / API 31：56 项，0 失败。
- Android 16 / API 36：56 项，0 失败。
- 中英资源完整性：373 个界面资源、21 个原小组件资源和 85 个小组件补充资源，两种语言键集合一致。
- 已在 release APK 首次连接页切换简体中文，并验证强制停止／重启后仍保留该选择。
- cf.1 与 cf.2 签名证书一致，已在两个模拟器验证直接覆盖安装；尚未用用户真实凭据进行升级联调。
- 新测试覆盖桌面术语、动态模板、中文／英文切换及持久化、中文小组件、设备改名／取消／恢复、32 个 Unicode 字符、非法换行、Hub／设备隔离及离线后的同 ID 恢复。
- 9 项中文合成截图测试包括主页、模型、设备、项目、趋势、设置、工具模型筛选及各尺寸小组件。

真实手机和用户已认证 Cloudflare Hub 仍未联调；测试使用合成数据，未读取真实凭据。原协议与统计测试继续通过，本次不改变刷新频率。

---

# v0.65.0-cf.1 验证记录

验证日期：2026-10-03。上游基线 `b19e08f17fe265e7895dae7d85bb545495de859d`，独立包名 `io.github.theminionooo.tokenmonitor.cloudflare`，versionCode 650101。

## 自动验证

- JVM：121 项，0 失败。
- Android 12 / API 31 模拟器：42 项，0 失败。
- Android 16 / API 36 模拟器：42 项，0 失败。
- 设备测试使用独立 `.preview` 包及合成数据，覆盖原有界面、小组件、连接生命周期，并加入 Keystore 并发、随机加密、篡改拒绝和缓存容量测试。
- 协议测试覆盖公网 HTTPS 地址、私网后备、认证及重定向、SSE 完整／新鲜度事件、版本缓存和关闭上游更新。
- 修正了上游小组件图表位图高度估算：按已配置布局实测可用空间生成位图，避免 dp 独立取整和额度行高度造成缩放。选择器测试改用 Android 的像素取整方式。

## 可复现构建

使用 JDK 17、Gradle 9.7.1、Android SDK 37.0 及 Build Tools 37.0.0。依赖版本沿用上游。SDK 37 平台应采用当前 command-line tools 安装。

```sh
./gradlew -PtokenMonitorPreview=true assembleDebug testDebugUnitTest connectedDebugAndroidTest
```

签名 release 使用 `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 四个环境变量运行 `./gradlew assembleRelease`。不要把实际凭据写入命令历史或源码。macOS 可使用 `scripts/sign-release.py`，它将签名口令保存在 Keychain，私钥保存在仓库外的私有目录；必须安全备份签名材料以便后续升级。

Release APK 已通过 v2/v3 签名校验，并在 API 31 与 API 36 模拟器安装成功。另运行 8 项合成截图测试并检查连接页、设置和大尺寸小组件。最终设置标题文案调整后重新构建签名 APK；该文案调整不改变协议或统计实现。

## 尚未验证

用户真实 Cloudflare Hub 的已认证响应、真实设备分类与余额、真机电池行为及各品牌启动器尚未联调。需要在手机安全配置现有 Hub 后检查数据一致性、新增用量推送及离线缓存；模拟器测试通过不代表已完成日常使用验收。
