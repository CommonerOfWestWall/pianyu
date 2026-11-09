# 片屿 0.3.1 发布说明

## 发布定位

0.3.1 是可公开审阅的 Android arm64 测试版，面向 Android 10 及以上。下载入口为 [GitHub Releases](https://github.com/CommonerOfWestWall/pianyu/releases/tag/v0.3.1)；APK 约 12.7 MB。附有约 90 秒的 1080p 中文介绍短片、字幕和校验和，配音由本地 IndexTTS 使用“董国鑫改1”音色合成。短片是原创静物、说明卡片和真实控件局部剪辑，不是完整操作录屏。

## 本版内容

- 本地 JPG / HEIF 导入、7 款胶片材料参考风格和基础调色。
- HSL 八色分色、裁切/旋转/镜像/校平、原图对比、撤销/重做。
- 命名配方、复制/套用配方、单图分享和批量导出。
- sRGB JPEG 1080/2048 导出，不覆盖原片且清除定位/EXIF 等元数据。
- 相对内部 0.3.0：更换公开示例照和方向测试输入，新增离线许可阅读入口，补充开源说明；胶片核心与调色/构图算法未改动。

## 验收依据

0.3.1 的 Debug、AndroidTest APK、Release 构建与 lint 检查通过；手机未连接，本轮没有安装验证或运行真机测试。0.3 的既有基线为 19 项真机 AndroidTest、压缩 Release UI 流程及解压源码独立构建，这些不冒充本版新跑结果。重点设备为 Redmi K80 Pro / Android 16 / Adreno 830。其他 GPU 未验证，不含 CPU 后备渲染。

## 已知限制

片屿尚未实现透视、曲线、蒙版、相机/视频、RAW、专业色彩管理、HDR 保真导出或后台批处理服务，因此不宣称 VSCO 全功能等价。HEIF SDR 解码通过的测试不覆盖所有 HDR/gain-map 来源。Release 构建使用 debug 签名，属于测试版，不能承诺签名、升级路径或长期兼容性。公开演示样片为 AI 生成；控件截图裁自不含照片的 0.3 真机画面。旧内部 Kodak 照片不随本次发布。

## 分发与许可

应用代码和 SpektraLab 修改按 GPL-3.0-or-later；spektrafilm profiles、LUT 与直接派生数据按 CC BY-SA 4.0；第三方组件按各自许可证。固定上游提交为 `d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289`。请阅读根目录 `NOTICE.md`、`license/` 和 [`OPEN-SOURCE-NOTES.md`](OPEN-SOURCE-NOTES.md)。
