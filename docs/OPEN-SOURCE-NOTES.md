# 0.3.1 开源说明

片屿使用 [JamesQiu2005/SpektraLab](https://github.com/JamesQiu2005/SpektraLab) 上游固定提交 `d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289`。公开源码同时包含此前 Windows/Vulkan 的局部修改，以及 Android unfused-FMA 数学探针和设备 capability overlay。对应源码、`native/SOURCE-DIRTY-DIFF.patch`、预编译 `.so` 和 [`build-engine.ps1`](build-engine.ps1) 一并提供，用来说明预编译核心如何产生。

应用 Kotlin/Compose 界面、JNI、C++ 修改及项目脚本按 GPL-3.0-or-later 提供。根目录 `NOTICE.md` 保留上游 NOTICE 并追加片屿修改说明；`license/NOTICE.md` 和 `license/SPEKTRAFILM_LICENSE.txt` 保留原文。根 `COPYING` 是完整 GPL 文本副本；使用此文件名是为了兼容 Windows 上既有 `license/` 目录的大小写规则。

spektrafilm profiles、LUT 及直接派生数据按 CC BY-SA 4.0 提供；Vulkan-Headers、volk、Material Icons、Noto Serif SC 等第三方组件保留各自目录中的许可证和来源记录。不要用项目 GPL 文本替代这些组件的许可证。

旧内部测试使用过 Kodak/Lossless Image Suite 的 `kodim07`，但其来源说明不作为公开素材许可证。本仓库替换应用示例及解码测试输入为原创 AI 生成的静物；公开截图仅为不含照片的旧版真实控件局部。样片和介绍短片不用于证明真实胶片定标。来源与使用声明见 `asset-provenance/DEMO.md`。

`app/src/main/jniLibs/arm64-v8a/libpianyu_core.so` 是 0.3 验证构建的输入。它不单独代表上游提交，也不保证其他 GPU 可用。要审计或重建，应同时查看 `engine/`、`native/`、`native/SOURCE-DIRTY-DIFF.patch`、`native/scripts/` 和 `docs/build-engine.ps1`。公开包不是完全离线构建环境。

0.3 品牌标记是项目内手工绘制的 Android VectorDrawable；`asset-provenance/BRAND-V03.md` 记录了设计意象和颜色。Material Icons 与 Noto Serif SC 子集的来源、哈希和许可证在 `asset-provenance/`。不要把第三方或来源受限资产重新标成项目原创。
