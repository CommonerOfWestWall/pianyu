package studio.pianyu.app

import android.net.Uri
import androidx.activity.compose.setContent
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExposureStrengthUiDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private fun text(value: String) = device.wait(Until.findObject(By.text(value)), 30_000)
        ?: run {
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "editor-seal-ui/failure.xml")
            file.parentFile?.mkdirs(); device.dumpWindowHierarchy(file)
            capture("failure")
            error("Missing text: $value")
        }
    private fun desc(value: String) = device.wait(Until.findObject(By.desc(value)), 30_000)
        ?: error("Missing control: $value")
    private fun ready() {
        instrumentation.uiAutomation.clearCache()
        assertNotNull(device.wait(Until.findObject(By.text("导出").enabled(true)), 30_000))
    }

    private fun click(label: String, description: Boolean = false) {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val value = if (description) node.contentDescription else node.text
            if (node.isVisibleToUser && value?.toString() == label) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.uiAutomation.clearCache()
            var node = find(instrumentation.uiAutomation.rootInActiveWindow)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                instrumentation.waitForIdleSync()
                return
            }
            SystemClock.sleep(100)
        }
        error("Could not activate $label")
    }

    private fun assertSelectedTab(label: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.uiAutomation.clearCache()
            if (device.hasObject(By.selected(true).hasDescendant(By.text(label)))) return
            SystemClock.sleep(100)
        }
        error("Expected $label to be selected")
    }

    private fun outputs(): Set<Uri> {
        val context = instrumentation.targetContext
        val media = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return buildSet {
            context.contentResolver.query(media,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?", arrayOf(context.packageName), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1).startsWith("Pianyu_")) add(Uri.withAppendedPath(media, cursor.getString(0)))
                }
            }
        }
    }
    private fun assertMetering(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.uiAutomation.clearCache()
            if (desc("自动测光").parent?.isChecked == expected) return
            SystemClock.sleep(100)
        }
        error("Expected automatic metering state: $expected")
    }
    private fun enter(label: String, value: String) {
        click("输入${label}数值", description = true)
        desc("${label}数值输入框")
        // Compose exposes the description on a non-editable semantics child.
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
            ?: error("Missing editable field: $label")
        // HyperOS rejects accessibility setText here; exercise real keyboard input.
        val bounds = field.visibleBounds
        device.executeShellCommand("input tap ${bounds.centerX()} ${bounds.top + bounds.height() / 3}")
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)), 5_000))
        device.executeShellCommand("input keyevent KEYCODE_MOVE_END")
        device.executeShellCommand("input keyevent " + List(16) { "KEYCODE_DEL" }.joinToString(" "))
        device.executeShellCommand("input text $value")
        assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText").text(value)), 5_000))
        click("应用")
    }
    private fun capture(name: String) {
        assertEquals("studio.pianyu.app", device.currentPackageName)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "editor-seal-ui/$name.png")
        file.parentFile?.mkdirs()
        assertTrue(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(file.parentFile, "$name.xml"))
    }

    @Test(timeout = 180_000) fun defaultMeterStrengthEntryAndUndoAreUsable() {
        device.executeShellCommand("am start -W -n studio.pianyu.app/.MainActivity")
        lateinit var activity: MainActivity
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().first()
        }
        val app = IsolatedTestApplication.create(instrumentation.targetContext)
        val models = ViewModelStore()
        val before = outputs()
        try {
            instrumentation.runOnMainSync {
                val model = EditorModel(app)
                models.put("editor-guidance", model)
                activity.setContent { PianyuTheme { EditorScreen(model, pickPhoto = {}) } }
            }
            text("导入")
            capture("00-empty-library")
            click("试用示例")
            ready()
            capture("01-style")
            assertSelectedTab("风格")
            text("75")
            click("知道了")
            click("构图")
            click("4:5")
            click("完成")
            ready()
            click("风格说明")
            text("参考来源")
            text("胶片与色彩特点")
            val disableFinish = text("关闭片屿预设").visibleBounds
            val closeGuide = text("明白了").visibleBounds
            assertEquals("both guide actions must be on the same row", closeGuide.centerY(), disableFinish.centerY())
            assertTrue("the preset action must sit left of the confirm action", disableFinish.right < closeGuide.left)
            assertFalse("the preset action must stay outside the scrolling text",
                device.hasObject(By.scrollable(true).hasDescendant(By.text("关闭片屿预设"))))
            capture("look-guide-0.3.12")
            click("关闭片屿预设")
            ready()
            click("风格说明")
            val enableFinish = text("启用片屿预设").visibleBounds
            assertEquals(text("明白了").visibleBounds.centerY(), enableFinish.centerY())
            click("启用片屿预设")
            ready()
            click("风格说明")
            text("关闭片屿预设")
            click("明白了")
            enter("风格强度", "100")
            text("100")
            click("撤销")
            text("75")
            click("调色")
            assertSelectedTab("调色")
            desc("胶片曝光调整")
            listOf("胶片", "相纸", "基础").forEach(::text)
            desc("HSL分色调整")
            click("基础")
            desc("画面曝光调整")
            assertFalse(device.hasObject(By.text("复制")))
            assertNotNull(device.wait(Until.findObject(By.checked(true).hasDescendant(By.text("画面曝光"))), 5_000))
            assertTrue(generateSequence(text("重置画面曝光")) { it.parent }.any { !it.isEnabled })
            capture("basic-0.3.11")
            enter("画面曝光", "0.30")
            text("0.30")
            click("重置画面曝光")
            text("0.00")
            assertTrue(generateSequence(text("重置画面曝光")) { it.parent }.any { !it.isEnabled })
            click("撤销")
            text("0.30")
            click("撤销")
            text("0.00")

            click("胶片")
            assertMetering(true)
            capture("03-film-exposure")
            click("自动测光", description = true)
            assertMetering(false)
            click("撤销")
            assertMetering(true)
            enter("胶片曝光", "0.30")
            text("0.30")
            click("HSL分色调整", description = true)
            text("红 · 饱和")
            click("相纸")
            desc("印放曝光调整")
            click("选择冲印材料", description = true)
            click("Kodak Ultra Endura")
            text("Kodak Ultra Endura")
            capture("03b-film-print")
            click("恢复推荐冲印 · 可撤销")
            click("基础")
            desc("画面曝光调整")

            click("风格")
            click("重置手动调色")
            ready()
            click("调色")
            click("胶片")
            assertMetering(true)
            text("0.00")
            click("撤销")
            text("0.30")

            click("配方")
            text("保存")
            assertSelectedTab("配方")
            capture("04-recipes")
            click("复制")
            text("调色配方已复制，保留每张照片自己的构图")
            click("保存")
            text("配方名称")
            click("取消")

            click("构图")
            click("1:1")
            click("完成")
            ready()
            click("导出")
            text("原尺寸 · JPEG")
            assertFalse(device.hasObject(By.text("原尺寸 · 保留输入像素")))
            capture("05-export")
            click("更多设置")
            click("HEIF · 高效存储（设备支持时）")
            text("原尺寸 · HEIF")
            click("取消")
            click("导出")
            text("原尺寸 · HEIF")
            click("更多设置")
            click("缩小 · 长边 1080")
            click("JPEG · 最高质量（仍有损）")
            text("长边 1080 · JPEG")
            capture("export-0.3.11")
            click("收起设置")
            text("更多设置")
            assertFalse(device.hasObject(By.text("缩小 · 长边 1080")))
            click("保存到相册")
            text("已保存到相册 · 片屿")
            val output = (outputs() - before).single()
            assertEquals("image/jpeg", instrumentation.targetContext.contentResolver.getType(output))
        } finally {
            (outputs() - before).forEach { instrumentation.targetContext.contentResolver.delete(it, null, null) }
            instrumentation.runOnMainSync { activity.recreate() }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { models.clear() }
            app.cleanup()
        }
    }
}
