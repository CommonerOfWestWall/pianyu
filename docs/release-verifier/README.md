# 压缩 Release 的本地验收

仅用于开发，Android 16/API36+。此目录是独立 Gradle 工程，使用 Android framework 的 Instrumentation/UiAutomation，不依赖目标 APK 被 R8 删除的 Kotlin 测试类。它通过无障碍语义动作执行片屿按钮；不能替代物理触摸、双指手势或无障碍人工验收。

先安装同一开发签名的片屿 Release APK，再构建和安装工具：

```powershell
# 在本目录，配置 JDK17 和 ANDROID_HOME，路径需为 ASCII
../../gradlew.bat -p . assembleDebug
adb install -r -t build/outputs/apk/debug/PianyuReleaseVerifier-debug.apk
adb shell am instrument -w -e mode public-smoke studio.pianyu.app.test/studio.pianyu.verifier.ReleaseVerifier
adb pull /sdcard/Android/data/studio.pianyu.app/files/release-evidence
```

工具沿用开发测试包名 `studio.pianyu.app.test`，会替换普通 androidTest APK；重新运行 JUnit 测试前应重新安装普通测试 APK。验收覆盖示例→调整面板→1:1裁切→保存→分享选择器→系统选图→返回。它只点击片屿内部按钮，不选择接收应用或私人照片，截图仅保存片屿页面，输出在片屿的 external files `release-evidence/`。完成会写 `PASS` 或具体失败原因。

结果依赖当前系统分享器 `com.android.intentresolver` 与系统相册选择器；其他厂商需检查实际包名后调整断言。此工具不进入片屿生产 APK。

## 0.2 验收流程

新工具截图目录为 `release-evidence-v02/`，不会混用0.1证据。流程：照片库示例→调色→自由裁切/比例/旋转/镜像/校平→风格参考→复制配方→保存→系统分享（不选接收人）→照片库多选套用→系统多选相册（不选私人照片）→返回。

触摸拖角通过 UiAutomation 注入，若系统拦截会在报告记录 false；这与语义按钮验证分开。真实裁切数学、配方保留、批量JPG/HEIF输出和异常继续由普通 androidTest 额外验证。AndroidTest APK版本码0，framework验收工具版本码1，反向安装需先卸载纯测试包 `studio.pianyu.app.test`，不要卸载生产应用 `studio.pianyu.app`。

## 0.3验收流程

截图目录为release-evidence-v03。新增HSL实际滑块数值校验、命名配方保存/改名/应用/删除取消与确认、明确原图对比按钮。文本输入通过可编辑节点语义动作，并等待对话框准备；滑块按RangeInfo定位，避免把同名标签当作滑块。只处理公开示例。


## 0.3.2 public-smoke

上述命令只操作公开内置示例：风格说明、关闭/启用额外预设、自由裁切与旋转、HSL、原尺寸PNG保存和系统分享选择器，不选接收人。不要在带有用户照片的库运行旧版默认流程（省略 mode），旧流程包含全选库条目。


v12的public-smoke还会重置此风格调色、保存HEIF并打开HEIF分享选择器（不选择接收人），最终恢复JPEG格式偏好。只有显式mode=imported-study会读取已有导入条目以做用户授权的本地私人研究；该模式不选择或遍历其他相册照片，不改变库中的设置，产物不得公开。测试工具和私人产物均不进入生产APK。
