# 工具、模型列表与设置入口对齐

当前0.2.31-dev（code40）移除工具、模型主列表多出的搜索框和模型规则入口，并按桌面原始层级恢复独立工具设置分组。七组设置为常规、主界面、外观、工具、额度、订阅、多端同步；工具管理、只读模型规则与定价从工具设置进入。保留八个主视图、旧偏好兼容、标题平均速率和主页Token动效。

固定桌面基线v0.68.0，commit `5d2db368d8313415763860d594de00e46a663418`。`index.html`的两个search输入为clientDisplaySearchInput和limitProviderSearchInput，属于设置管理。主列表renderRows没有通用搜索输入；移除安卓工具、模型页的额外输入及其过滤条件，保留设置搜索。只读价格和模型规则长表的搜索/分页属于手机查看适配。

原始设置八组依次为general、main、window、appearance、tools、limits、subscriptions、sync；排除桌面window后保留七组。模型别名、定价和工具管理的实际祖先均为toolsSettingsDetails。安卓原把工具管理放main、定价放general、规则放模型页；本轮归位。原tools标题是Collection（采集），安卓只保留显示管理与只读共享数据，以Tools/工具命名并使用已有工具图标。采集开关、账户登录和远端写入仍排除。设置一次展开一组，设置子页返回当前分组并沿用进入前的主视图。

143项单元、129项普通API36通过；设置及工具管理六配置、共享数据和主要页面共16份报告、100张公开PNG，另有同包33帧Token动效。真实NAS只读桥接共享数据10GET、列表/设置6GET、前后台关闭及恢复通过；0.1.7和0.2.27覆盖升级保留UID、Keystore、精确缓存与偏好。Lint0错误、80警告、1提示。实体手机、直接局域网和Lucky外网仍未验证。

所有最终证据位于`evidence/list-interaction/final`，并绑定同一APK。`preview.html`提供主要页面及工具设置浅深主题，`motion-frames/preview.html`提供主页Token播放与逐帧查看。初始未归位的搜索专项仅实际执行ToolDetailScreenTest两项；不能把多类参数的意图算作实际执行。后续通过直接运行器验证五个类全部执行的10项检查，再完成最终完整回归。`source/settings-groups.json`保存实际HTML祖先，`source/search-controls.txt`记录搜索控件与列表源码摘要。

首轮工具标题误用数据页全大写资源，截图发现后改为设置标题Tools；首轮证据保留在`pre-title-case`，最终回归、100张PNG和33动效帧均重新绑定最终安装包，没有混用旧证据。

最终APK：`dist/token-monitor-native-0.2.31-dev.apk`，16,353,750字节，SHA256 `c51c10c7f7ac631996becfa275af389cfc7f052b4ac1740d8aa399341f5e76b9`。四附件与权限未变化，613组中英文资源匹配，40份归档校验通过。普通测试包恢复且19个可选测试类排除；所拥有模拟器清理、停止应用并恢复显示和动效设置，见`package-verification.txt`。

真实NAS验证经只读测试桥接，不作为生产直连或外网证明。0.2.29直连预检因模拟器虚拟子网不包含NAS而被正式范围拒绝，未放宽规则。用户实体手机暂时不可连接，Lucky根地址仍待提供；没有修改NAS、Hub或Agent配置。整体目标和用户验收继续进行。
