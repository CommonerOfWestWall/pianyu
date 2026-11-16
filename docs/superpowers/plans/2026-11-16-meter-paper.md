# 测光与冲印分层 Implementation Plan

**Goal:** 用同输入的官方上游/片屿实际输出决定控制分层；首页保持七个推荐搭配，在手动调节增加可解释、可复现的测光与冲印选择。

**Architecture:** 不改胶片材料模型、不以成片曝光伪造印放。新字段通过已有 JSON 设置、渲染缓存、历史、配方和导出路径贯通；默认值与旧编辑兼容。官方参考版本独立固定 SHA 构建，不覆盖片屿引擎。

**Tech Stack:** Kotlin / Jetpack Compose / 既有 C ABI 引擎；Python ctypes 同图实验；Gradle 单测、lint、APK、AndroidTest；有设备才执行真机测试。

## 1. 实验与视觉验收
- [x] 单个 luna_worker 在 `../revision-v032/exposure-paper-study/` 固定官方当前源码并构建；与当前片屿模型/确切APK资源分开。
- [x] 六类输入长边640，比较五种测光、胶片EV、相纸/拷贝片和印放EV；记录参数、resolved值、图像散列与误差，部分代表960复核。
- [x] 主线程查看接触表和代表原图，依据明暗、肤色、颜色分离及场景稳定性决定保留/合并；误差不能称真实胶片准确率。

## 2. 最小参数贯通
Files: `EditSettings.kt`, `LibraryStore.kt`, `Look.kt`, `NativeCore.kt`, `EditorModel.kt`。
- [x] 增加 `printStock: String = ""`（跟随风格推荐）和 `printExposure: Float = 0f`（EV）；胶片正片扫描忽略印放控制。
- [x] 保留 `autoFilmExposure` 与方法字符串的旧存储形式；允许四种新测光和旧 `center_weighted`，不重解读旧配方。
- [x] `Look.params` 把相纸覆盖限制为兼容材料，`print_exposure = 2^printExposure`；原默认仍为1，不关闭既有归一化。
- [x] 所有设置参与重新渲染、缓存、撤销、缩略图、单张/批量导出；跨风格选材须使用兼容回退且UI明确显示实际生效材料。

## 3. 交互
Files: `AdjustmentPanel.kt`, 新建 `FilmPrintPanel.kt`。
- [x] 原胶片曝光入口保留；自动测光开关继续可用，新增可见的测光意图选择。手动EV独立，不把切模式等同增加饱和度。
- [x] 手动页增加“相纸与印放”：推荐搭配为默认，可选经实验确认有用的材料；印放曝光与成片曝光分别解释。
- [x] 正片直接扫描显示“不经过相纸”，禁用不生效选项，不制造假切换；可以恢复当前推荐搭配。
- [x] 首页面七种风格不扩成胶片×相纸的重复入口；不在没有证据时删除旧风格。

## 4. 验证与交付
Files: 定向host/device tests、`README.md`、`docs/USER-GUIDE.md`、新研究报告。
- [x] 先补有意义的参数兼容、JSON往返、旧编辑迁移、正片旁路与模式状态检查，再运行最小相关检查。
- [x] `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease`。
- [x] 有连接设备则跑定向设备测试与UI截图；无设备明确未验收内容，不把桌面结果冒充真机。
- [x] 版本号新增为测试候选，不改既有发布；输出APK、同图对照和结论；不自动push或发布。

授权：用户已要求研究、实践并落实简单首页/手动可选控制，按计划继续，不再请求重复确认。设备验收明确待办，不作为已完成真机测试。

## 实施记录
- 150张受控输出、12张稳定版基线及960代表复核完成；当前上游Windows/Vulkan方向耦合异常的初轮结果作废。受控实验两边关闭方向耦合，另保留旧稳定版开启方向耦合的基线。
- 实验与产品结论见 `docs/METERING-PRINT-STUDY-v13.md`；证据已去除本机路径，包含640输入副本、参数、散列及对照图。
- 12项host tests、Debug/Release、AndroidTest编译、lint通过；最终包versionCode13 / 0.3.3-preview，签名与本地v12相同，核心预构建库散列保持不变。
- 本轮ADB无设备，也无可用模拟器：4项新增设备测试及UI、导出验收**未执行**，不属于已完成的测试。
- APK已复制至工作区 outputs/独立胶片应用/v0.3.3-preview/；未提交、push或公开发布。

2026-11-16补验：后续v14已执行4项FilmPrintDeviceTest，原生相纸/印放变化及正片旁路通过。最终R8 Release测光四模式、开关、两种相纸、印放+0.4EV/恢复推荐、自然正片直接扫描UI通过，并完成原尺寸JPEG/HEIF及批量导出。版本0.3.3-preview2已装到手机，安装包位于outputs/独立胶片应用/v0.3.3-preview2/；详见 `docs/BATCH-PIPELINE-v14.md`。之前的无设备记录是v13首次候选的历史状态。
