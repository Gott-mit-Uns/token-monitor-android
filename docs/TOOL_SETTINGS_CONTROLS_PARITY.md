# 工具设置内部控件对齐

固定桌面v0.68.0基线，0.2.32-dev/code41最终源码。打开工具设置后直接显示列表管理，移除安卓多出的tool-list-toggle和对应展开状态。恢复原始说明/恢复顺序/显示全部同行、搜索与工具行顺序；本机显隐/置顶/排序的存储保持。

模型别名和自定义模型单价使用原始cursor-settings-toggle标题/状态/箭头形态，11sp标题、9sp状态、原始行高、透明背景和状态边框；整体48dp触控仍为手机适配。模型规则弹窗标题由误用的整段说明改为原始“模型别名”，说明移到正文。规则和定价长表继续使用手机只读明细页面/弹窗，编辑、采集和提供商登录仍排除。

直接运行固定源码modelAliasForm.js及app.js的状态表达式，记录两种语言的34组标题/状态；原始空别名状态显示实际分组模式（Off/Merge duplicates/Strip prefixes），不能用Automatic声称分组开启。五个新增原始标题/计数字段及已有分组文字与这些输出逐项匹配。三个原始CSS声明保存在evidence/tool-settings-controls/source/settings-control-reference.json。

五个实际执行的交互类共10项通过，包含直接显示工具搜索、状态模式变化、空规则/撤回、正确弹窗标题、七组设置及返回路径。证据为focused/controls.txt。143项单元、129项普通API36通过；34组原始标题/状态匹配。六种设置与工具配置、共享数据和主页面共16份报告、106张公开PNG，另有同包33帧Token动效。真实NAS只读桥接共享数据10GET、列表/设置6GET、前后台关闭与恢复通过；0.1.7和0.2.27覆盖升级保留UID、Keystore、精确缓存与偏好。Lint0错误、84警告、1提示。实体手机、直连NAS及Lucky外网仍未验证。

最终证据位于evidence/tool-settings-controls/final，并全部绑定同一APK。两种共享设置标题在六配置均实际显示后截图；新增截图断言没有改变生产APK。页面与工具设置预览为preview.html，Token播放/逐帧入口为motion-frames/preview.html。真实NAS经只读测试桥接，不作为直连或外网证明；实体手机暂不可连接，Lucky根地址仍待提供，没有修改NAS/Hub/Agent配置。

最终APK为dist/token-monitor-native-0.2.32-dev.apk，16,358,187字节，SHA256 `3944eb6d14af15d483b6f1980ff0cf92cfa4c8375059c4bd24335e24a3d6f20b`。41份归档校验、四附件与权限、618组中英文资源、19个可选类隔离、普通测试包恢复及所拥有模拟器清理/显示与动效恢复通过，见package-verification.txt。用户验收与整体目标继续进行。
