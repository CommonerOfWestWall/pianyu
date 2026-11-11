# 片屿 v0.3.2 发布说明

v0.3.2 是面向 Android 10 及以上、arm64 设备的公开预发布版，公开 tag 为 `v0.3.2`。发布资产为：

- [pianyu-0.3.2-v12-arm64.apk](https://github.com/CommonerOfWestWall/pianyu/releases/download/v0.3.2/pianyu-0.3.2-v12-arm64.apk)，SHA-256：`8097a256958ca699bf57fa45af5cbe0af6ee8f508d684c037975f6935bcb7909`，大小 12,738,109 bytes。
- [pianyu-0.3.2-v12-source.zip](https://github.com/CommonerOfWestWall/pianyu/releases/download/v0.3.2/pianyu-0.3.2-v12-source.zip)。对应文件哈希记录在源码包内。

## 本版内容

- 新照片默认强度 75、轻颗粒 12.5、保护高光自动测光开启、filmEV 额外补偿为 0；100 提供更强的创作余量，不代表准确率。
- “重置此风格调色”恢复当前风格的推荐调色，保留构图并可撤销；旧编辑不会静默改色。它是配方重置，不是 AI 修图。
- 编辑后支持 JPEG、PNG，以及设备支持时的 HEIF 分享和导出；HEIF 路径为 ImageDecoder 像素 → 设备 HEVC/HEIC 编码器，使用 AndroidX HeifWriter 1.1.0，不经过 JPEG 中转。

HEIF 输出为 8-bit sRGB SDR，不承诺 HDR、10-bit、EXIF 或所有设备编码器兼容。需要完整原文件时可分享未编辑原片。

## 验证与限制

7 项 host tests、21 项定向 Android tests、Release 构建、AndroidTest 编译和 lint 通过（0 errors、32 warnings、1 hint）。最终完全相同的 APK 已在 Redmi K80 Pro 重新安装，验证最终 UI、HEIF 原尺寸保存和分享；本地真机覆盖不代表所有设备兼容。v10 已移除 Android 上误执行桌面大分配 probe 的路径；此前曾造成约 7 GB 占用，OPPO 恢复情况来自用户反馈，本地仅 Redmi K80 Pro 实测。

旧宣传视频基于 versionCode 7，保留为历史研究材料，不能描述为本版默认效果。夜色 800T 模型源于 500T 并配 2383，不是独立测量的 800T，也不是官方复刻。应用不提供 RAW、HDR 保真导出、透视、曲线、蒙版或 CPU 后备渲染；其他 GPU、超高像素、HDR/gain-map HEIF 和设备编码器仍需单独验证。

## 来源与许可

应用及修改代码按 GPL-3.0-or-later；模型、profiles、LUT 及直接派生数据按 CC BY-SA 4.0。现有 `license/`、`NOTICE.md` 和 `license/NOTICE.md` 随发布保留。引擎来源为 SpektraLab `d90cf9a005d5c7eb4c0bc1eb9cac17459c2a9289`，并包含仓库内 Android overlay 与修改差异。

Release APK 使用既有 Android Debug 开发测试签名，属于公开预发布版；不承诺该签名用于长期升级渠道。
