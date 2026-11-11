# 片屿 0.3.2 修订说明

## 公开测试构建（versionCode 12）

本次发布包含此前各候选的修订，以及默认胶片前自动测光、推荐强度75与更强创作余量、Android内存修复、原尺寸JPEG/PNG/HEIF保存与分享。最终APK已在Redmi K80 Pro安装并完成实际界面检查；功能、验证范围与限制以[0.3.2发布说明](RELEASE-v0.3.2.md)为准。以下保留研究过程中的历史记录，不代表本版默认配方或本次发布资产清单。

## 历史候选构建（versionCode 8）

新增胶片前曝光，直接接入上游模型，并保留旧成片曝光和旧配方。两个上游与片屿当前源码的同输入实测共295个受控输出，含70个条带执行检查；模型基线与+0.7EV路径一致。结果与限制见 [上游实测](UPSTREAM-PRACTICE-2026-11.md)。新增缓存/历史/配方回归和真机UI验收状态在最终交付记录中列出，以下26项为上一候选的记录，不冒称当前已通过。

## 上一候选构建（versionCode 7）

保留上游材料 profiles，额外片屿调色单独实现并可在风格说明关闭；关闭/启用保留用户参数、颗粒与构图，支持撤销。旧编辑按原版本读取，重选风格恢复当前预设。自然正片、浓彩风光和克制复古分别复核，避免把降低饱和、泛黄或褪色当成统一胶片定义。味道包括有依据的偏色，不承诺所有照片更好看。

已通过26项核心真机测试和6项数值UI检查，当前构建重新生成39张默认/同图对照/白马阶段输出并核对哈希。参考资料视觉初筛166个元数据候选，不等于166张定标片。研究过程与边界见 [胶片性格复核](FILM-CHARACTER-AUDIT.md)。以下第二版宣传和更早实验说明保留历史背景，不能作为当前版本的验收记录。


0.3.2 将编辑后导出默认改为原尺寸。用户仍可选择长边 1080 或 2048；低分辨率输入不会被放大。编辑后 JPEG 使用质量 100，属于高质量有损编码。PNG 保存的是编辑后的 8-bit sRGB 像素，像素无损，但不代表 RAW 或 HDR 无损。直接分享原片会逐字节复制源文件，不经过编辑，因此保留原始元数据并明确区别于编辑后照片。

编辑器预览长边约 960，仅用于交互显示。颗粒滑块的 UI 范围为 0–100，映射到引擎参数 0–2；拖动停顿约 120ms 后刷新预览，松手提交。原图对比、裁切旋转、非空照片库打开示例和移出库入口已按 Android 流程保留；移出只删除片屿记录和缓存，不删除相册原片。

## 公开样片与介绍视频

第三版改为14段连贯口播，配套七款风格的同图前后对照、白马累计参数和真实界面片段，独立于主应用交付验收。素材使用实际versionCode7 Android输出，胶片曝光默认为0；没有将其当成versionCode8新控制的演示。来源、核对方式与限制见 [第三版说明](PROMO-V3-AUDIT.md)。第二版132秒和下列248.52秒成片保留作历史实验记录，不代表当前视频。

主样片由 AI 生成的数字风格 PNG 输入和内置 `stilllife` 组成：`portrait`、`landscape`、`night`、`humanity`、`phone-landscape`、`concert`、`candid`、`stilllife`。这些生成输入不是实拍或真实人物背书；视频中的 3 组历史参考是 LOC 扫描，作为单独的风格参考，不能与生成输入混称为扫描。风格结果由真实 Android NativeCore/PhotoIO 管线生成。旧 `ordinary-park` 和 `ordinary-meal` 不作为主样片。

视频已完成 15 段，实际时长 248.52 秒（4 分 09 秒），1080p/30fps，约 18.5 MB；使用 AI 合成旁白。前 3 段展示 LOC 历史扫描参考、AI 手机风格输入和实际 Android 输出，随后以原图为起点，用路景展示 4 个累计处理阶段：风格 100、明暗、HSL、颗粒 22。画面是静态结果说明，不是操作录屏。

完整历史参考和审美说明见 [`FILM-STUDY.md`](FILM-STUDY.md)，配方复现见 [`RECIPE-STUDY.md`](RECIPE-STUDY.md)。`pianyu-film-study.zip` 包含 3 组原图、参考、每组 4 个累计阶段的 12 个实际 Android 输出，以及完整 JSON、manifest、hash 和 runner。视频与验证记录随 v0.3.2 测试发布提供。

实验规模分两步：先生成 30 个配方候选（3×10），再对选定方案生成 12 个累计阶段（3×4）；这不是 42 张独立照片。

## 参数复现表

以下参数来自 `ShowcaseDeviceTest.kt`，每次输出的 manifest 同时保存完整 `settingsToJson`：

| 输出 | 风格索引 | 参数 |
| --- | ---: | --- |
| `humanity-tuned` | 1 | strength .55, exposure .15, warmth .08, contrast .05, highlights -.15 |
| `phone-landscape-tuned` | 3 | exposure .2, shadows .12, warmth .06, saturation .08, highlights -.15 |
| `concert-tuned` | 2 | strength .45, exposure .15, highlights -.2, warmth -.05, shadows .08 |
| `candid-tuned` | 0 | strength .8, exposure .2, warmth .08, contrast .08, highlights -.12, fade .12, grain .25 |
| `candid-strength-35` | 0 | candid tuned recipe with strength .35 |
| `candid-strength-100` | 0 | candid tuned recipe with strength 1.0 |

## 验证状态

核心 24 项真机流程已重跑通过，80 个 PNG 哈希核对通过。另有 6 项 ADB 数值 UI 检查通过：精确曝光输入、越界拒绝、取消保持原值、曝光撤销、HSL 输入与 HSL 撤销。完整 UI 自动化仍不能声称通过，既有 UiAutomator 流程受 HyperOS 输入注入限制；其他 GPU、50MP 和 HDR 保真导出仍未验证。`fullres-device-final.json` 记录 2304×3456 的 JPEG/HEIF 解码、渲染、调整和 PNG 保存回读，约 8–10 秒是这台设备的完整链路时间；PSS 是快照，不是峰值内存。原始结果随发布资产 `pianyu-verification.zip` 提供。

上游变更及“引擎无变化”的核实见 [`UPSTREAM-REVIEW-2026-11.md`](UPSTREAM-REVIEW-2026-11.md)。

白马样片在追加比较 19 个候选后，改用自然正片重新输出 4 个累计阶段；[重调前后对比与完整参数](film-study/PRAIRIE-REVISION.md) 保留旧版，视频已同步更新。
