# 0.2.52-dev交付与版本规则

2026-10-12用户取消此前禁止自动递增正式版本的约束，并要求最新APK。本轮从5a171c6858987a9d2a79195d9700199a0cf5c00c出发，仅更新版本、关于页测试期望及版本管理文档，产品功能保持V4结果。

## 当前版本与规则

- 最新Android开发交付：0.2.52-dev，versionCode61，applicationId dev.bing.tokenmonitor.nativeclient。
- 新交付APK递增versionName及versionCode；同一交付候选的临时重建不重复递增。versionCode独立单调递增，不从显示版本数字换算。
- 延续现有编号习惯：下一次新交付默认0.2.53-dev/code62；稳定版是否去掉dev后缀由实际验收状态决定，不能仅因递增版本就宣称稳定。
- 桌面参考仍v0.69.0、部分对齐；Android版本、桌面参考、Hub实际版本独立。Hub版本仍未知（未提供）。
- 递增版本不等于授权GitHub推送/Release或生产服务修改；本轮均未执行。

## APK与定向验证

文件：dist/token-monitor-native-0.2.52-dev.apk，为Debug开发验证包。

APK SHA-256：e5fc166abab4055c5184cb6001c7015d2c2c251ccb67d2bf4b401f880596b979。

签名证书SHA-256：560f4beedb200ebd4ac974702d3f5417e57929f1293f203c8d20f28b5d732107。与已核实的旧Debug交付相同；未知Release或手机实际签名仍需核对。

构建assembleDebug/assembleDebugAndroidTest成功；只改版本与测试期望，没有重复跑未受影响的完整单元/Lint套件。沿用V4功能验证，新增两项Android定向检查通过：关于页在中英/浅深四组合显示0.2.52-dev (61)；真实HubViewModel对合成Hub的八页导航保持单SSE，后台关闭、前台重连。

自有API36 emulator-5556覆盖安装0.2.51-dev/code60→0.2.52-dev/code61成功，appId10670保持，display-preferences.bin覆盖安装前后校验一致。实际设备base.apk与交付APK校验相同。没有卸载或清数据；本次没有真实Hub加密缓存样本，因此不据设置字节保持声称实体手机旧Hub数据已验收。

旧dist/token-monitor-native-0.2.51-dev.apk及V4开发验证包都保留，校验未变。源码差异、构建、签名、版本、APK/测试APK校验及Android结果位于evidence/version-052/；提交SHA记该目录final-receipt.json和本地v0.2.52-dev带注释标签。

## 继承功能与未验证边界

包含Antigravity双周期、V3连接策略、V4可选会话状态/关系/Grok分组、统一设备名称与图标、导航窄屏/浏览状态、设置换行及明确版本信息。详细功能/测试/局部差异见HANDOFF_DESKTOP_069_UI.md。

真机覆盖安装与旧Hub缓存、触摸/字体/弹窗/键盘/动画、实际Lucky公网及蜂窝切换仍待授权设备验收。本轮不新增功能，不发布GitHub，不升级NAS，交付后停止。
