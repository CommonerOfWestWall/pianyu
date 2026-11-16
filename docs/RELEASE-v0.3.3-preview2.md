# 片屿 v0.3.3-preview2 发布说明

Android 10+ / arm64，versionCode 14，公开测试版。

- [下载安装包](https://github.com/CommonerOfWestWall/pianyu/releases/download/v0.3.3-preview2/pianyu-0.3.3-preview2-v14-arm64.apk)：12,760,837 bytes（约12.17 MiB）。
- APK SHA-256：`951325888179b4f17153f12527ae392d45bc6d6ecf22859905c3d572b42f33fe`。
- [完整源码ZIP](https://github.com/CommonerOfWestWall/pianyu/archive/refs/tags/v0.3.3-preview2.zip)：对应公开标签，包含Android应用、原生引擎源码、Android修改和构建脚本；逐文件SHA-256见Release附件`source-files.json`。

## 修复与功能

- GPU设备失效后销毁并重建引擎，按相同输入、尺寸和参数自动重试一次，避免后续照片沿用失效引擎。不会静默缩图或关闭颗粒、测光。
- 批量保存后保留成功数、失败数和逐张失败原因，支持“仅重试失败”；已成功的照片不会重复保存。可停止后续任务。
- 多图导入先去重，再分段读取信息并显示进度，防止重复启动；照片库按需读取缩略图，导入阶段不渲染全部胶片效果。
- 首页保留七个推荐风格；手动调色新增均衡、中心、保护高光、保护阴影四种测光意图，并保留自动测光开关、胶片曝光补偿。
- “相纸与印放”提供兼容相纸/电影拷贝片和独立±2EV印放曝光，可恢复推荐、撤销、保存配方和批量套用。正片直接扫描不经过相纸。
- 旧编辑与构图保留，新字段缺省跟随原推荐搭配。应用继续本地处理，无网络权限，不上传照片。

## 实际验证

Redmi K80 Pro / Android API 36：

- 6张4096×3072 HEIF输入，原尺寸JPEG和HEIF各6/6成功。
- 将上述6张各复制3份做18张连续JPEG压力测试，18/18成功，约143秒；不是18个不同场景。
- 100张本地导入的真实Compose界面测试，含12张HEIF：首个缩略图约493毫秒，首屏请求的缩略图约2.01秒完成，16毫秒主线程心跳最大间隔92毫秒。
- 正常照片/坏文件混合、仅重试失败、停止/再次处理、HEIF方向与可读性检查通过。
- 最终R8压缩Release通过真实界面导出原6张JPEG，全部可完整解码且为4096×3072；测光、相纸、印放与正片旁路界面通过。
- 16项host tests、17项定向Android测试通过，另执行上述压力与HEIF复跑；Debug/Release构建、AndroidTest编译通过；lint为0 errors、36 warnings、1 hint。

研究依据：[批量处理调查](BATCH-PIPELINE-v14.md)、[测光与相纸同图对照](METERING-PRINT-STUDY-v13.md)、[使用指南](USER-GUIDE.md)。私人照片、URI、文件名和截图不随发布上传。

## 验证边界与许可

首次GPU设备失效的触发条件尚未完全查明，已修复失效后的连锁使用并增加恢复/诊断，不宣称驱动故障已被彻底消除。100张导入测试不含云相册下载，主线程心跳不等于完整帧率统计，仍可能短暂掉帧。其他设备、50MP、HDR/gain-map HEIF和厂商编码器需另测；HEIF输出仍为8-bit sRGB SDR。进程被系统终止后不会自动续传。

APK与已安装并验证的本地v14字节相同，沿用v12的Android Debug开发测试签名，可覆盖同签名旧版，不承诺长期升级签名。核心预构建库未改动；JNI增加失效恢复和诊断。

应用及修改代码按GPL-3.0-or-later；模型、profiles、LUT及直接派生数据按CC BY-SA 4.0。`license/`、`NOTICE.md`与`license/NOTICE.md`继续保留。上游来源和固定提交见[上游实践](UPSTREAM-PRACTICE-2026-11.md)及[测光与相纸研究](METERING-PRINT-STUDY-v13.md)。
