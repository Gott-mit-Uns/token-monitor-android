# 当前验证入口

沿用仓库 Gradle wrapper、JDK 17 和 Android SDK 37，不通过 clean、清数据、替换签名或放宽断言绕过问题。认证、签名等配置保留在仓库外，日志与证据不得含凭据。

| 范围 | 入口与要求 |
|---|---|
| JVM 单元 | `./gradlew :app:testDebugUnitTest`；正式候选按现有流程执行 Release 对应任务 |
| Android 测试编译 | `./gradlew :app:assembleDebugAndroidTest`；编译成功不代表执行通过 |
| 定向模拟器测试 | 使用已有自有模拟器与 `tokenMonitorIsolatedTest=true` 隔离包，选择受影响测试类；不覆盖用户生产包 |
| 可选测试 | live / permission / perf / capacity 由 app 构建配置中的对应 tokenMonitor 开关显式启用；普通套件不包含全部可选范围 |
| 界面改动 | 根据范围检查深浅色、小屏、大字体、长文本；保存实际截图并记录应用/测试 APK 指纹 |
| 发布 | 沿用[签名规则](RELEASE_SIGNING.md)、[版本规则](../VERSIONING.md)和[当前交接](HANDOFF_0.72.0-01.md)，区分实跑、继承和未验证范围 |

## 共享测试样例

`app/src/sharedTest/resources/` 是三份公开样例 home-quota-parity.json、limit-layout-parity.json、subscription-layout-parity.json 的唯一来源。Gradle 同时将其加入 JVM test resources 和 androidTest assets，现有 classloader/assets 读取方式及文件名不变，不进入生产 main resources/assets。

`tools/capture-home-quotas.cjs`、`capture-limit-layout.cjs`、`capture-subscription-layout.cjs` 只写入共享目录；不再复制到原两个位置。这些工具依赖固定上游源码与原有浏览器环境，未运行生成器不能声称重新采集桌面结果。其余未重复样例仍留在原目录。

本轮资料整理的实际验证与限制见[整理记录](DOCS_CLEANUP_VALIDATION.md)。历史验证流水完整保留在[整理前快照](archive/history/VALIDATION_BEFORE_CLEANUP.md)，首次失败、原 APK 指纹和证据目录不删除，不改写为新版通过。
