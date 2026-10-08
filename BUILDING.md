# 构建本分支

需要 JDK 17、Android SDK 和项目内 Gradle Wrapper。版本与桌面基准在 `gradle.properties`，安卓代码来源另记于 `upstream.json`。

普通调试构建：

```sh
./gradlew testDebugUnitTest assembleDebug
```

模拟器检查使用独立预览应用 ID，防止测试影响真实 Hub 配对：

```sh
./gradlew assembleDebug assembleDebugAndroidTest -PtokenMonitorPreview=true
```

协议测试先运行隔离 Hub。提供官方桌面源码的 Git checkout，并使用已审核的提交；测试只向临时 Hub 填充合成数据，不访问个人 Hub。

```sh
node tools/check-hub-contract.mjs --upstream-repo /path/to/token-monitor --approved-commit 5d2db368d8313415763860d594de00e46a663418 --output /tmp/token068-contract-responses
TOKEN_MONITOR_CONTRACT_DIR=/tmp/token068-contract-responses ./gradlew testDebugUnitTest
```

不提供隔离响应目录时，有一项实际 Hub 合约测试会跳过；这不等于该项验证通过。

本分支固定签名保存在源码之外。macOS 发布脚本从系统 Keychain 读取签名材料，不输出密码：

```sh
python3 scripts/sign-release.py
```

其他机器必须安全恢复同一签名密钥才能覆盖安装既有 APK。不要提交签名密钥或密码，不要以新密钥替代旧密钥后宣称可覆盖安装。

文档与来源检查：

```sh
node tools/check-docs.mjs
python3 tools/check-code-provenance.py
```

生成的 APK 位于 `app/build/outputs/apk/`。正式交付需核对签名、版本、SHA-256 和覆盖安装，并在 Release 中注明实机未验证部分。
