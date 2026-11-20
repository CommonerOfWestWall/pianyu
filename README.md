# 片屿 Pianyu

片屿是一款在手机上运行的 Android 胶片调色工具。照片、手机 DNG、视频和 JPEG 实况照片共用 20 款主风格与 4 款同系变体。可按日光、灯光、冷暖或四季找风格，再调胶片曝光、相纸与印放。常用搭配可以保存成配方，并批量套用到照片。

![片屿标记](docs/images/icon.png)

## 下载

**最新版本 0.3.12**。适用于 Android 10 及以上的 arm64 设备，胶片引擎使用 Vulkan。沿用 GitHub 旧版签名，可覆盖安装同签名旧版。

[下载安装包](https://github.com/CommonerOfWestWall/pianyu/releases/download/v0.3.12/pianyu-0.3.12-arm64.apk) · [GitHub 发布页](https://github.com/CommonerOfWestWall/pianyu/releases/tag/v0.3.12) · [本版更新与实测范围](docs/RELEASE-v0.3.12.md) · [使用指南](docs/USER-GUIDE.md)


## 名字与视觉

“片”是照片，也是生活里的片刻；“屿”是一小块值得停留的日常。应用里的岛岸、日光与一片倒影，分别构成品牌标记的三层意象：岸线承接照片，日光提供方向，倒影留下片刻。它们不是滤镜名称，也不暗示某个真实胶片的唯一颜色。

## 已实现功能

- 从系统相册导入 JPG / HEIF；一次最多选择 100 张，私有照片库保存访问 URI 和编辑设置，不复制原片。
- 照片、视频和 JPEG 实况都可选择胶片风格，在相应编辑页继续调整。
- 风格强度、曝光、亮度、对比、饱和、高光、阴影、色温、色调、褪色、颗粒、锐化和暗角；HSL 分色支持红、橙、黄、绿、青、蓝、紫、洋红八个色区。
- 胶片曝光在胶片响应前以±2EV调节，直接使用上游模型；原曝光保留成片后的提亮作用。旧配方胶片曝光默认为0。已实际运行两个上游与片屿模型的同输入对照，见 [实验结果和边界](docs/UPSTREAM-PRACTICE-2026-11.md)。
- 新照片默认开启保护高光自动测光，在“调色 → 胶片曝光”可直接关闭；v13可手动选择均衡、中心、保护高光或保护阴影。推荐强度75与旧版推荐100等效（关闭测光比较），100进一步加强材料混合和额外预设。75不是准确率。旧配方不自动重新曝光。
- v13“调色 → 相纸与印放”保留推荐搭配，可更换兼容相纸/电影拷贝片，并独立调整±2EV印放曝光；支持撤销、配方保存和批量导出。正片直接扫描绕过相纸。选择另一风格会恢复该风格推荐冲印，其余手动调色保留。
- 风格强度、基础调色和 HSL 数值点按后可直接输入；曝光显示两位小数，其他参数使用 UI 百分数，并按各自范围校验后应用。未改动输入时保留原始浮点值。
- 裁切、移动裁切框、自由或锁定比例、左右 90° 旋转、水平/垂直镜像和 ±20° 校平。
- 撤销/重做、原图对比、长按对比、双指缩放；预览完成后可恢复全图。
- 复制/套用调色配方；命名配方可保存、应用、改名和删除，配方不包含几何构图。
- 单张保存或分享；照片库多选后批量保存，显示进度、结果和逐张失败原因，可以仅重试失败项或停止后续任务。编辑后可分享 JPEG、PNG 或设备支持时的 HEIF；另可直接分享原片，原片按源字节复制，保留原始元数据且不含编辑。
- 默认按原尺寸导出 8-bit sRGB JPEG（质量 100，高质量但仍有损），也可选择长边 1080 或 2048；另可导出编辑后的 8-bit sRGB PNG，或设备支持时的 8-bit sRGB SDR HEIF。HEIF 使用 AndroidX HeifWriter 1.1.0 和设备 HEVC/HEIC 编码器，不经过 JPEG 中转。不放大更小原图，写入 `Pictures/片屿`，编辑后导出不保留定位/EXIF 等元数据。
- DNG 在胶片模拟前由 LibRaw 提供高精度线性输入；普通视频输出 SDR MP4 并保留音频，编辑页展示抽帧预览。带内嵌视频的 JPEG 实况照片可同步处理封面与视频，也可单独另存 MP4；品牌私有封装和真实手机覆盖范围尚未广泛测试。
- 编辑预览长边约 960，预览尺寸不代表全分辨率输出。颗粒 UI 的 0–100 映射到引擎 0–2，拖动停顿约 120ms 后预览，松手提交；双指放大可观察颗粒，颗粒随风格强度变化。

## 风格

0.3.12 提供 20 款主风格和 4 款同系变体，照片、视频和 JPEG 实况照片共用同一目录。点「风格说明」可以查看参考来源、胶片与色彩特点，以及适用的环境和场景。表中的胶片和相纸是调色模型的参考材料。

滑动风格面板顶部的场景条，点冷暖、光源或季节标签，将匹配的主风格排到前面。季节按所选季节起循环排序，点「全部」恢复默认。排序保留当前风格和调色。

| 入口 | 参考 | 说明 |
| --- | --- | --- |
| 电影日光 | Kodak Vision3 250D | Vision3 250D 与 Kodak 2383 电影正片模型；适合街景、建筑和有方向的光。 |
| 日常暖色 | Kodak Portra 400 | Portra 400 负片与 Kodak Endura 相纸模型；偏暖、肤色柔和，适合日常和人物。 |
| 柔光人像 | Kodak Portra 400 | 柔和肤色与高光过渡，适合人物和日常光线。 |
| 夜色光晕 | CineStill 800T | 以 Vision3 500T 为基础的 800T 衍生模型与 2383，非独立测量的 800T；少量光晕，适合霓虹、灯光和夜景。 |
| 红蓝复古 | Kodak Kodachrome 64 | 正片材料模型，保留克制的复古色彩，偏向街景和日常物件。 |
| 暖金冷蓝 | Fujifilm Velvia 100 | 暖金高光与冷蓝阴影，适合风光和植物。 |
| 蓝紫暮色 | Fujifilm Provia 100F | 蓝紫暮色与克制反差，适合傍晚和城市远景。 |
| 灰青低彩 | Kodak Portra 400 | 灰青低饱和与柔和反差，适合阴天和安静场景。 |
| 浅蓝淡彩 | Fujifilm Provia 100F | 清淡的蓝青色调与柔和反差，适合自然光和海岸场景。 |
| 深青水色 | Fujifilm Provia 100F | 深青与低饱和水色，适合旅行、天空和水面。 |
| 自然正片 | Fujifilm Provia 100F | 反转片模型，色彩相对清爽平衡，适合自然光和旅行。 |
| 深绿林地 | Fujifilm Provia 100F | 压低亮绿、保留深绿层次，适合树林和阴影。 |
| 浓彩风光 | Fujifilm Velvia 100 | 反转片模型，色彩更浓、对比更强，适合植物和风光。 |
| 柔和黑白 | Fujifilm Neopan Acros 100 II | 黑白负片与 Ilford Multigrade IV RC 相纸模型；以灰阶、纹理和明暗关系为主。 |
| 暖粉淡彩 | Kodak Portra 400 | 微暖带粉，提亮肤色与浅色。 |
| 深秋暖雾 | Fujifilm Provia 100F | 铜红、金棕和亮白分离，暗部更深。 |
| 青蓝亮调 | Kodak Vision3 500T | 青蓝更清晰，中间调明亮。 |
| 电影暖亮 | Kodak Vision3 250D | 暖色自然提亮，保留柔和亮部。 |
| 电影冷影 | Kodak Vision3 250D | 青蓝更深，明暗反差清晰。 |
| 电影暖影 | Kodak Vision3 500T | 琥珀与棕橙更明显，保留深影。 |

选中带变体的主风格后，可在缩略图下方的「变体」一行直接切换。切换风格保留画面曝光、胶片曝光、HSL 等调整，强度和冲印恢复新风格的推荐值。旧照片和已保存的配方继续按原参数处理。

| 同系变体 | 关联主风格 | 色彩特点 |
| --- | --- | --- |
| 浅绿柔光 | 青蓝亮调 | 浅绿叶色与柔和亮部。 |
| 冷灰薄雾 | 青蓝亮调 | 降低彩度，突出薄雾中的冷灰与青绿。 |
| 橄榄暖影 | 电影日光 | 黄绿向橄榄色偏移，砖墙与林地暗部更深。 |
| 金色日照 | 电影暖亮 | 突出金黄日照并保留蓝色。 |

![HSL 调整](docs/images/hsl.png)

<img src="docs/images/look-guide-v0.3.12.webp" alt="片屿 0.3.12 照片风格说明" width="300"> <img src="docs/images/video-editor-v0.3.12.webp" alt="片屿 0.3.12 视频调色" width="300">

两张截图均来自 Redmi K80 Pro 上运行的 0.3.12，使用应用内置示例。

## 使用流程

1. 在照片库点「导入」。选「从相册选」可以多选照片，选「从文件选」可打开照片、DNG、视频或 JPEG 实况原件。也可点「试用示例」跟着简短引导试一次调色。
2. 先选风格，从推荐强度 75 开始。需要精修时进「调色」，依次可调胶片曝光、相纸与印放、基础光影和 HSL。点「重置手动调色」可恢复当前风格的推荐起点并保留构图。
3. 从底栏的「构图」打开裁切、旋转和校平。点「完成」回到原编辑面板，点「取消」放弃本次构图。
4. 在「配方」可保存、复制当前调色，或整套套用自己的配方和官方配方。整套套用保留构图，单张可从提示条撤销。照片库点「选择」，多选后可直接从「套用配方」的最近复制、我的配方或官方配方分组选择。
5. 点“导出 → 保存到相册”使用当前的尺寸和格式。“更多设置”可切换尺寸、JPEG、PNG 或 HEIF。也可分享单张照片或在照片库多选后批量保存。导出期间请保持应用打开，进程被系统杀死后不会自动续传。

DNG 在胶片模拟前由 LibRaw 提供高精度线性输入，沿用照片编辑和导出流程。普通视频和 JPEG 实况照片进入独立编辑页，可调胶片风格、强度、胶片曝光、相纸或电影拷贝片、印放曝光。页面固定显示抽帧或封面，调色控件独立滚动，右上角可直接导出。导出将固定颜色查找表用于整段视频，保留原有音轨并生成 SDR MP4。JPEG 实况导出会同步调色封面和内嵌视频，也可以单独另存 MP4。视频暂不应用空间颗粒与光晕，HEIC/AVIF 实况及各品牌的私有封装尚未接入，真实手机品牌覆盖范围未广泛测试。

![裁切](docs/images/crop.png)

![配方](docs/images/recipes.png)

## 构建

从源码构建 Android APK 前，先下载并校验固定的 LibRaw 0.22.2 源码。环境要求：JDK 17、Android SDK 36、NDK 28.2.13676358、CMake 3.22.1、Gradle 9.5.0；依赖版本见 `app/build.gradle.kts`。

```powershell
./engine/setup-libraw.ps1 -DependencyDirectory ./build/deps
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Windows 工程路径需为 ASCII；若实际目录包含非 ASCII 字符，可用 `subst` 映射到临时盘符后执行 Gradle。Linux/macOS 先执行 `./engine/setup-libraw.sh ./build/deps`，再运行 `./gradlew`，需要能下载 Gradle/Maven 依赖。

应用构建默认使用源码包内已验证的 `app/src/main/jniLibs/arm64-v8a/libpianyu_core.so` 输入。要重建胶片核心，请先准备 NDK、CMake/Ninja、glslang 和 Vulkan Headers，再运行 [`docs/build-engine.ps1`](docs/build-engine.ps1)。该脚本在解压包根目录 staging，应用 Android unfused-FMA overlay，生成 Release arm64 核心并更新资源；它不依赖原开发工作区的相对路径。完整说明见 [`docs/SOURCE-DISTRIBUTION.md`](docs/SOURCE-DISTRIBUTION.md)。

## 隐私与数据

片屿无账户、广告、网络权限和照片上传。应用长期保存可重新访问的 URI、每张照片的编辑设置和应用私有的 `recipes-v1.json`。RAW 与实况导出可能暂存原始数据，处理结束后清理缓存中的临时文件。删除原图、撤销相册访问或清除应用数据后，需要重新导入。导出 JPEG 不保留定位、EXIF 等元数据。

## 已验证与限制

0.3.12 通过 29 项单元测试和 5 项 Redmi K80 Pro 真机测试，并验证同签名 Release APK 覆盖安装。各项结果与复测情况见 [0.3.12 发布说明](docs/RELEASE-v0.3.12.md)。

v12在Redmi K80 Pro完成最终Release APK再安装验证，确认最终UI、HEIF原尺寸保存与分享；7项host tests、21项定向 Android tests、Release 构建、AndroidTest 编译和 lint（0 errors、32 warnings、1 hint）通过。JPG和HEIF原尺寸处理及PNG保存/读取通过。之前版本的宣传视频基于 versionCode 7，不代表v12默认效果。50MP、HDR和尚未覆盖的其他设备编码器仍需单独验证。

v14通过16项host tests、17项定向Android测试，另完成18张连续原尺寸JPEG压力测试（6张各复制3份）及6张原尺寸HEIF导出。100张本地导入实际列表的首屏缩略图约2.01秒完成；主线程心跳不代表全程帧率。最终相同Release APK在Redmi K80 Pro通过实际UI保存原6张JPEG（全部4096×3072）、测光/相纸/印放与正片旁路检查。Debug/Release、AndroidTest编译和lint（0 errors、36 warnings、1 hint）通过；签名与本地v12一致。150张桌面受控实验及六组稳定版基线的条件见[研究报告](docs/METERING-PRINT-STUDY-v13.md)，驱动恢复和验证边界见[批量处理调查](docs/BATCH-PIPELINE-v14.md)。

- 当前范围不是 VSCO 的全功能替代品；没有透视、曲线、蒙版、相机、专业色彩管理、HDR 保真导出或后台批处理服务。
- 本地 GPU 兼容性在 Redmi K80 Pro / Adreno 830 验证；OPPO Find X9 有用户正常使用反馈。没有 CPU 后备渲染，不能保证所有 Vulkan/GPU 设备兼容。
- Ultra HDR JPEG 可使用其 SDR 底图调色，当前成片管线使用 8-bit sRGB。10-bit/HDR HEIF 是否可读取决于设备解码器。
- DNG、普通视频和 JPEG 实况照片已接入，但其他 RAW 容器、HEIC/AVIF 实况及各品牌私有封装仍未覆盖；视频导出为 SDR MP4，保留原音轨。
- 公开版演示输入为 AI 生成的手机摄影风格 PNG 加内置 stilllife，不是实拍或真实人物背书。参考、AI输入与实际 Android 输出分别标注；编辑结果未经额外调色，不用于证明真实胶片定标。界面录制使用公开内置示例；测试输入只随 AndroidTest 包提供。
- AndroidTest 中的合成样片和测试输入只随测试包提供。

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
