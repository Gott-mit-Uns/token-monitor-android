# 来源与许可信息更正交接

2026-10-12，起点 main f4c3bc6（正式0.2.55/code64），独立分支 fix/source-license-attribution。

上游项目来源统一为 https://github.com/Javis603/token-monitor 。随包介绍和图标说明去除固定v0.68.0限定与基线SHA展示；具体参考提交仍在UPSTREAM_SYNC.md等开发追溯文档保留，未删除历史记录或声明完全兼容最新桌面。补全Lucide/Feather项目地址和导航图标范围。THIRD_PARTY_NOTICES.md中的旧65项依赖计数更正为实际67项。tools/update-third-party.py同步修改，防止再次生成旧信息。Maven POM中的依赖版本与项目链接保留原始元数据；它们是具体依赖的归属信息。

验证：使用0.2.55现有精确运行清单和缓存公共POM离线重新生成，67项/1份去重许可；--check通过。逐项核对67个坐标，依赖段与原版本逐字相同；上游MIT、Lucide ISC、Feather MIT原文保持；git diff --check通过。上游公开LICENSE亦核对版权和许可类型。首次静态核对错误地假设每个依赖都含publisher字段，改为检查精确坐标后通过，记录于本工作区evidence/source-license-attribution/validation.txt；未修改产品来迎合断言。

仅修改声明文字与通知生成工具，没有修改Compose布局、字符串资源、协议、业务代码或生产服务。没有独立构建APK、执行Android界面/真机验证或发布。

用户在协调聊天01a12ff9-8e65-7a22-b08a-e101ba6920d7明确要求“发布到GitHub，和其他任务进行的更新合并一起发布”。本分支提供独立提交供统一0.2.56/code65合并验证发布，不单独递增版本。另一个UI分支也修改open-source-notices.txt；合并时保留其展示基线说明，将项目来源与开发参考分开，并同步生成器再运行--check，避免生成结果与模板不同。独立提交完成后停止。
