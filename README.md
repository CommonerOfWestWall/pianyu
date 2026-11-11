# 片屿 Pianyu 0.3.2

片屿是一款面向 Android 手机 JPG / HEIF 的本地胶片照片编辑器：在设备上完成选片、胶片风格、基础调色、构图和导出，不上传照片。

![片屿标记](docs/images/icon.png)

> 0.3.2（versionCode 12）公开预发布版：Android 10+、arm64。新照片默认先做胶片前自动测光、推荐强度75，100提供更强的创作余量；旧编辑保留原效果。依据、上游源码与实测方法见[默认工作流](docs/DEFAULT-WORKFLOW-v11.md)。v10修复Android误执行桌面大缓冲实验后，用户已反馈OPPO恢复正常；本地最终真机验证使用Redmi K80 Pro，其他设备仍需测试。

v12新增保留构图、可撤销的“重置此风格调色”，以及不经JPEG中间文件的HEIF导出（当前为8-bit sRGB SDR）。使用方法与实际验证见[推荐起点与HEIF](docs/HEIF-AND-RECOMMENDED-v12.md)。

## 下载

0.3.2 是公开预发布版：[Release 页面](https://github.com/CommonerOfWestWall/pianyu/releases/tag/v0.3.2)。直接下载：[pianyu-0.3.2-v12-arm64.apk](https://github.com/CommonerOfWestWall/pianyu/releases/download/v0.3.2/pianyu-0.3.2-v12-arm64.apk)；源码包：[pianyu-0.3.2-v12-source.zip](https://github.com/CommonerOfWestWall/pianyu/releases/download/v0.3.2/pianyu-0.3.2-v12-source.zip)。Release 构建使用既有 Android Debug 开发测试签名，适合开发和测试；不能承诺该签名会用于长期升级。

本版功能与验证范围见[发布说明](docs/RELEASE-v0.3.2.md)。七风格对照、白马累计微调与[第三版介绍片](docs/PROMO-V3-AUDIT.md)保留为早期候选的研究记录，不能当作v12默认效果；视频没有作为本次发布附件。AI生成输入与Android实际输出分别标注，历史胶片参考为异场景风格对照，不是严格色彩标定。默认风格保留上游材料模型，片屿额外调色可在风格说明关闭。方法和局限见[胶片性格复核](docs/FILM-CHARACTER-AUDIT.md)。

## 名字与视觉

“片”是照片，也是生活里的片刻；“屿”是一小块值得停留的日常。应用里的岛岸、日光与一片倒影，分别构成品牌标记的三层意象：岸线承接照片，日光提供方向，倒影留下片刻。它们不是滤镜名称，也不暗示某个真实胶片的唯一颜色。

## 已实现功能

- 从系统相册导入 JPG / HEIF；一次最多选择 100 张，私有照片库保存访问 URI 和编辑设置，不复制原片。
- 7 款风格入口：日常暖色、电影日光、夜色光晕、自然正片、浓彩风光、克制复古、柔和黑白。
- 风格强度、曝光、亮度、对比、饱和、高光、阴影、色温、色调、褪色、颗粒、锐化和暗角；HSL 分色支持红、橙、黄、绿、青、蓝、紫、洋红八个色区。
- 胶片曝光在胶片响应前以±2EV调节，直接使用上游模型；原曝光保留成片后的提亮作用。旧配方胶片曝光默认为0。已实际运行两个上游与片屿模型的同输入对照，见 [实验结果和边界](docs/UPSTREAM-PRACTICE-2026-11.md)。
- 新照片默认开启保护高光自动测光，在“调色 → 胶片曝光”可直接关闭；推荐强度75与旧版推荐100等效（关闭测光比较），100进一步加强材料混合和额外预设。75不是准确率。旧配方不自动重新曝光。
- 风格强度、基础调色和 HSL 数值点按后可直接输入；曝光显示两位小数，其他参数使用 UI 百分数，并按各自范围校验后应用。未改动输入时保留原始浮点值。
- 裁切、移动裁切框、自由或锁定比例、左右 90° 旋转、水平/垂直镜像和 ±20° 校平。
- 撤销/重做、原图对比、长按对比、双指缩放；预览完成后可恢复全图。
- 复制/套用调色配方；命名配方可保存、应用、改名和删除，配方不包含几何构图。
- 单张保存或分享；照片库多选后批量保存，显示进度和失败数，并可停止后续任务。编辑后可分享 JPEG、PNG 或设备支持时的 HEIF；另可直接分享原片，原片按源字节复制，保留原始元数据且不含编辑。
- 默认按原尺寸导出 8-bit sRGB JPEG（质量 100，高质量但仍有损），也可选择长边 1080 或 2048；另可导出编辑后的 8-bit sRGB PNG，或设备支持时的 8-bit sRGB SDR HEIF。HEIF 使用 AndroidX HeifWriter 1.1.0 和设备 HEVC/HEIC 编码器，不经过 JPEG 中转。不放大更小原图，写入 `Pictures/片屿`，编辑后导出不保留定位/EXIF 等元数据。
- 编辑预览长边约 960，预览尺寸不代表全分辨率输出。颗粒 UI 的 0–100 映射到引擎 0–2，拖动停顿约 120ms 后预览，松手提交；双指放大可观察颗粒，颗粒随风格强度变化。

## 七款风格

风格入口由 `app/src/main/java/studio/pianyu/app/Look.kt` 定义。参考材料只表示模型来源和审美方向，不是品牌合作、官方复刻或颜色保证。

| 入口 | 参考 | 说明 |
| --- | --- | --- |
| 日常暖色 | Kodak Portra 400 | Portra 400 负片与 Kodak Endura 相纸模型；偏暖、肤色柔和，适合日常和人物。 |
| 电影日光 | Kodak Vision3 250D | Vision3 250D 与 Kodak 2383 电影正片模型；适合街景、建筑和有方向的光。 |
| 夜色光晕 | CineStill 800T | 以 Vision3 500T 为基础的 800T 衍生模型与 2383，非独立测量的 800T；少量光晕，适合霓虹、灯光和夜景。 |
| 自然正片 | Fujifilm Provia 100F | 反转片模型，色彩相对清爽平衡，适合自然光和旅行。 |
| 浓彩风光 | Fujifilm Velvia 100 | 反转片模型，色彩更浓、对比更强，适合植物和风光。 |
| 克制复古 | Kodak Kodachrome 64 | 正片材料模型，保留克制的复古色彩，偏向街景和日常物件。 |
| 柔和黑白 | Fujifilm Neopan Acros 100 II | 黑白负片与 Ilford Multigrade IV RC 相纸模型；以灰阶、纹理和明暗关系为主。 |

![HSL 调整](docs/images/hsl.png)

<img src="docs/images/editor-v12.png" alt="v12 编辑界面" width="300"> <img src="docs/images/export-heif-v12.png" alt="v12 HEIF 导出界面" width="300">

上面两图是 v12 在 Redmi K80 Pro 上的真机界面，照片为 AI 生成的内置示例，不是私人照片。旧 0.3 控件图仍保留作历史界面记录；未用合成照片替换截图里的照片来伪造运行结果。

## 使用流程

1. 在照片库点“导入”，从系统相册选择照片；点一张进入编辑，长按可多选。
2. 先选风格，从推荐75开始；需要更鲜明的效果可提高到100。胶片曝光可补偿或关闭自动测光。“风格说明”列出参考材料，可关闭片屿额外预设，自行从胶片模型开始调色；重新选择风格会恢复推荐强度和预设。
3. 需要构图时打开“裁切 / 旋转”，完成后提交；点“取消”放弃本次构图。
4. 调好一张后点“复制配方”，回到照片库多选并“套用配方”。也可以在“配方”页保存个人配方。
5. 点保存或分享；多选照片时可批量保存。导出期间请保持应用打开，进程被系统杀死后不会自动续传。

![裁切](docs/images/crop.png)

![配方](docs/images/recipes.png)

## 构建

源码包直接克隆后可用 Gradle 构建 Android APK。环境要求：JDK 17、Android SDK 36、NDK 28.2.13676358、CMake 3.22.1、Gradle 9.5.0；依赖版本见 `app/build.gradle.kts`。

```powershell
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Windows 工程路径需为 ASCII；若实际目录包含非 ASCII 字符，可用 `subst` 映射到临时盘符后执行 Gradle。Linux/macOS 使用 `./gradlew`，需要能下载 Gradle/Maven 依赖。

应用构建默认使用源码包内已验证的 `app/src/main/jniLibs/arm64-v8a/libpianyu_core.so` 输入。要重建胶片核心，请先准备 NDK、CMake/Ninja、glslang 和 Vulkan Headers，再运行 [`docs/build-engine.ps1`](docs/build-engine.ps1)。该脚本在解压包根目录 staging，应用 Android unfused-FMA overlay，生成 Release arm64 核心并更新资源；它不依赖原开发工作区的相对路径。完整说明见 [`docs/SOURCE-DISTRIBUTION.md`](docs/SOURCE-DISTRIBUTION.md)。

## 隐私与数据

片屿无账户、广告、网络权限和照片上传。原图不会复制或备份；应用只保存可重新访问的 URI、每张照片的编辑设置和应用私有的 `recipes-v1.json`。删除原图、撤销相册访问或清除应用数据后，需要重新导入。导出 JPEG 不保留定位、EXIF 等元数据。

## 已验证与限制

v12在Redmi K80 Pro完成最终Release APK再安装验证，确认最终UI、HEIF原尺寸保存与分享；7项host tests、21项定向 Android tests、Release 构建、AndroidTest 编译和 lint（0 errors、32 warnings、1 hint）通过。JPG和HEIF原尺寸处理及PNG保存/读取通过。之前版本的宣传视频基于 versionCode 7，不代表v12默认效果。50MP、HDR和尚未覆盖的其他设备编码器仍需单独验证。

- 当前范围不是 VSCO 的全功能替代品；没有透视、曲线、蒙版、相机/视频、RAW、专业色彩管理、HDR 保真导出或后台批处理服务。
- 本地GPU兼容性在Redmi K80 Pro / Adreno 830验证；OPPO Find X9有用户正常使用反馈。没有CPU后备渲染，不能保证所有GPU兼容。
- HEIF SDR 解码测试不能代表所有 HDR 或 gain-map 来源。
- 公开版演示输入为 AI 生成的手机摄影风格 PNG 加内置 stilllife，不是实拍或真实人物背书。参考、AI输入与实际 Android 输出分别标注；编辑结果未经额外调色，不用于证明真实胶片定标。界面录制使用公开内置示例；测试输入只随 AndroidTest 包提供。
- Release APK 是 debug 签名测试版；公开长期分发需要独立管理正式签名和升级渠道。AndroidTest 中的 AI 输入只随测试包提供，不进入主 APK。

## 来源与分层许可

- 应用、Android 界面和本项目修改代码：GPL-3.0-or-later，见 [`license/LICENSE`](license/LICENSE) 和根目录 [`NOTICE.md`](NOTICE.md)。
- [SpektraLab](https://github.com/JamesQiu2005/SpektraLab) 固定上游提交：[`d90cf9a`](https://github.com/JamesQiu2005/SpektraLab/commit/d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289)。引擎还包含 Windows/Vulkan 局部修改和 Android overlay，差异随源码包提供。
- 上游截至 2026-11-10 的 3 个提交、引擎未变更结论和商标边界见 [`docs/UPSTREAM-REVIEW-2026-11.md`](docs/UPSTREAM-REVIEW-2026-11.md)。
- spektrafilm profiles、LUT 及直接派生数据：CC BY-SA 4.0，见 [`license/SPEKTRAFILM_LICENSE.txt`](license/SPEKTRAFILM_LICENSE.txt)；该文件及 `license/NOTICE.md` 保留原文。
- Material Icons 为 Apache-2.0；Noto Serif SC 子集为 SIL OFL 1.1；Vulkan-Headers、volk 及其他第三方组件按各自目录许可证提供。来源记录见 [`asset-provenance/`](asset-provenance/)。

请先阅读各目录中的许可证和 NOTICE，再重新分发或修改相应内容。

## 致谢

感谢 Andrea Volpato 的 [spektrafilm](https://github.com/andreavolpato/spektrafilm)、[SpektraLab](https://github.com/JamesQiu2005/SpektraLab) 上游工程、Vulkan/volk、Google Material Icons 和 Noto Serif SC。片屿没有沿用网友安卓版的 UI 或二进制核心。

## 参与贡献

请先阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)。欢迎性能分析、无障碍改进、设备兼容性报告和可复现构建修复；请保留来源记录、测试输入和许可证边界，避免新增仅靠名称区分的相似风格入口。

白马样片在追加比较 19 个候选后，改用自然正片重新输出 4 个累计阶段；[重调前后对比与完整参数](docs/film-study/PRAIRIE-REVISION.md) 保留旧版，视频已同步更新。
