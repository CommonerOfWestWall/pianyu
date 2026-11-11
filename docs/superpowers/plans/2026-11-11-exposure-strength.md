# 默认测光与风格余量实现计划

> **For agentic workers:** Use executing-plans to implement the checklist; one read-only worker researches upstream, root owns all application changes and device verification.

**Goal:** 学习上游曝光工作流，让新照片默认有可关闭的胶片前自动测光，并把推荐风格移至75，100提供实际创作余量；保留旧编辑效果。

**Architecture:** 复用现有Vulkan材料引擎的auto_exposure参数，不用成片提亮伪装测光。新增持久化开关与测光方式，新照片选protect_highlights；预览/缩略图/导出/配方/撤销共用，旧JSON缺开关为false，开关true但缺方法保留center_weighted。新lookVersion3将strength/.75映射至原推荐配方，材料混合最多1、额外finish可到4/3。旧v2只在UI显示为原数值*.75，移动滑块或输入强度才转v3，避免偷偷改旧配方。

**Tech Stack:** Kotlin/Compose、现有C++/Vulkan C ABI、JUnit、Android instrumentation、ADB；不新增依赖或替换核心。

## 文件与步骤

- [x] 对照SpektraLab与spektrafilm当前官方源码，记录曝光默认、测光位置和算法；实际运行auto on/off样片后决定默认测光方法，不以固定正EV替代。
- [x] `EditSettings.kt`、`LookFinish.kt`：新增autoFilmExposure、新默认75/version3、v2显示映射；主机测试验证75=旧推荐、100实际增强、旧版本效果保持、0不加风格。
- [x] `Look.kt`、`NativeCore.kt`：显式透传自动测光；`EditorModel.kt`缓存、失效与所有预览/导出调用一致；`LibraryStore.kt`/配方保持新开关与方法，旧JSON为false。
- [x] `AdjustmentPanel.kt`添加自动测光开关与胶片曝光说明；`EditorScreen.kt`标明75推荐/100加强，说明这是风格量而非准确率。保留关闭额外预设的材料模型模式，强度可点按输入。
- [x] 更新debug样片管线和Android测试；真机运行旧配方兼容、自动测光开关/补偿、撤销/配方、0/75/100、预览导出与图库回归。
- [x] 在红米上渲染人物/风光/夜景/演出等同输入对照并视觉检查，记录参数、来源与输出，发现问题后再调整。
- [x] 构建Debug/Release/androidTest、主机测试、lint、签名与包源码核对；真机实际操作新UI并留图。
- [x] 输出v11 APK、GPL对应源码、110张实际Android输出与验证清单，核对打包结果；分发脚本对版本、签名、测试结果、资源匹配和110张输出哈希设断言。

## 实测结果

2026-11-11：7项主机测试、17项真机测试通过。五场景×七风格×三设置加输入共110张PNG已逐一核对哈希，并视觉检查五张总表。JPG和HEIF在2304×3456开启新默认测光后完成原尺寸PNG输出与逐像素读取验证。压缩Release的公开示例、预设开关、胶片前EV、HSL、自由裁切/旋转、原尺寸保存及分享入口实测通过。lint为0错误、32警告、1提示。

初次回归中的关闭额外预设断言仍假设强度不变，已改为验证保留实际材料混合量；UI自动化的Compose语义子节点无法直接setText，改用实际键盘输入并验证可编辑节点值。最终整组17项再次通过。没有为测试移除实际行为或放宽输出断言。

## 验收边界

用户已确认OPPO v10正常，记为用户反馈，不能冒充本地OPPO测试。当前连接红米K80 Pro，允许占用片屿测试；只使用公开/测试素材，不读取私人照片，不清除主应用数据。原尺寸导出不变。自动测光是可撤销/关闭的起点，不宣称所有夜景都更好或JPEG获得RAW高光余量。
