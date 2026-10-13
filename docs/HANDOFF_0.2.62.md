# Android 0.2.62 / 71 — Desktop 0.70 adaptation

2026-10-14，Asia/Shanghai。Android 最新正式基线是 0.2.61/70，tag产品提交 `29121e4e4d36e98e56a389d22a1381a78a1a385f`，远端main文档提交 `7e85cb1`。原主目录仍为main/3731975，未提交NEXT_TASKS及未跟踪AGENTS-original、REA审计保留，未reset、stash或清理。独立开发分支 `feat/desktop-070-adaptation`，工作区 `../work/token-monitor-android-070`。

上游固定：v0.69.0 `3f106ae3d4712b26cced8272f0d488eaab0de1e6` → v0.70.0 `ac4eeb2c4aea239dd94b67f074c73f69aafc46f8`。标签对象SHA与commit已分别核实；116变化文件按20项分类。先固定源码及差异，再实施适合手机的显示变化，详见DESKTOP_070_DIFF.md。

## 本轮范围

- 默认逐位滚动，保留Count up模式，650ms有限动画。主数字、工具/模型/会话/项目/设备分解的既有数字叶节点适配；金额、图表与Widget静态读数不增加滚动。没有Dock。
- 滚动只更新绘制层，最多每字符两层，续接当前可见字形位置；单个数字使用一致的最大字宽槽位，保持用户字体，主数字与分解均保留原Text语义、选择/点击和自然大字体换行。缩位采用左对齐尾部空槽，收束不横移。长列表只对窗口裁剪后可见的叶节点启动；后台、减少动效、真实系统比例0及离屏收束，回来不重播。未知/首次可用数字直接显示，模式切换立即准确收束。
- Count模式保留原Headline/Breakdown纯数值时间线。旧count专用UI测试显式选择count，保留中间值、周期、系统尺度、前后台原断言；新增真实Headline roll/count切换与缺失/周期专项，不以更换模式删除既有验收。
- 动画偏好是独立可选两字节文件；主设置仍V16，切换动画模式不改旧设置字节。缺失、未知、截断或错误长度回退roll，读取不重写。旧字体、双周期额度、语言时区、缓存、Widget均保留。
- 实时指示器、工具图标、标题图标放入外观高级自定义，原选项/字段/tag保持；数字动画选项在外观基本区域。紧凑弹出菜单仍按原规范，主控件48dp不变。
- Home近期选择一次解析时间，用候选堆仅排序上限与活跃行；额外保留running/waiting并按租约过期。Sessions保留完整会计成员后分组分页；独立会话快速路径、正则复用、避免多余键排序和尾列表复制。2k/10k旧新同数据结果与配对性能在SESSION_070_PERFORMANCE.md。
- CatPaw仅消费Hub已上传工具用量，名称/隐藏/置顶/排序采用原工具体系，追加末尾、不增加采集器。MiniMax按量余额使用固定 `billing/credits/remaining/currency` 合同，CNY/USD分别展示，真0和缺失区分，不推测百分比；旧Token Plan双周期不变，缓存额度Widget复用相同投影。
- 设置页显示桌面参考0.70.0和部分对齐/Hub依赖，Android版本独立递增。

## 服务端依赖与真实观察

DXP4800只读health及鉴权stats均200，4设备、core78/runtime5；当前样本没有CatPaw或MiniMax。没有修改/升级/重启生产NAS、Hub、Agent、Lucky、路由器或桌面。新provider真实输出未联调，合成合同与缓存支持不是生产上线证明。

CatPaw源端启用/采集；MiniMax按量地域与余额查询；T3标题SQL查询；Codex压缩日志/归档优先级；Hermes Tokscale reasoning与公共normalizer配对修复；DeepSeek精确keepalive哨兵回退；StepFun令牌续期；Cursor显式1830000ms/后台150000ms同步预算均由数据产生层负责。Android保持Hub权威total/cost/unpriced/model，不猜测或重复计算，不扩大手机超时，不用切入口绕过401/403/TLS。详细源码与验收条件见DESKTOP_070_DIFF.md、HUB_070_DEPENDENCIES.md。

## 验证与首失败

候选Release单元458项、Lint0错误/101警告/1提示已通过；任务专用API36模拟器emulator-5600，正式签名候选实际由GitHub 0.2.61覆盖升级，旧加密Hub缓存及四份字体/设置profile可读、UID和原文件保留，无卸载/清数据。6项新UI/缓存专项第二轮通过。

首轮provider测试调用类型不匹配改为正确String类型；旧桌面参考断言更新0.70且保留fullyAligned=false。首轮新UI2失败保留：数字绘制回调检查早于Android绘制完成；补充waitForIdle，并把主数字局部捕获调用改为稳定的直接组件调用后，原模式/活动断言通过。未删除或放宽断言。只读复核还修复比例字体静态与动画槽位不同、缩位横移、可选偏好读取长度和缺失值动画资格；两项组件测试白底暗色文字是未提供Surface背景的测试宿主问题，补相同主题背景以获得可读截图，不改产品颜色。

49项原回归首轮48通过，唯一路径名断言失败：旧MainDisplay速率fixture只有hostname，却按Public A/B断言，既有名称合同优先Hub displayName/id。另一个全新API36模拟器在GitHub正式0.2.61+原测试APK上复现相同失败；源resolver本轮未改。fixture明确提供displayName，保留名称、吞吐、前后台全部断言后定向复测。

最终cfc9159包55项回归中54通过，201会话分页在初次进入后Compose idle超时，单独同包重现。其Breakdown进入动画起点使用System.nanoTime，而测试帧使用Compose mainClock，两个时钟域使有限entry不能结束。仅给此测试提供与既有动画专项相同的mainClock域后，原分页/SSE/零额外读取断言及正常动画保留，2.224秒通过；没有更改生产时钟、超时、断言或动画开关。第一次测试补测编译缺少导入已修正，旧测试APK误复跑的指纹/失败留存，不能当作新测试已执行。

随后统一55项中另外4个未提供时钟域的旧Home/会话/主显示用例出现同类idle超时；仅在相应测试宿主补同域时钟，保持正常动画、原数值/计数/导航/缓存断言，7项受影响定向回归22.116秒全部通过。生产dex、资源、资产及本地库与此前已验收候选逐项字节相等，测试修正未改生产逻辑。

最终冻结源码后执行同Release配置的必要单元/Lint/UI/网络/缓存/Widget及窗口矩阵、实际已安装APK指纹和签名升级补验，结果与SHA以Release四附件VALIDATION.md/release-manifest.json及GITHUB_PUBLICATION.md回执为准。本源码交接不提前宣称发布完成。

## 未验证和回退

没有实体手机安装。新版Xiaomi/其他实体设备的覆盖升级、蜂窝→Wi-Fi自动恢复、真实LAN/Lucky HTTPS切换、锁屏/厂商后台、Launcher Widget尺寸/电量、长列表FPS均未执行。历史0.2.59 Xiaomi蜂窝→Wi-Fi失败仍保留；本轮模拟网络回归不能把历史实网失败改成通过。没有导入R8/资源裁剪或瘦身实验。

体验回退：外观中切回Count up；原选项无需重设。版本回退不能承诺非debuggable Release直接安装较低versionCode。保留0.2.61 Tag/APK；需要完整代码回退时用固定0.2.61源码（保留现有缓存/新工具设置兼容）构建更高versionCode的原证书恢复版，再覆盖升级，另立明确任务。不要卸载/清数据，也不要覆盖旧Tag或资产。
