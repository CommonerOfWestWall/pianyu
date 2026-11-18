# 压缩 Release 的本地验收

仅用于开发，Android 16/API36+。此目录是独立 Gradle 工程，使用 Android framework 的 Instrumentation/UiAutomation，不依赖目标 APK 被 R8 删除的 Kotlin 测试类。它通过无障碍语义动作执行片屿按钮；不能替代物理触摸、双指手势或无障碍人工验收。

先安装同一开发签名的片屿 Release APK，再构建和安装工具：

```powershell
# 在本目录，配置 JDK17 和 ANDROID_HOME，路径需为 ASCII
../../gradlew.bat -p . assembleDebug
adb install -r -t -d build/outputs/apk/debug/PianyuReleaseVerifier-debug.apk
adb shell am instrument -w -e mode public-smoke studio.pianyu.app.test/studio.pianyu.verifier.ReleaseVerifier
adb pull /sdcard/Android/data/studio.pianyu.app/files/release-evidence
```

批量 UI 验收使用显式 `batch-ui` 模式。它只操作片屿库中已有条目，不打开系统相册；要求选中数量在 1–30 张之间，选择原尺寸 JPEG，等待“批量保存完成：已保存 n 张，失败 0 张”，校验本应用所有者且名称以 `Pianyu_` 开头的新 MediaStore 输出可读并记录尺寸，随后删除仅本次新增的这些输出，最后关闭结果并取消选择。截图和报告写入私有目录 `private-batch-release-v14`：

```powershell
adb shell am instrument -w -e mode batch-ui studio.pianyu.app.test/studio.pianyu.verifier.ReleaseVerifier
adb pull /sdcard/Android/data/studio.pianyu.app/files/private-batch-release-v14
```

显式 `film-print-ui` 模式只打开公开内置示例，不导出、不读取私人条目：通过实际 UI 验收胶片曝光的四种自动测光方式、自动测光开关、相纸与印放、Kodak Ultra Endura、Fujifilm Crystal Archive Type II、印放曝光 +0.4 EV 与恢复推荐，并进入自然正片确认“正片直接扫描 · 不经过相纸”，最后恢复日常暖色推荐。截图和报告写入独立目录 `release-evidence-v14-ui`：

```powershell
adb shell am instrument -w -e mode film-print-ui studio.pianyu.app.test/studio.pianyu.verifier.ReleaseVerifier
adb pull /sdcard/Android/data/studio.pianyu.app/files/release-evidence-v14-ui
```

工具沿用开发测试包名 `studio.pianyu.app.test`，会替换普通 androidTest APK；重新运行 JUnit 测试前应重新安装普通测试 APK。验收覆盖示例→调整面板→1:1裁切→保存→分享选择器→系统选图→返回。它只点击片屿内部按钮，不选择接收应用或私人照片，截图仅保存片屿页面，输出在片屿的 external files `release-evidence/`。完成会写 `PASS` 或具体失败原因。

结果依赖当前系统分享器 `com.android.intentresolver` 与系统相册选择器；其他厂商需检查实际包名后调整断言。此工具不进入片屿生产 APK。

v16新增显式 `palette-ui` 模式，只打开公开内置示例，检查“胶片 / 配色”切换、五个配色入口、强度0/75/100，以及新配色原尺寸PNG/HEIF保存和解码。仅删除此模式新建且属于片屿的导出项，不读/选择私人照片；最后恢复示例的日常暖色和JPEG偏好。证据写入 `release-evidence-v16-palettes`。

```powershell
adb shell am instrument -w -e mode palette-ui studio.pianyu.app.test/studio.pianyu.verifier.ReleaseVerifier
```

## 0.2 验收流程

新工具截图目录为 `release-evidence-v02/`，不会混用0.1证据。流程：照片库示例→调色→自由裁切/比例/旋转/镜像/校平→风格参考→复制配方→保存→系统分享（不选接收人）→照片库多选套用→系统多选相册（不选私人照片）→返回。

触摸拖角通过 UiAutomation 注入，若系统拦截会在报告记录 false；这与语义按钮验证分开。真实裁切数学、配方保留、批量JPG/HEIF输出和异常继续由普通 androidTest 额外验证。AndroidTest APK版本码0，framework验收工具版本码1；更新工具使用 `adb install -r -t -d`，不要卸载生产应用 `studio.pianyu.app`。

## 0.3验收流程

截图目录为release-evidence-v03。新增HSL实际滑块数值校验、命名配方保存/改名/应用/删除取消与确认、明确原图对比按钮。文本输入通过可编辑节点语义动作，并等待对话框准备；滑块按RangeInfo定位，避免把同名标签当作滑块。只处理公开示例。


## 0.3.2 public-smoke

上述命令只操作公开内置示例：风格说明、关闭/启用额外预设、自由裁切与旋转、HSL、原尺寸PNG保存和系统分享选择器，不选接收人。不要在带有用户照片的库运行旧版默认流程（省略 mode），旧流程包含全选库条目。


v12的public-smoke还会重置此风格调色、保存HEIF并打开HEIF分享选择器（不选择接收人），最终恢复JPEG格式偏好。只有显式mode=imported-study会读取已有导入条目以做用户授权的本地私人研究；该模式不选择或遍历其他相册照片，不改变库中的设置，产物不得公开。测试工具和私人产物均不进入生产APK。

显式 `cleanup-reference19` 模式只清理六个 `recipe-study/ref19-v16-*` 目录中已知研究文件，逐项检查规范路径，不递归删除或扫描其他库目录；清理前后核对照片库/配方文件摘要。必须先将研究证据保存到电脑。`palette-ui`沿用历史目录名 `release-evidence-v16-palettes`，不同包版本执行后应拉取到各自证据目录；当前v17结果以 installed-release-v17.json及最终日志为准。


v18的`palette-ui`扩展为七款配色（追加暮色蓝调、深林清影），证据写入独立`release-evidence-v18-palettes`。本轮仅编译通过，手机不可用，未运行。旧v16/v17日志保留历史目录含义。cleanup-reference19的范围未扩展；不得用它清理新目录或省略显式mode执行。

v20 的 `palette-ui` 遍历同一风格条中的 15 款模板。覆盖安装前后可用 `store-digests` 只读核对照片库和配方文件摘要。此模式适用于签名与目标一致的本地验收包。

```powershell
adb shell am instrument -w -e mode store-digests studio.pianyu.app.test/studio.pianyu.verifier.ReleaseVerifier
```
