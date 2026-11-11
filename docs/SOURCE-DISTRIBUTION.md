# 源码分发与核心重建

公开源码包的根目录就是 Gradle 工程根目录。直接克隆仓库后，配置 `local.properties` 的 `sdk.dir`，即可运行 `./gradlew :app:assembleDebug` 或 `./gradlew :app:assembleRelease`。源码包不要求原开发工作区的中文绝对路径，也不要求 `work/engine-practice/deps`、`native-mobile/` 或相邻研究目录。

仓库中的 `app/src/main/jniLibs/arm64-v8a/libpianyu_core.so` 是v10重新编译的预编译输入，移除了Android误执行的桌面CPU容量实验；本地最终真机验证使用Redmi K80 Pro，OPPO恢复情况来自用户反馈。它与 `engine/`、`native/` 中的固定上游源码、`native/SOURCE-DIRTY-DIFF.patch`、Android overlay 以及本文件描述的重建脚本一起分发。重新编译核心时不要把这个 `.so` 误当作完整的源码来源或通用 GPU 兼容保证。

## 重建核心

便携入口为仓库内的 [`build-engine.ps1`](build-engine.ps1)：

```powershell
pwsh ./docs/build-engine.ps1 `
  -NdkPath C:/Android/ndk/28.2.13676358 `
  -CmakePath C:/Android/cmake/3.22.1/bin/cmake.exe `
  -GlslangPath C:/tools/glslang.exe
```

也可用 `ANDROID_NDK`、`ANDROID_CMAKE`、`GLSLANG_PATH` 提供路径；Ninja 默认取 CMake 同目录，也可传入脚本支持的参数。脚本默认把 `SourceRoot` 解析为解压包根目录，并检查工具和目录边界。

脚本将 `engine/` 和根 CMake 文件复制到临时 staging，然后严格执行 Android unfused-FMA capability overlay、删除桌面CPU容量probe调用，以及math probe的两个 `precise` 替换；任一替换次数不为 1 都会失败。Android保留设备声明的storage limit，不执行桌面CPU大缓冲实验。可先运行 `python native/scripts/check_storage_probe.py` 检查这些转换。它使用 Android 24、arm64-v8a、Release、Vulkan、`c++_shared`、`BUILD_NATIVE_RAW=OFF` 和测试 DLL 配置，生成并 strip 核心，复制 SPIR-V 资源，最后更新应用所需的 engine manifest/version。原始源码不会被 staging 覆盖。

## 工具链边界

源码包不携带 NDK、CMake、Ninja、glslang、Gradle/Maven 缓存，因此重建需要网络或本地依赖。运行时还需要 ABI 对应的 `libc++_shared.so`。Android overlay 是针对已验证设备路径的兼容性修正，不能推导为所有 Vulkan GPU 或 fused/unfused FMA 行为的保证。

## 分发内容与排除项

包含 Android app、Gradle wrapper/config、`engine/`、`native/`、资源、核心构建脚本、预编译验证输入、overlay/diff 和许可证文件。排除 `.git`、构建缓存、`local.properties`、签名材料、私人照片、URI、用户识别资料、桌面 app/RAW IO 和原开发工作区的已编译探针产物。

## 来源与许可

应用代码和 SpektraLab 修改以 GPL-3.0-or-later 提供；spektrafilm profiles、LUT 与直接派生数据以 CC BY-SA 4.0 提供；Vulkan-Headers、volk、Material Icons、Noto Serif SC 等按各自目录许可证提供。请阅读根目录和 `license/` 中的完整文本及 [`OPEN-SOURCE-NOTES.md`](OPEN-SOURCE-NOTES.md)。
