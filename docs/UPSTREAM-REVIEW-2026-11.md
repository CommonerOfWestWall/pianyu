# 上游审查：SpektraLab（2026-11-10）

## 当日再次核实：2.3.1（本段取代后面的旧 HEAD 结论）

GitHub 再次查询的 main 为 [`0b5acfc`](https://github.com/JamesQiu2005/SpektraLab/commit/0b5acfc4e45b7a946ed33a3c4b4e60429d208b66)，相对片屿基线多 **5** 个提交。今天新增 2.3.0 / 2.3.1，确实包含 `engine/` 改动，不能沿用“只有 Mac 界面变化”的旧判断。完整比较：[基线至当前](https://github.com/JamesQiu2005/SpektraLab/compare/d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289...0b5acfc4e45b7a946ed33a3c4b4e60429d208b66)。

- `scan_hdr` 增加正片 HDR 扫描、输出缩放和曲线逻辑；默认关闭，不等于改善普通 JPG 的必要补丁。当前 Android 只提供 SDR，未接入 Apple gain map / HDR 显示链。
- `grain_seed` 可固定颗粒实现，默认 0 延续每次新种子；适合以后比较预览/导出一致性，不把它声称为当前已接入。
- `minilab_enlarger` 修复 minilab 表路径中放大机亮度、黄/品红滤色和预闪不起作用的问题。默认 `{print_exposure=1, y=0, m=0, preflash=0}` 原样绕过；片屿目前没有暴露这些控制，不能把后期色温冒充放大机滤色。
- Decode 曲线后的场景采样与直方图更新，不等于修改胶片材料 profile。

已实读当前 `FrameDecoder.swift`、`FilmParams.swift`、`EngineWire.swift`、架构文档及上述引擎差异。Mac 将输入变为 scene-linear ProPhoto RGB；RAW 关闭额外提亮/局部映射，非 RAW 有独立解码路径。片屿通过 Android ImageDecoder 转 sRGB，传 encoded sRGB float32，明确设置 `input_cctf_decoding=true`，由原生引擎解码传递函数；不能把这一路径描述成 RAW 解码，也不能把反伽马当成撤销手机 HDR/局部映射。

重要差异：原滑块 `exposure` 是成片后的 CPU 调整，上游 `exposure_compensation_ev` 是进入胶片响应前的曝光，两者不等价。实际同图运行后，versionCode8新增独立 `filmExposure`，接入上游前曝光；原滑块保留兼容。没有声称具备完整 Decode 曲线。运行结果与边界见 [上游同输入实测](UPSTREAM-PRACTICE-2026-11.md)。

## 更上游：spektrafilm Python 参考实现

此次解析的参考提交为 [`3bb2c2d`](https://github.com/andreavolpato/spektrafilm/commit/3bb2c2d2801ff68b92019cf1dbcbb133d60832bc)。已实读 `runtime/pipeline.py`、`runtime/stages/filming.py`、`runtime/stages/scanning.py` 与参数构建：输入色域/传递函数解码 → 胶片曝光 → 密度/耦合/颗粒 → 放大/相纸 → 光谱扫描与输出编码。`scan_film` 分支跳过相纸。它是模型参考，不是只能靠扫描照片拼色的滤镜库。

对其七个相关 JSON 与片屿保存的数据进行递归语义比较：Portra 400、Vision3 250D、Provia 100F、Velvia 100、Kodachrome 64 五款胶片内容一致；2383 和 Portra Endura 仅多出 SpektraLab 的 `data.edr_tone_map`，其余内容一致。Android 当前 `edr_enabled=false`，这部分不参与输出。800T 与 Acros II 是 SpektraLab 产品层扩展，不能宣称为上述五款相同来源的独立实测材料。

这只能证明数据来源和调用关系，不能证明 Android/Vulkan 与 Python 全像素一致、RAW 与 JPG 等价或历史扫描复刻准确。此前 Android/Windows parity 记录也不能扩大解释为当前两上游的全部路径已通过。

## 早前核实快照（已被上文更新）

通过 GitHub API 核实 `JamesQiu2005/SpektraLab` 的 `main` 当前 HEAD 为 [`fd4e1dbcd1951114c8709c775a706911c9444c3b`](https://github.com/JamesQiu2005/SpektraLab/commit/fd4e1dbcd1951114c8709c775a706911c9444c3b)，提交时间为 2026-11-09 09:08:18 UTC。它相对片屿记录的基线 [`d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289`](https://github.com/JamesQiu2005/SpektraLab/commit/d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289) 多 3 个提交：

- [`9c66f25176c070fbff9f16f7c3751767a1a66a19`](https://github.com/JamesQiu2005/SpektraLab/commit/9c66f25176c070fbff9f16f7c3751767a1a66a19)，2026-11-08 23:11:43 UTC：Mac Canvas Colour Space 菜单和英中文指南。
- [`d4b8145f6f9bdade15cda014776e99521adf0b5f`](https://github.com/JamesQiu2005/SpektraLab/commit/d4b8145f6f9bdade15cda014776e99521adf0b5f)，2026-11-09 02:47:04 UTC：2.2.2 发布信息、Mac 构建和指南更新。
- [`fd4e1dbcd1951114c8709c775a706911c9444c3b`](https://github.com/JamesQiu2005/SpektraLab/commit/fd4e1dbcd1951114c8709c775a706911c9444c3b)，2026-11-09 09:08:18 UTC：README 增加商标说明。

API compare 结果为 `ahead_by=3`，涉及 `ARCHITECTURE.md`、README、Mac 应用和指南文件；比较结果没有 `engine/` 文件。因此这 3 个提交没有改变片屿当前采用的引擎代码，无需把 Mac 色域菜单或 Mac 界面变动引入 Android。

## 商标约束

上游 README 当前说明：SpektraLab 名称和图标由 Guangzhou Songzi Intelligent Technology Services Co., Ltd. 管理，商标注册进行中；代码和数据许可不包含名称或图标。公开 fork 或构建应使用自己的名称和图标，避免暗示官方身份或背书。片屿继续使用独立的“片屿 Pianyu”名称与图标，并保留 SpektraLab 的来源署名；不会把 SpektraLab 名称作为产品品牌或 Android 应用名。

Capture One、胶片和纸材名称仍属于各自权利人；片屿只按现有来源和许可记录描述材料模型，不暗示合作或认证。

## 适用性结论

本轮仅记录上游事实和商标边界。Android 原尺寸导出、预览尺寸、颗粒交互和真机样片验证属于片屿自己的应用层工作；上游 Mac 的 Canvas Colour Space 菜单与界面结构不适用于当前 Android 范围。
