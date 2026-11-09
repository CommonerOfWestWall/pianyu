# 片屿源码归档

这是公开源码包的入口说明。直接克隆或解压仓库后，从仓库根目录运行 Gradle 即可构建应用；不需要原开发工作区中的中文目录、相邻研究目录或旧的 `native-mobile` 路径。

```powershell
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

核心重建入口是 [`docs/build-engine.ps1`](docs/build-engine.ps1)。它从仓库内的 `engine/`、根 `CMakeLists.txt`、`native/deps/` 和资源出发，在临时 staging 中应用 Android overlay，再生成 arm64 Release 核心；通过 `-NdkPath`、`-CmakePath`、`-GlslangPath` 或相应环境变量提供工具链。

归档保留已验证的 `app/src/main/jniLibs/arm64-v8a/libpianyu_core.so` 构建输入、对应上游源码、`native/SOURCE-DIRTY-DIFF.patch` 和 Android overlay 说明。预编译 `.so` 与 native overlay / diff / 重建脚本必须作为一组阅读；预编译输入不是脱离源码的独立上游发布物。

源码包不包含 `.git`、Gradle/CMake/build 缓存、`local.properties`、签名材料、私照或原开发工作区外部依赖。它也不是完全离线构建包：Gradle/Maven 依赖、Android NDK、CMake/Ninja、glslang 和 Vulkan Headers 需要由构建者提供。

旧工作区中的相对路径（例如 `work/engine-practice/deps`、`native-mobile/` 或外部资产目录）只存在于历史构建记录和旧脚本语境中，不是公开源码包的要求。公开入口统一以仓库根目录和 `docs/build-engine.ps1` 为准。

许可证和第三方来源见 [`NOTICE.md`](NOTICE.md)、[`license/NOTICE.md`](license/NOTICE.md)、[`license/LICENSE`](license/LICENSE)、[`license/SPEKTRAFILM_LICENSE.txt`](license/SPEKTRAFILM_LICENSE.txt) 和 [`docs/OPEN-SOURCE-NOTES.md`](docs/OPEN-SOURCE-NOTES.md)。
