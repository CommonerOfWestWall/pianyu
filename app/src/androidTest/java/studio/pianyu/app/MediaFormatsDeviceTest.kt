package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MediaFormatsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val settings = EditSettings(lookIndex = 1, grain = 0f, autoFilmExposure = false,
        filmExposure = .5f, printStock = "kodak_2393", printExposure = .2f)

    @Test(timeout = 90_000)
    fun videoChangesAllColorPatchesAndPreservesAudioAndDuration() = runBlocking {
        withSample { video, input ->
            val source = checkNotNull(VideoIO.identify(context, input))
            assertNull(source.motion)
            val output = VideoIO.save(context, source, settings) {}
            try {
                assertEquals("video/mp4", resolver.getType(output))
                val outFile = File(video.parentFile, "export.mp4")
                resolver.openInputStream(output)!!.use { stream -> outFile.outputStream().use(stream::copyTo) }
                assertAudioTrack(outFile)
                assertVideoGrade(input, output)
            } finally { resolver.delete(output, null, null) }
        }
    }

    @Test(timeout = 90_000)
    fun jpegMotionPhotoRoundTripsWithAdjustedCoverAndVideo() = runBlocking {
        withSample { video, input ->
            val original = VideoIO.preview(context, VideoIO.Source(input))
            val coverTimeUs = 625_000L
            val container = File(video.parentFile, "input_MP.jpg")
            try {
                container.outputStream().use { MotionPhoto.write(original, video, coverTimeUs, it) }
                val source = checkNotNull(VideoIO.identify(context, uri(container)))
                assertEquals(MotionPhoto.Clip(video.length(), coverTimeUs), source.motion)
                val stripped = File(video.parentFile, "cover-only.jpg")
                container.copyTo(stripped)
                java.io.RandomAccessFile(stripped, "rw").use { it.setLength(container.length() - video.length()) }
                assertNull("stale XMP is insufficient", VideoIO.identify(context, uri(stripped)))
                val output = VideoIO.save(context, source, settings) {}
                try {
                    val outFile = File(video.parentFile, "motion_MP.jpg")
                    resolver.openInputStream(output)!!.use { stream -> outFile.outputStream().use(stream::copyTo) }
                    val clip = checkNotNull(outFile.inputStream().use(MotionPhoto::read))
                    assertEquals(coverTimeUs, clip.coverTimeUs)
                    val outVideo = File(video.parentFile, "motion.mp4")
                    outFile.inputStream().use { MotionPhoto.extract(it, outFile.length(), clip, outVideo) }
                    assertAudioTrack(outVideo)
                    assertVideoGrade(input, uri(outVideo))
                    val result = PhotoIO.decode(context, output, 0)
                    try {
                        assertEquals(original.width, result.width)
                        assertEquals(original.height, result.height)
                        assertTrue("the encoded still has changed",
                            distance(original.getPixel(80, 60), result.getPixel(80, 60)) > 15)
                    } finally { result.recycle() }
                } finally { resolver.delete(output, null, null) }
                val mp4 = VideoIO.save(context, source, settings, asMotion = false) {}
                try {
                    assertEquals("video/mp4", resolver.getType(mp4))
                    assertVideoGrade(input, mp4)
                } finally { resolver.delete(mp4, null, null) }
            } finally { original.recycle() }
        }
    }

    @Test(timeout = 90_000)
    fun videoGuideRanksLampLooksWithoutApplyingAnotherLook() = runBlocking {
        val layoutFixture = File(context.cacheDir, "editor-finish-demo.mp4")
            .takeIf { InstrumentationRegistry.getArguments().getString("useLayoutFixture") == "true" && it.isFile }
        withSample(layoutFixture) { video, input ->
            val device = UiDevice.getInstance(instrumentation)
            fun node(selector: BySelector) = checkNotNull(device.wait(Until.findObject(selector), 10_000L)) {
                "missing video editor node: $selector"
            }
            fun click(label: String) {
                fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (node == null) return null
                    if (node.isVisibleToUser && (node.text?.toString() == label ||
                            node.contentDescription?.toString() == label)) return node
                    for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                    return null
                }
                val deadline = SystemClock.uptimeMillis() + 8_000L
                while (SystemClock.uptimeMillis() < deadline) {
                    instrumentation.uiAutomation.clearCache()
                    var target = find(instrumentation.uiAutomation.rootInActiveWindow)
                    while (target != null && !target.isClickable) target = target.parent
                    if (target?.isEnabled == true && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                    SystemClock.sleep(100)
                }
                error("unable to click video editor label: $label")
            }

            device.executeShellCommand("am start -W -n studio.pianyu.app/.MainActivity")
            lateinit var activity: MainActivity
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().first()
                activity.setContent { PianyuTheme { VideoEditor(VideoIO.Source(input)) {} } }
            }
            try {
                assertTrue(device.wait(Until.hasObject(By.desc("胶片风格").enabled(true)), 10_000L))
                click("胶片风格")
                click("夜色光晕")
                val scene = node(By.desc("按场景排序"))
                val density = context.resources.displayMetrics.density
                assertTrue("video scene strip must be one 32dp row", scene.visibleBounds.height() / density in 30f..33f)
                assertFalse(device.hasObject(By.text("场景优先")))
                click("灯光")
                val ordered = sortedLookCards(LookSort.ARTIFICIAL).take(3)
                    .map { node(By.text(it.value.name)).visibleBounds.top }
                assertTrue(ordered.zipWithNext().all { (a, b) -> a < b })
                device.pressBack()
                node(By.text("胶片风格 · 夜色光晕"))
                click("全部")
                node(By.text("电影日光"))
                device.pressBack()
                node(By.text("胶片风格 · 夜色光晕"))
                click("胶片风格")
                click("电影日光")
                node(By.text("同系变体"))
                click("橄榄暖影")
                val selectedVariant = By.checked(true).hasChild(By.text("橄榄暖影"))
                val deadline = SystemClock.uptimeMillis() + 8_000L
                while (SystemClock.uptimeMillis() < deadline) {
                    instrumentation.uiAutomation.clearCache()
                    if (device.hasObject(selectedVariant)) break
                    SystemClock.sleep(100)
                }
                assertTrue("variant chip must indicate the active style", device.hasObject(selectedVariant))
                node(By.text("胶片风格 · 电影日光"))
                assertFalse(device.hasObject(By.desc("细调配方")))
                assertFalse(device.hasObject(By.textStartsWith("细调配方")))
                val preview = By.desc("视频抽帧预览")
                val beforeScroll = node(preview).visibleBounds
                val back = By.text("‹ 返回")
                val export = By.clickable(true).enabled(true).hasDescendant(By.text("导出"))
                val title = node(By.text("视频调色")).visibleBounds
                assertTrue(node(back).visibleBounds.right < title.left)
                assertTrue(node(export).visibleBounds.left > title.right)
                assertTrue(node(By.text("看原片")).visibleBounds.bottom <= beforeScroll.bottom)
                val guide = node(By.text("风格说明")).visibleBounds
                val lookButton = node(By.desc("胶片风格")).visibleBounds
                assertTrue("style guide and picker must share a row", guide.centerY() in lookButton.top..lookButton.bottom)
                val printLabel = By.text("印放曝光")
                val printTop = node(printLabel).visibleBounds.top
                val controls = By.scrollable(true).hasDescendant(printLabel)
                val controlTop = node(controls).visibleBounds.top
                assertTrue("the scrolling viewport must keep a gap below the preview",
                    (controlTop - beforeScroll.bottom) / density in 8f..10f)
                node(controls).scroll(Direction.DOWN, .55f)
                assertEquals("the gap above the scrolling viewport must stay fixed",
                    controlTop, node(controls).visibleBounds.top)
                assertTrue("the sliders must move inside the control area", node(printLabel).visibleBounds.top < printTop)
                assertEquals("scrolling controls must keep the full preview fixed", beforeScroll, node(preview).visibleBounds)

                val slider = node(By.desc("风格强度")).visibleBounds
                assertTrue(device.swipe(slider.left + slider.width() * 3 / 4, slider.centerY(),
                    slider.centerX(), slider.centerY(), 18))
                val valueBounds = node(By.desc("输入风格强度数值")).visibleBounds
                val changedValue = node(By.text(Pattern.compile("[45][0-9]|6[0-5]"))).visibleBounds
                assertTrue("the video strength slider must update its displayed value",
                    valueBounds.contains(changedValue.centerX(), changedValue.centerY()))
                node(export)
                assertEquals("dragging the slider must keep the preview fixed", beforeScroll, node(preview).visibleBounds)
                val evidence = File(context.getExternalFilesDir(null), "editor-finish-ui").apply { mkdirs() }
                assertTrue("video editor screenshot", device.takeScreenshot(File(evidence, "05-video.png")))
                File(evidence, "video-preview.txt").writeText("before=$beforeScroll\n" +
                    "after_scroll_and_drag=${node(preview).visibleBounds}\n")
                node(By.text("看原片")).click()
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.clearCache()
                node(By.text("看调色")).click()
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.clearCache()
                node(By.text("看原片"))

                val motionFile = File(video.parentFile, "layout-check_MP.jpg")
                val cover = VideoIO.preview(context, VideoIO.Source(input))
                try {
                    motionFile.outputStream().use { MotionPhoto.write(cover, video, 0L, it) }
                } finally {
                    cover.recycle()
                }
                val motion = checkNotNull(VideoIO.identify(context, uri(motionFile)))
                assertNotNull(motion.motion)
                instrumentation.runOnMainSync {
                    activity.setContent { PianyuTheme { VideoEditor(motion) {} } }
                }
                node(By.text("实况照片"))
                node(By.desc("封面预览"))
                node(export)
                val motionSceneRow = node(By.desc("按场景排序"))
                assertTrue(motionSceneRow.visibleBounds.height() / density in 30f..33f)
                node(By.text("同系变体"))
                val coverBounds = node(By.desc("封面预览")).visibleBounds
                node(By.scrollable(true).hasDescendant(By.text("印放曝光"))).scroll(Direction.DOWN, .75f)
                node(By.text("另存为 MP4"))
                node(export)
                assertEquals(coverBounds, node(By.desc("封面预览")).visibleBounds)
                assertFalse(device.hasObject(By.textStartsWith("细调配方")))
            } catch (failure: Throwable) {
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "editor-finish-video-failure.xml"))
                throw failure
            } finally {
                instrumentation.runOnMainSync { activity.recreate() }
                instrumentation.waitForIdleSync()
            }
        }
    }

    private suspend fun withSample(fixture: File? = null, test: suspend (File, Uri) -> Unit) {
        val directory = File.createTempFile("media-test-", ".tmp", File(context.cacheDir, "exports").apply { mkdirs() })
        check(directory.delete() && directory.mkdir())
        try {
            val video = File(directory, "input.mp4")
            val source = fixture?.inputStream() ?: instrumentation.context.assets.open("media/grades.mp4")
            source.use { input -> video.outputStream().use(input::copyTo) }
            test(video, uri(video))
        } finally { directory.deleteRecursively() }
    }

    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun assertAudioTrack(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val audio = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            assertNotNull("audio track survives", audio)
            extractor.selectTrack(audio!!)
            assertTrue("audio contains samples", extractor.sampleTime >= 0)
        } finally { extractor.release() }
    }

    private suspend fun assertVideoGrade(input: Uri, output: Uri) {
        val originalSource = VideoIO.Source(input)
        val original = VideoIO.preview(context, originalSource)
        val outputFrame = VideoIO.preview(context, VideoIO.Source(output))
        val expected = VideoIO.grade(context, original, settings)
        try {
            assertEquals(original.width, outputFrame.width)
            assertEquals(original.height, outputFrame.height)
            assertTrue(abs(duration(input) - duration(output)) < 150)
            var previewError = 0
            var visualChange = 0
            listOf(80 to 60, 240 to 60, 80 to 180, 240 to 180).forEach { (x, y) ->
                val actual = outputFrame.getPixel(x, y)
                val rendered = expected.getPixel(x, y)
                previewError += distance(actual, rendered)
                visualChange += distance(actual, original.getPixel(x, y))
            }
            assertTrue("four colors change ($visualChange summed RGB difference)", visualChange > 65)
            assertTrue("LUT matches the still preview ($previewError summed RGB error)", previewError < 95)
            android.util.Log.i("PianyuMediaTest", "preview_rgb_mae=${previewError / 12f} grade_rgb_mae=${visualChange / 12f} duration_ms=${duration(output)} audio=true")
        } finally {
            original.recycle()
            expected.recycle()
            outputFrame.recycle()
        }
    }

    private fun duration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return checkNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
        } finally { retriever.release() }
    }

    private fun distance(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) +
        abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
}

