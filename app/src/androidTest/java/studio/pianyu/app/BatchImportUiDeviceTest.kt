package studio.pianyu.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchImportUiDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private val createdFiles = mutableListOf<File>()

    @Test(timeout = 240_000)
    fun import100RendersLibraryUiWithoutMainThreadStall() {
        device.executeShellCommand("am start -W -n studio.pianyu.app/.MainActivity")
        val activity = resumedActivity()
        val application = IsolatedTestApplication.create(target)
        val models = ViewModelStore()
        lateinit var model: EditorModel
        val heartbeat = Handler(Looper.getMainLooper())
        var lastTick = 0L
        var maxGap = 0L
        val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                if (lastTick != 0L) maxGap = maxOf(maxGap, now - lastTick)
                lastTick = now
                heartbeat.postDelayed(this, 16L)
            }
        }
        try {
            instrumentation.runOnMainSync {
                model = EditorModel(application)
                models.put("batch-ui-model", model)
                activity.setContent { PianyuTheme { EditorScreen(model, pickPhoto = {}) } }
            }
            instrumentation.waitForIdleSync()
            waitText("从相册导入")
            awaitRestored(model)
            val uris = (0 until 100).map { index ->
                val name = if (index < 12) "sample_orientation6.heic"
                else if (index % 3 == 0) "sample_orientation6.jpg" else "demo.jpg"
                fixtureUri(name, "batch-ui-$index-${UUID.randomUUID()}")
            }
            instrumentation.runOnMainSync { heartbeat.post(tick) }
            val started = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { model.importPhotos(uris, openSingle = false) }
            poll(180_000L) { val state = readLibrary(model); !state.importing && state.importProgress == null && state.entries.size == 100 }
            waitText("照片库 · 100")
            waitText("原图预览")
            val firstThumbMs = SystemClock.elapsedRealtime() - started
            poll(30_000L) { val state = readLibrary(model); state.thumbnailLoading.isEmpty() && state.thumbnails.size >= 9 }
            val wallMs = SystemClock.elapsedRealtime() - started
            instrumentation.runOnMainSync { heartbeat.removeCallbacks(tick) }
            android.util.Log.i("PianyuBatchUi", "import100_first_thumb_ms=$firstThumbMs visible_ready_ms=$wallMs main_max_gap_ms=$maxGap")
            assertTrue("main heartbeat stalled for ${maxGap}ms", maxGap < 500L)
            val screenshot = File(target.getExternalFilesDir(null), "private-batch-ui-test/100-library.png")
            screenshot.parentFile?.mkdirs()
            assertTrue("UI screenshot failed", device.takeScreenshot(screenshot))
        } finally {
            instrumentation.runOnMainSync { heartbeat.removeCallbacks(tick) }
            instrumentation.runOnMainSync { activity.recreate() }
            instrumentation.waitForIdleSync()
            resumedActivity()
            instrumentation.runOnMainSync { models.clear() }
            createdFiles.forEach { it.delete() }
            application.cleanup()
        }
    }

    @Test(timeout = 180_000)
    fun failedBatchShowsFriendlyResultAndRetriesOnlyFailure() {
        device.executeShellCommand("am start -W -n studio.pianyu.app/.MainActivity")
        val activity = resumedActivity()
        val application = IsolatedTestApplication.create(target)
        val models = ViewModelStore()
        lateinit var model: EditorModel
        val before = queryOutputs()
        try {
            instrumentation.runOnMainSync {
                model = EditorModel(application)
                models.put("batch-failure-ui-model", model)
                activity.setContent { PianyuTheme { EditorScreen(model, pickPhoto = {}) } }
            }
            instrumentation.waitForIdleSync()
            waitText("从相册导入")
            awaitRestored(model)
            val valid = fixtureUri("demo.jpg", "batch-failure-valid-${UUID.randomUUID()}")
            val bad = fixtureUri("demo.jpg", "batch-failure-empty-${UUID.randomUUID()}", empty = true)
            instrumentation.runOnMainSync { model.importPhotos(listOf(valid, bad), openSingle = false) }
            poll(30_000L) { val state = readLibrary(model); !state.importing && state.entries.size == 2 }
            val badId = readLibrary(model).entries.first { it.uri == bad.toString() }.id
            clickSemantic("全选"); waitText("已选 2 张"); clickSemantic("保存 2 张")
            waitText("原尺寸 · JPEG")
            clickSemantic("更多设置")
            waitText("原尺寸 · 保留输入像素")
            waitText("JPEG · 最高质量（仍有损）")
            clickSemantic("收起设置")
            clickSemantic("保存到相册")
            waitTextLong("批量保存完成：已保存 1 张，失败 1 张", 120_000L)
            waitTextContains("导出失败")
            assertEquals(1, readLibrary(model).lastBatch?.failures?.size)
            assertEquals(badId, readLibrary(model).lastBatch?.failures?.single()?.photoId)
            clickSemantic("仅重试失败")
            waitTextLong("批量保存完成：已保存 0 张，失败 1 张", 120_000L)
            assertEquals(0, readLibrary(model).lastBatch?.saved?.size)
            assertEquals(1, readLibrary(model).lastBatch?.failures?.size)
            assertEquals(badId, readLibrary(model).lastBatch?.failures?.single()?.photoId)
            clickSemantic("关闭")
            poll(10_000L) { readLibrary(model).lastBatch == null }
        } finally {
            instrumentation.runOnMainSync { activity.recreate() }
            instrumentation.waitForIdleSync(); resumedActivity()
            instrumentation.runOnMainSync { models.clear() }
            val added = queryOutputs().apply { removeAll(before) }
            added.forEach { target.contentResolver.delete(it, null, null) }
            createdFiles.forEach { it.delete() }
            application.cleanup()
        }
    }

    private fun resumedActivity(): MainActivity {
        var activity: MainActivity? = null
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .firstOrNull { it is MainActivity }?.let { activity = it as MainActivity }
        }
        return activity ?: error("MainActivity is not RESUMED; package=${device.currentPackageName}")
    }

    private fun fixtureUri(name: String, outputName: String, empty: Boolean = false): Uri {
        val file = File(target.cacheDir, "exports/$outputName").apply {
            parentFile?.mkdirs()
            if (empty) createNewFile() else {
                val assets = if (name == "demo.jpg") target.assets else testContext.assets
                assets.open(name).use { input -> outputStream().use(input::copyTo) }
            }
        }
        createdFiles += file
        return FileProvider.getUriForFile(target, "${target.packageName}.files", file)
    }

    private fun waitText(text: String) {
        requireNotNull(device.wait(Until.findObject(By.text(text)), 30_000L)) { "text missing: $text" }
    }
    private fun waitTextLong(text: String, timeout: Long) =
        requireNotNull(device.wait(Until.findObject(By.text(text)), timeout)) { "text missing: $text" }
    private fun waitTextContains(text: String) =
        requireNotNull(device.wait(Until.findObject(By.textContains(text)), 30_000L)) { "text missing: $text" }
    private fun clickSemantic(text: String) {
        val end = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < end) {
            val node = findNode(instrumentation.getUiAutomation().rootInActiveWindow, text)
            var clickable = node
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            if (clickable != null && clickable.isEnabled && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(100L)
        }
        error("semantic click timed out: $text")
    }
    private fun findNode(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        for (index in 0 until node.childCount) findNode(node.getChild(index), text)?.let { return it }
        return null
    }
    private fun queryOutputs(): MutableSet<Uri> {
        val result = linkedSetOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?"
        target.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(target.packageName), null)?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) if (cursor.getString(name).startsWith("Pianyu_"))
                result += Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getString(id))
        }
        return result
    }

    private fun awaitRestored(model: EditorModel) = poll(30_000L) { readLibrary(model).restoring.not() }
    private fun readLibrary(model: EditorModel): LibraryState {
        lateinit var value: LibraryState
        instrumentation.runOnMainSync { value = model.library }
        return value
    }
    private fun poll(timeoutMs: Long, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100L)
        }
        throw AssertionError("condition timed out")
    }
}
