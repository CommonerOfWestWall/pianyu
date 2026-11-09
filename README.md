# 片屿 Pianyu 0.3.1

片屿是一款面向 Android 手机 JPG / HEIF 的本地胶片照片编辑器：在设备上完成选片、胶片风格、基础调色、构图和导出，不上传照片。

![片屿标记](docs/images/icon.png)

> 0.3.1 测试版：Android 10+、arm64。目标设备是 Redmi K80 Pro / Android 16 / Adreno 830；其他 GPU 尚未验证。

## 下载

0.3.1 的 arm64 Android 10+ 测试 APK 约 12.7 MB。下载入口：[GitHub Releases](https://github.com/CommonerOfWestWall/pianyu/releases)。当前源码中的 Release 构建使用 debug 签名，适合开发和测试；不能承诺该签名会用于长期升级。

介绍短片：[配音介绍视频、字幕及 APK](https://github.com/CommonerOfWestWall/pianyu/releases/tag/v0.3.1)。1080p，约 90 秒，使用本地 IndexTTS 的“董国鑫改1”合成配音。视频包含原创生成静物、说明卡片和真实界面局部，不是完整操作录屏。

## 名字与视觉

“片”是照片，也是生活里的片刻；“屿”是一小块值得停留的日常。应用里的岛岸、日光与一片倒影，分别构成品牌标记的三层意象：岸线承接照片，日光提供方向，倒影留下片刻。它们不是滤镜名称，也不暗示某个真实胶片的唯一颜色。

## 已实现功能

- 从系统相册导入 JPG / HEIF；一次最多选择 100 张，私有照片库保存访问 URI 和编辑设置，不复制原片。
- 7 款风格入口：日常暖色、电影日光、夜色光晕、自然正片、浓彩风光、克制复古、柔和黑白。
- 风格强度、曝光、亮度、对比、饱和、高光、阴影、色温、色调、褪色、颗粒、锐化和暗角；HSL 分色支持红、橙、黄、绿、青、蓝、紫、洋红八个色区。
- 裁切、移动裁切框、自由或锁定比例、左右 90° 旋转、水平/垂直镜像和 ±20° 校平。
- 撤销/重做、原图对比、长按对比、双指缩放；预览完成后可恢复全图。
- 复制/套用调色配方；命名配方可保存、应用、改名和删除，配方不包含几何构图。
- 单张保存或分享；照片库多选后批量保存，显示进度和失败数，并可停止后续任务。
- 导出为 sRGB JPEG（质量 92），长边 1080 或 2048；不放大更小原图，写入 `Pictures/片屿`，不覆盖原片且不保留定位/EXIF 等元数据。

## 七款风格

风格入口由 `app/src/main/java/studio/pianyu/app/Look.kt` 定义。参考材料只表示模型来源和审美方向，不是品牌合作、官方复刻或颜色保证。

| 入口 | 参考 | 说明 |
| --- | --- | --- |
| 日常暖色 | Kodak Portra 400 | Portra 400 负片与 Kodak Endura 相纸模型；偏暖、肤色柔和，适合日常和人物。 |
| 电影日光 | Kodak Vision3 250D | Vision3 250D 与 Kodak 2383 电影正片模型；适合街景、建筑和有方向的光。 |
| 夜色光晕 | CineStill 800T | CineStill 800T 与 2383；少量光晕，适合霓虹、灯光和夜景。 |
| 自然正片 | Fujifilm Provia 100F | 反转片模型，色彩相对清爽平衡，适合自然光和旅行。 |
| 浓彩风光 | Fujifilm Velvia 100 | 反转片模型，色彩更浓、对比更强，适合植物和风光。 |
| 克制复古 | Kodak Kodachrome 64 | 正片材料模型，保留克制的复古色彩，偏向街景和日常物件。 |
| 柔和黑白 | Fujifilm Neopan Acros 100 II | 黑白负片与 Ilford Multigrade IV RC 相纸模型；以灰阶、纹理和明暗关系为主。 |

![HSL 调整](docs/images/hsl.png)

界面图为 0.3 在 Redmi K80 Pro 上的真实控件局部，裁去旧测试照和系统状态栏；不是完整屏幕截图。0.3.1 沿用编辑功能，更换公开示例照并补充许可阅读入口。未用合成照片替换截图里的照片来伪造运行结果。

## 使用流程

1. 在照片库点“导入”，从系统相册选择照片；点一张进入编辑，长按可多选。
2. 先选风格，再调强度和基本色彩。点“风格说明”可查看参考材料和适用倾向。
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

0.3 的既有基线包括 19 项真机 AndroidTest、压缩 Release 的实际 UI 流程，以及源码 ZIP 解压后的独立构建。0.3.1 本轮 Debug、AndroidTest APK、Release 构建及 lint 检查通过；手机未连接，因此没有把旧的 19 项结果算作 0.3.1 新跑结果，也没有声称完成本版安装、许可弹窗和新样片的真机验证。其他 GPU 仍未验证。

- 当前范围不是 VSCO 的全功能替代品；没有透视、曲线、蒙版、相机/视频、RAW、专业色彩管理、HDR 保真导出或后台批处理服务。
- GPU 兼容性只在 Redmi K80 Pro / Adreno 830 上验证；没有 CPU 后备渲染，其他 GPU 可能无法工作。
- HEIF SDR 解码测试不能代表所有 HDR 或 gain-map 来源。
- 公开版演示样片是 AI 生成的原创静物，不用于证明真实胶片定标；公开界面局部不含照片。旧内部 Kodak 测试图及其 JPG / HEIF 衍生输入不随本仓库分发。
- Release APK 是 debug 签名测试版；公开长期分发需要独立管理正式签名和升级渠道。

## 来源与分层许可

- 应用、Android 界面和本项目修改代码：GPL-3.0-or-later，见 [`license/LICENSE`](license/LICENSE) 和根目录 [`NOTICE.md`](NOTICE.md)。
- SpektraLab 固定上游提交：`d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289`。引擎还包含 Windows/Vulkan 局部修改和 Android overlay，差异随源码包提供。
- spektrafilm profiles、LUT 及直接派生数据：CC BY-SA 4.0，见 [`license/SPEKTRAFILM_LICENSE.txt`](license/SPEKTRAFILM_LICENSE.txt)；该文件及 `license/NOTICE.md` 保留原文。
- Material Icons 为 Apache-2.0；Noto Serif SC 子集为 SIL OFL 1.1；Vulkan-Headers、volk 及其他第三方组件按各自目录许可证提供。来源记录见 [`asset-provenance/`](asset-provenance/)。

请先阅读各目录中的许可证和 NOTICE，再重新分发或修改相应内容。

## 致谢

感谢 Andrea Volpato 的 spektrafilm、SpektraLab 上游工程、Vulkan/volk、Google Material Icons 和 Noto Serif SC。片屿没有沿用网友安卓版的 UI 或二进制核心。

## 参与贡献

请先阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)。欢迎性能分析、无障碍改进、设备兼容性报告和可复现构建修复；请保留来源记录、测试输入和许可证边界，避免新增仅靠名称区分的相似风格入口。
