# 来源说明

本应用参照 [Javis603/token-monitor v0.68.0](https://github.com/Javis603/token-monitor/releases/tag/v0.68.0) 的Hub协议、统计显示、订阅日期和模型名称语义，以Kotlin/Compose独立实现。基线提交为5d2db368d8313415763860d594de00e46a663418。没有引入旧Android fork的业务源码。

上游Token Monitor由Javis维护，采用MIT许可证。其完整版权与许可文本保留在app/src/main/assets/token-monitor-upstream-MIT.txt，并随APK包含。模型别名与订阅显示逻辑依据上游行为重新实现，仍保留来源说明。

工程使用标准Gradle Wrapper作为构建启动工具。AndroidX/Compose、Kotlin、kotlinx.serialization和OkHttp通过声明的依赖获取。已由精确解析的debug/release运行依赖生成包含传递依赖的65项清单，见docs/DEPENDENCY_LICENSES.md。对应Maven POM（含父POM继承）均声明Apache-2.0；二进制提供的许可/声明原文按内容去重保留，随APK包含在app/src/main/assets/open-source-notices.txt，设置页可离线阅读。此清单不包括测试库、Gradle工具或Android系统本身。依赖变化后需重新生成并核对，不能沿用旧版本清单。

当前没有为本项目的原创部分指定公开源代码许可证；发布时由项目所有者确定。独立包名和签名不继承旧fork的升级关系。
# Desktop interface brand assets

The monochrome vector shapes in `app/src/main/res/drawable/brand_*.xml` are converted from the icons bundled by Javis603/token-monitor v0.68.0, commit `5d2db368d8313415763860d594de00e46a663418`, under `assets/icons/`. Provider and operating-system names and marks remain those of their respective owners. The existing upstream MIT notice is retained in this project and the application assets. These shapes are used to identify the same providers and platforms as the desktop interface.

Desktop settings/home glyphs and the v0.69.0 tool/model/session/status/trends navigation artwork are derived from Lucide Icons.
Lucide Icons license:

ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

Feather-derived Lucide icons license:

The MIT License (MIT)

Copyright (c) 2013-present Cole Bemis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

