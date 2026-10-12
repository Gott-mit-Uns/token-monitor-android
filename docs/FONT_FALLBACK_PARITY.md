# 字体回退与整页对照配置

本轮封存为 0.2.39-dev / code48。针对完整原始页面对照发现的两处问题，不修改 Hub 数据或网络策略。

## 实际问题与修正

原先安卓只把自定义 font-family 列表的第一个名称传给系统。Android 在名称不存在时会静默返回默认字体，后面的 serif、monospace 或其他可用系统字体因此被忽略。现在按顺序尝试列表，跳过不可用名称，采用第一个可用字体；默认界面/总量字体与角色跟随规则保持。带引号或转义的逗号属于名称，不会错误拆出候选；支持 CSS 十六进制字符转义。没有新增字体文件、下载或依赖。

例如 `"TokenMonitorMissingPublicFont, monospace", serif` 中，第一项整个名称不存在，应选后面的 serif；名称内的 monospace 不应被当成回退项。`TokenMonitorMissingPublicFont, sans-serif-condensed, serif` 应保留当前设备实际支持的 condensed 字体。

完整桌面截图工具此前显式设置 displayFontFamily 为空，代表“跟随界面”，而安卓公开样例使用总量默认字体。桌面 main.js 的新安装默认值为 fontSettings.DEFAULT_DISPLAY_FONT；现在直接调用该原始模块的默认值，统一比较配置。新证据放在 evidence/font-fallback/source，旧封存不改写。Mac 与 Android 的系统字体字形仍由各自平台提供，不宣称跨平台字形像素相同。

## 最终验证与范围

148 项单元检查通过，包括候选顺序、引号/转义逗号、CSS 字符转义及拒绝不安全声明。完整原始页面 34 张、167 个文件哈希，无浏览器错误或失败请求；配置审计见 source/font-fixture-audit.json。Lint 无错误、85 警告、1 提示。

普通 API36 首轮 131/132，新增字体检查错误比较了两个 Compose 包装对象；改为实际解析出的原生 Typeface 后，专项通过，整套重跑 132 项通过，失败诊断保留。外观六配置每组两项及主要页面浅深色各两项共 8 份报告、16 项交互、46 张原生 PNG。字体选择/独立显示/无额外 Hub 读取、保存与重建均在实际界面核对。同包重新录制 33 帧 Token 动效及后台返回断言。

本地页面对照 9 个选项均实际加载，无浏览器错误或外部请求。图库说明从实际报告生成，不沿用工具触控专项的固定描述。签名、权限、621 对中英资源、四项附件、普通测试包的 19 个可选类隔离、48 份安装包归档与模拟器清理均核对通过。

本轮未复跑真实 Hub 与覆盖升级。0.2.38 的七份运行报告保留原包哈希和范围，不替代本轮。实际 Lucky 成功与实体手机尚未验证；桌面/安卓系统字体字形不宣称像素相同。

安装包 `dist/token-monitor-native-0.2.39-dev.apk`，15,019,170 字节，SHA256 `841f11b707fb001e5fe7ebcc32041dfd31440841be74ae7c6a7fa148583e932a`。证据 `evidence/font-fallback`，整页预览 `final/preview.html`，动效 `final/motion-frames/preview.html`。整体目标与用户体验验收继续进行。
