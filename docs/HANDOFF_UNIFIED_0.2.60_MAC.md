# 0.2.60 联合更新 — Windows 至 Mac 接续

2026-10-13。用户已授权「整合其他提交，一起发布一个新版」，随后明确转到 Mac 继续。**当前未发布、未创建版本 Tag，源码仍为 0.2.59/code68。** 下一候选默认 0.2.60/code69，Mac 必须先核对最新远端 Release，防止版本冲突。

## 接续源码与边界

- 仓库：`Gott-mit-Uns/token-monitor-android`，既有私有仓库、正式分支 main，不改变 remote/可见性或历史发布。
- 接续分支：`release/0.2.60`。使用独立 worktree，保护 Mac 原有未提交修改。不要仅从旧 main 继续，也不要重复 cherry-pick 已合并提交。
- 合并提交 `f146cf6` 保留网络修复 `91c968d`、冷启动 `0958b17`/`48a6c0f`、字体设计资源 `45591aa` 的历史。两处 ViewModel 冲突已合并：scope 内刷新保留启动终态；等待网络 ready 时不提前关闭初始启动页。新增联合回归约束此行为。
- Windows 后续修改包括实际字体接入、公开字体测试与 Dashboard 刷新图标尺寸；以本文件所在分支的完整 HEAD 为接续源。
- 不扩大到历史 NEXT_TASKS、Hub/Lucky/NAS/Agent/路由器或桌面生产服务，不用清数据、卸载、替换签名或降级绕过问题。

## 本次源码内容

1. 默认网络更换时立即取消旧 HTTP/SSE，HTTPS 等待同一网络 ready 后重新验证；仅 NETWORK 错误在前台额外最多三次受控重试，保留认证/TLS/协议封锁、旧响应隔离和单 SSE 释放屏障。详见 HANDOFF_NETWORK_RECOVERY.md。
2. 冷启动图标只展示一次：有配置等待首次请求终态后进 Home，无配置进 Hub 设置；缓存/失败保留；普通前后台与 Activity 重建保留页面。详见 HANDOFF_COLD_START.md。
3. 字体已接入源码：界面与总量使用 Token UI（Inter + Noto Sans SC 常用中文合成），对齐数据使用 Token Data（JetBrains Mono），400/700 两字重。保留系统、自定义及既有明确 monospace 偏好；图表轴、记录与对齐数值使用数据角色。未收录字符使用 Android 系统后备，仍须实测。
4. Dashboard 复用现有 desktop_refresh 图标，绘制尺寸 8.3×8dp；设置图标仍 12dp，26dp 视觉容器与 48dp 点击范围保留。没有采用已废弃的 outline 替代图标。
5. `tokenMonitorIsolatedTest=true` 仅给 Debug 增加 `.acceptance` 后缀，便于公开 fixture 真机测试与正式 App 共存。正式 Release 不使用此标志，包名必须仍为 `dev.bing.tokenmonitor.nativeclient`。

## Windows 已执行与未执行

| 检查 | 实际结果与限制 |
|---|---|
| 合并后、字体前全量 Debug 单元/构建 | 430 passed，0 fail/error/skip；Debug APK 构建成功 |
| 联合源码 Debug 单元 | **431 passed，0 fail/error/skip，81 份 XML**；其中真实 ViewModel + TLS fixture 状态机使用 Robolectric API34/JDK17，不是真机 API36 |
| 联合 Debug/AndroidTest APK | 编译与打包成功，使用独立 Debug applicationId；**未安装、未执行任何 Android instrumentation** |
| Lint Debug | **0 error、97 warning、1 hint**；检查成功 |
| 许可生成及检查 | 67 个既有运行依赖；三种字体完整 OFL/版权另随包，生成器检查通过。Windows 超长缓存路径问题已通过 extended path 支持解决 |
| 字体资源 | 四个派生 TTF 已打包入源码；generator 为 fonttools 4.66.1，来源和 SHA256 在 design/font-preview/bundled-manifest.json；不打包完整 17MB Noto 原始资源 |
| APK 体积 | 字体前普通 Debug 15,853,562 bytes；字体后独立 Debug 19,544,224 bytes，表面差值 3,690,662 bytes；四个字体 ZIP entry 合计 3,689,677 bytes。**applicationId/构建阶段不同，不能作为同配置正式 APK 增量门槛通过** |
| 最新随包许可 | 在上述 APK 构建结束后生成，因此已有 Debug APK 不是最终源码完整字节交付；Mac 需重新构建最终包 |
| 手机 | 一度识别 Xiaomi 13 Ultra/API36，随后 USB/ADB 消失；没有安装候选、读取凭据或改变手机设置 |
| 模拟器 | Windows WHPX 可用；Google API36 镜像获取失败，AOSP 镜像下载在用户选择 Mac 接续后停止。没有启动 AVD 或执行 UI 测试 |
| 签名/版本/发布 | Windows 未取得正式签名配置；未递增版本、未创建正式 APK/Tag/Release。GitHub CLI 可连接并具有仓库权限，不等同 Android 签名可用 |

Windows 本地日志/首次失败/测试 XML/APK 指纹位于 `evidence/unified-060`，不在 Git 中。网络原始红/绿证据位于独立网络 worktree 的 `evidence/network-recovery`。Mac 不能声称已下载这些未推送日志；可继承上述准确摘要，但须建立自己的发布证据。

## Mac 必须完成的发布门槛

1. 先读 AGENTS.md、TAKEOVER_AUDIT_0.2.51.md、NEXT_TASKS.md、本交接，以及 NETWORK_RECOVERY/COLD_START、design/font-preview/README.md、RELEASE_SIGNING.md 与 GITHUB_PUBLICATION.md。核对 Git、最新正式版本与现成工具链/签名配置。
2. 保持 JDK17、SDK37、Gradle wrapper。历史 Mac 路径为 `/Users/bing/android-toolchain/jdk-17.0.20.1+1/Contents/Home` 和 `/Users/bing/android-toolchain/sdk`，先验证存在。既有正式签名从仓库外读取，不能重新生成或换 Debug 证书。
3. 定向执行 HubStartupTest、HubStartupScreenTest、HubLifecycleTest、NavigationStreamScreenTest、MainNavigationScreenTest、FontFallbackTest、BundledFontTest、BundledFontScreenTest、AppearanceScreenTest，以及受影响 CompactV5ScreenTest/网络 TLS 与缓存回归。保留首失败日志及应用/测试 APK 哈希，不放宽断言。冷启动慢成功、失败/超时、离线、未配置、Activity/进程恢复与普通前后台必须区分。
4. 检查浅深主题、中英混排、数字/金额/负号、粗体、长动态设备名和生僻字的系统后备；最大系统/App 字号、窄屏、趋势与记录轴和滚动完整可访问，刷新/设置独立点击。新增 BundledFontScreenTest 的 320dp/2.0 density 是组件条件，不能称真实系统窗口矩阵；追加实际系统窗口/字号验证并保存公开 fixture 截图。
5. 用**相同版本、签名和 Release 构建配置**建立有/无新增字体的测量，全部字体 APK 增量必须 ≤5,000,000 bytes。可用 f146cf6 无运行字体来源构建隔离基线，补齐相同非字体变更与候选元数据；基线仅测量，不安装到有真实数据的正式 App。`tools/build-bundled-fonts.py` 可用 fonttools==4.66.1 重新生成；输出只含 TTF，manifest 保存在 res/font 外。许可清单、字重/覆盖/回退、指纹及最终 APK 内容必须核对。
6. 通过必要检查后统一递增为候选版本；运行最终 Release 单元、必要 Android 回归、Lint 和许可检查，构建**既有正式签名** APK。证书 SHA256 必须为 `e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202`，包名保持，code 严格递增。
7. 正式 0.2.59→新版覆盖升级在隔离模拟器用合成配置/加密缓存/偏好验证，保留 UID 与数据证据。可连接手机时再安排受影响的蜂窝→Wi-Fi 自动恢复与前后台/缓存复测；没有执行则明确未验证，不能把原版失败改成新版通过。
8. 更新交接/版本/README/发布说明；最终产品提交对应唯一 annotated Tag 和内嵌源码指纹，安全合入并正常推送 main 与 Tag，不强推或改写旧发布。只发布一个版本。
9. 按既定四附件方式建立草稿 Release：正式 APK、SHA256SUMS.txt、release-manifest.json、VALIDATION.md；全部下载回读逐文件核对后正式发布，再下载 APK 验证 SHA256。中英文说明区分真实执行/继承/未验证及剩余风险。签名、构建或关键升级门槛不满足时停止发布，给出具体阻塞。

## 必须保留的真机验收结论

原 0.2.59/68 的 Xiaomi 13 Ultra 真机验收：正式 SHA 校验/覆盖安装与缓存保留、Lucky HTTPS Wi-Fi/蜂窝读取、离线缓存、五次前后台及两主题/大字体通过；蜂窝→Wi-Fi 自动恢复初次和两次复测失败，主页手动刷新可恢复。共有 12 份性能测量，进入趋势及恢复有重复慢帧，未声称性能提升。家庭 LAN、连续两分钟锁屏、后台请求/SSE 连接数及严格厂商后台未验证。旧版原始报告保留在 Windows `token-monitor-v5-acceptance-20261013/ACCEPTANCE.md`，不在此源码分支。不得改写这些历史结果。

发布或如实说明阻塞后停止，不启动下一项开发。
