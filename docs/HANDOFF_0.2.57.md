# 0.2.57 — 项目名称与 README 更新

2026-10-12。基于已发布0.2.56/main 25f9def，分支docs/project-readme-refresh。

项目名称、Android应用显示名、小组件添加提示、随包许可页标题和Gradle工程名统一为Token Monitor Android。保留applicationId、签名证书和现有目录/历史文件名，避免破坏升级关系。版本0.2.57/code66；今后本次交付APK名为token-monitor-android-0.2.57.apk。

README改为产品首页，删除历史更新流水，突出用量/费用、多维明细、趋势/Dashboard、额度/订阅、SSE/缓存、主题/字号/主页模块、小组件与Hub构建信息；提供下载、连接、隐私、开发及来源许可入口。历史交接和Release没有删除或改写。

四张PNG在独立API36模拟器emulator-5584从本次正式签名APK实际渲染获取；公开合成数据，主页中文、设置英文，展示双语和两种主题。桌面/旧截图未作为本次图片，未使用真实Hub数据。逐图视觉核对完成，PNG进入docs/images供GitHub直接显示，原始证据仍不入Git。

验证：正式Release和ReleaseAndroidTest构建通过；Lint0错误/93既有警告/1提示。现有DesktopParityScreenTest浅深主题各2项、EnglishScreenTest1项、SettingsStructureScreenTest指定方法两种主题各1项，共7次定向检查通过。首次构建因JAVA_HOME缺少macOS Contents/Home而未进入编译，修正环境路径后通过；未改工具版本或清数据。通知生成器--check通过（67项），APK内名称与上游归属核对通过。application label实际为Token Monitor Android，version0.2.57/code66，package dev.bing.tokenmonitor.nativeclient；apksigner验证通过，沿用正式证书e5c6fe0c7b0a4fb2834c05126b5b99fa0056dff9274a38eda682bd67f5f7e202。正式APK SHA256：932ba020525ee79a367e3dc2fd1451a0075d106f70ad28d4c643b8513ee4dbe6。

本轮未改统计、网络、缓存或页面布局，不重复未受影响的全量单元/网络套件。应用与测试包安装和页面启动通过；没有本轮旧版数据保留专项、真机、蜂窝/Lucky或生产Hub验收，不操作NAS或其他模拟器。旧Debug迁移限制保留。

交付dist/release-0.2.57/包含APK、SHA256SUMS、release-manifest、VALIDATION。正式发布与下载回读结果记录于GITHUB_PUBLICATION.md。本轮到发布验证后停止。
