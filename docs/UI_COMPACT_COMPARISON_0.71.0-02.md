# 0.71.0-02 紧凑设置与记录日历：改前／改后原图

改前为实际正式0.71.0-01/code74（APK SHA-256 `2e62a0239f6f6917d321d30152ccb6986e77a95e36fa693accb26d5594c3ccbb`）；改后为最终正式签名0.71.0-02/code75（`c9948af3fbfbe848d002fb5af8ed924b6e7b1e260a4f58526de8d2e06d33763c`）。所有PNG均从自有API36模拟器测试原图复制，未裁切、重绘或隐藏溢出；部分保留点击后的真实按压／焦点反馈。点击缩略图可查看完整原图。

使用公开合成fixture，无真实账号、凭据或真机截图。每张原图来源与SHA-256见[原图清单](images/compact-ui-075/manifest.json)。执行批次、测试APK差别和真机限制见[联合交接](HANDOFF_COMPACT_UI.md)。

两主题的50个指定页面场景已逐项取得通过；字体与其他页面的测试包按执行清单分别记录，正式应用APK始终是同一份c994。系统实际320dp／字体2.0、跨时区、缺失维度及Activity重建证据另外保留，不把普通字号图当成大字体图。

## 浅色

| 页面 | 改前 | 改后 | 对照范围 |
|---|---|---|---|
| 关于应用 | <a href="images/compact-ui-075/about-before-light.png"><img src="images/compact-ui-075/about-before-light.png" width="220" alt="关于应用before" /></a> | <a href="images/compact-ui-075/about-after-light.png"><img src="images/compact-ui-075/about-after-light.png" width="220" alt="关于应用after" /></a> | 同一实际 AboutSettings 组合；最终图显示实际安装02/code75，并保留当时“查询中”状态。 |
| 字体两行与重置 | <a href="images/compact-ui-075/fonts-before-light.png"><img src="images/compact-ui-075/fonts-before-light.png" width="220" alt="字体两行与重置before" /></a> | <a href="images/compact-ui-075/fonts-after-light.png"><img src="images/compact-ui-075/fonts-after-light.png" width="220" alt="字体两行与重置after" /></a> | 同一英文标准字号、应用默认字体及两次重置操作。 |
| 只读模型单价 | <a href="images/compact-ui-075/pricing-before-light.png"><img src="images/compact-ui-075/pricing-before-light.png" width="220" alt="只读模型单价before" /></a> | <a href="images/compact-ui-075/pricing-after-light.png"><img src="images/compact-ui-075/pricing-after-light.png" width="220" alt="只读模型单价after" /></a> | 同一 demo-model：输入0、输出1.25、1小时缓存写入2.5；旧独立弹窗改为页内详情。 |
| 订阅去重 | <a href="images/compact-ui-075/subscriptions-before-light.png"><img src="images/compact-ui-075/subscriptions-before-light.png" width="220" alt="订阅去重before" /></a> | <a href="images/compact-ui-075/subscriptions-after-light.png"><img src="images/compact-ui-075/subscriptions-after-light.png" width="220" alt="订阅去重after" /></a> | 同一三套餐与月折算30 fixture；旧重复入口改成直接内容，刷新与详情保留。 |
| 额度选项 | <a href="images/compact-ui-075/quota-before-light.png"><img src="images/compact-ui-075/quota-before-light.png" width="220" alt="额度选项before" /></a> | <a href="images/compact-ui-075/quota-after-light.png"><img src="images/compact-ui-075/quota-after-light.png" width="220" alt="额度选项after" /></a> | 同一合成账户及72剩余额度；14dp视觉复选框保留至少48dp行触控。 |
| 邮箱遮罩说明分组 | <a href="images/compact-ui-075/privacy-before-light.png"><img src="images/compact-ui-075/privacy-before-light.png" width="220" alt="邮箱遮罩说明分组before" /></a> | <a href="images/compact-ui-075/privacy-after-light.png"><img src="images/compact-ui-075/privacy-after-light.png" width="220" alt="邮箱遮罩说明分组after" /></a> | 同一隐私开关与合成账户的结果图；标题／说明与下一组间距另见下方完整组件原图。 |
| 手机／Hub 两列 | <a href="images/compact-ui-075/devices-before-light.png"><img src="images/compact-ui-075/devices-before-light.png" width="220" alt="手机／Hub 两列before" /></a> | <a href="images/compact-ui-075/devices-after-light.png"><img src="images/compact-ui-075/devices-after-light.png" width="220" alt="手机／Hub 两列after" /></a> | 同一393dp设备fixture与顺序；本机仍只读，模拟器型号不伪装成真实小米。 |
| 每日记录选择 | <a href="images/compact-ui-075/calendar-before-light.png"><img src="images/compact-ui-075/calendar-before-light.png" width="220" alt="每日记录选择before" /></a> | <a href="images/compact-ui-075/calendar-after-light.png"><img src="images/compact-ui-075/calendar-after-light.png" width="220" alt="每日记录选择after" /></a> | 结构对照：旧年度活动选择与新月视图使用不同固定公开数据，不比较其数值或宣称同数据像素回归。 |

## 深色

| 页面 | 改前 | 改后 | 对照范围 |
|---|---|---|---|
| 关于应用 | <a href="images/compact-ui-075/about-before-dark.png"><img src="images/compact-ui-075/about-before-dark.png" width="220" alt="关于应用before" /></a> | <a href="images/compact-ui-075/about-after-dark.png"><img src="images/compact-ui-075/about-after-dark.png" width="220" alt="关于应用after" /></a> | 同一实际 AboutSettings 组合；最终图显示实际安装02/code75，并保留当时“查询中”状态。 |
| 字体两行与重置 | <a href="images/compact-ui-075/fonts-before-dark.png"><img src="images/compact-ui-075/fonts-before-dark.png" width="220" alt="字体两行与重置before" /></a> | <a href="images/compact-ui-075/fonts-after-dark.png"><img src="images/compact-ui-075/fonts-after-dark.png" width="220" alt="字体两行与重置after" /></a> | 同一英文标准字号、应用默认字体及两次重置操作。 |
| 只读模型单价 | <a href="images/compact-ui-075/pricing-before-dark.png"><img src="images/compact-ui-075/pricing-before-dark.png" width="220" alt="只读模型单价before" /></a> | <a href="images/compact-ui-075/pricing-after-dark.png"><img src="images/compact-ui-075/pricing-after-dark.png" width="220" alt="只读模型单价after" /></a> | 同一 demo-model：输入0、输出1.25、1小时缓存写入2.5；旧独立弹窗改为页内详情。 |
| 订阅去重 | <a href="images/compact-ui-075/subscriptions-before-dark.png"><img src="images/compact-ui-075/subscriptions-before-dark.png" width="220" alt="订阅去重before" /></a> | <a href="images/compact-ui-075/subscriptions-after-dark.png"><img src="images/compact-ui-075/subscriptions-after-dark.png" width="220" alt="订阅去重after" /></a> | 同一三套餐与月折算30 fixture；旧重复入口改成直接内容，刷新与详情保留。 |
| 额度选项 | <a href="images/compact-ui-075/quota-before-dark.png"><img src="images/compact-ui-075/quota-before-dark.png" width="220" alt="额度选项before" /></a> | <a href="images/compact-ui-075/quota-after-dark.png"><img src="images/compact-ui-075/quota-after-dark.png" width="220" alt="额度选项after" /></a> | 同一合成账户及72剩余额度；14dp视觉复选框保留至少48dp行触控。 |
| 邮箱遮罩说明分组 | <a href="images/compact-ui-075/privacy-before-dark.png"><img src="images/compact-ui-075/privacy-before-dark.png" width="220" alt="邮箱遮罩说明分组before" /></a> | <a href="images/compact-ui-075/privacy-after-dark.png"><img src="images/compact-ui-075/privacy-after-dark.png" width="220" alt="邮箱遮罩说明分组after" /></a> | 同一隐私开关与合成账户的结果图；标题／说明与下一组间距另见下方完整组件原图。 |
| 手机／Hub 两列 | <a href="images/compact-ui-075/devices-before-dark.png"><img src="images/compact-ui-075/devices-before-dark.png" width="220" alt="手机／Hub 两列before" /></a> | <a href="images/compact-ui-075/devices-after-dark.png"><img src="images/compact-ui-075/devices-after-dark.png" width="220" alt="手机／Hub 两列after" /></a> | 同一393dp设备fixture与顺序；本机仍只读，模拟器型号不伪装成真实小米。 |
| 每日记录选择 | <a href="images/compact-ui-075/calendar-before-dark.png"><img src="images/compact-ui-075/calendar-before-dark.png" width="220" alt="每日记录选择before" /></a> | <a href="images/compact-ui-075/calendar-after-dark.png"><img src="images/compact-ui-075/calendar-after-dark.png" width="220" alt="每日记录选择after" /></a> | 结构对照：旧年度活动选择与新月视图使用不同固定公开数据，不比较其数值或宣称同数据像素回归。 |

## 每月：12个月份网格

现有改前基线没有单独的“每月列表”原图，不以每日活动图冒充。以下为最终APK的年份／月份网格与完整选中明细；月份数值直接来自Hub monthly记录。

| 浅色 | 深色 |
|---|---|
| <a href="images/compact-ui-075/monthly-after-light.png"><img src="images/compact-ui-075/monthly-after-light.png" width="240" alt="每月light" /></a> | <a href="images/compact-ui-075/monthly-after-dark.png"><img src="images/compact-ui-075/monthly-after-dark.png" width="240" alt="每月dark" /></a> |

## 证据边界

关于完整图展示的是默认真实查询源在捕获瞬间的加载状态，不把它冒称注入的成功结果；关于位置、当前安装版本链接、错误与限流状态另有行为断言，真实上游匿名客户端另行验收。日历前后fixture不同已在表中标明。真实手机触摸、键盘、覆盖安装、系统／厂商后台及实际Hub网络仍需用户设备确认。

原桌面样式依据实际安装的桌面0.72.0 renderer及有限实际界面观察；REA语义分析覆盖不完整，后续桌面深层像素操作受锁屏限制，未声称已执行桌面价格编辑／保存。Android功能参考基线保持0.71.0，本次单价按用户明确确认保持只读。

## 邮箱说明与下一组的完整组件证据

这两张是最终APK的320dp公开组件fixture，明确展示邮箱标题／说明及下一个额度条分组；它们不是与旧完整页面同数据的像素对比。原图保留全部视口和触控反馈。

| 浅色 | 深色 |
|---|---|
| <a href="images/compact-ui-075/privacy-component-after-light.png"><img src="images/compact-ui-075/privacy-component-after-light.png" width="220" alt="邮箱说明浅色" /></a> | <a href="images/compact-ui-075/privacy-component-after-dark.png"><img src="images/compact-ui-075/privacy-component-after-dark.png" width="220" alt="邮箱说明深色" /></a> |
