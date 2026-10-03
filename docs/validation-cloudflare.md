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
