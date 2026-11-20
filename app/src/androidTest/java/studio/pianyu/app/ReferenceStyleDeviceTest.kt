package studio.pianyu.app

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the bundled public demo and isolated stores; never selects user photos. */
@RunWith(AndroidJUnit4::class)
class ReferenceStyleDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private val unifiedOrder = LookCards.map { it.index }
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Test(timeout=240_000)
    fun simplePalettesKeepGeometryHistoryAndSavedRecipes() {
        device.executeShellCommand("am start -W -n studio.pianyu.app/.MainActivity")
        lateinit var activity: MainActivity
        onMain {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().first()
        }
        val app = IsolatedTestApplication.create(instrumentation.targetContext)
        val models = ViewModelStore()
        val pickerRoute = AtomicReference<String>()
        lateinit var model: EditorModel
        try {
            onMain {
                model = EditorModel(app)
                models.put("reference19-model", model)
                val gallery = ActivityResultContracts.PickMultipleVisualMedia(100)
                val file = ActivityResultContracts.OpenDocument()
                activity.setContent {
                    PianyuTheme {
                        EditorScreen(
                            model = model,
                            pickPhoto = {
                                pickerRoute.set("gallery")
                                activity.startActivity(gallery.createIntent(
                                    activity,
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                ))
                            },
                            pickMediaFile = {
                                pickerRoute.set("file")
                                activity.startActivity(file.createIntent(
                                    activity,
                                    arrayOf("image/*", "video/*", "image/x-adobe-dng")
                                ))
                            }
                        )
                    }
                }
            }
            assertNotNull(device.wait(Until.findObject(By.text("让日常，有一点胶片的味道。")), 15_000L))
            screenshot("empty-library")

            clickText("导入")
            waitForUi(By.text("选择导入来源"))
            assertNotNull(device.findObject(By.text("从相册选")))
            assertNotNull(device.findObject(By.text("从文件选")))
            clickText("取消")
            waitForUi(By.text("让日常，有一点胶片的味道。"))

            clickText("导入")
            waitForUi(By.text("选择导入来源"))
            clickText("从相册选")
            awaitPickerRoute(pickerRoute, "gallery")
            device.pressBack()
            waitForUi(By.text("让日常，有一点胶片的味道。"))

            clickText("导入")
            waitForUi(By.text("选择导入来源"))
            clickText("从文件选")
            awaitPickerRoute(pickerRoute, "file")
            device.pressBack()
            waitForUi(By.text("让日常，有一点胶片的味道。"))

            clickText("试用示例")
            waitForUi(By.text("先按场景选风格，拖动强度或长按对比原图。"))
            await { !model.state.loading && !model.state.rendering && model.state.displayedSettings != null && !model.recipesLoading }
            assertEquals(EditSettings(), model.state.settings)
            waitForUi(By.desc("按场景排序"))
            assertFalse(device.hasObject(By.text("场景优先")))
            clickText("知道了")
            assertEquals(EditSettings(), model.state.settings)
            assertNull(device.findObject(By.text("经典")))
            assertNull(device.findObject(By.text("配色")))
            await { !model.state.loading && !model.state.rendering && model.state.displayedSettings != null && !model.recipesLoading }
            val beforeBrowse = model.state.settings
            clickText("风格说明")
            waitForUi(By.text("参考来源"))
            waitForUi(By.text("胶片与色彩特点"))
            val scenes = By.text("适合的环境与场景")
            if (!device.hasObject(scenes)) {
                device.findObject(By.scrollable(true).hasDescendant(By.text("参考来源")))
                    ?.scroll(Direction.DOWN, .85f)
            }
            waitForUi(scenes)
            clickText("明白了")

            for (sort in LookSort.entries) {
                clickScene(sort.label)
                val cards = sortedLookCards(sort).take(3).map { By.desc("选择风格${it.value.name}") }
                cards.forEach(::waitForUi)
                val positions = cards.map { device.findObject(it).visibleBounds.left }
                assertEquals(positions.sorted(), positions)
                assertEquals(beforeBrowse, model.state.settings)
                clickScene("全部")
                waitForUi(By.desc("选择风格电影日光"))
            }

            val demoAction = "选择风格青蓝亮调"
            clickLook(demoAction)
            await { model.state.settings.lookIndex == Looks.indexOfFirst { it.id == "cinema-tungsten-cool-bright" } && model.state.displayedSettings == model.state.settings }
            assertLookSelected(demoAction)
            waitForUi(By.text("变体"))
            waitForUi(By.text("浅绿柔光"))
            waitForUi(By.text("冷灰薄雾"))
            onMain {
                val source = checkNotNull(model.state.source)
                val portrait = 4f / 5f
                model.setGeometry(model.state.settings.copy(cropAspect = portrait,
                    crop = PhotoGeometry.fitAspect(source.width, source.height, portrait)))
            }
            await { !model.state.rendering && model.state.displayedSettings == model.state.settings }
            waitForUi(By.desc("输入风格强度数值"))
            val stylePreview = previewHeight()
            val density = instrumentation.targetContext.resources.displayMetrics.density
            val sceneRow = device.findObject(By.desc("按场景排序")).visibleBounds
            val stylesTab = device.findObject(By.text("风格")).visibleBounds
            val panelDp = (stylesTab.centerY() - sceneRow.top) / density - 28 + 4
            val ratio = 100.0 * stylePreview / device.displayHeight
            assertTrue("style preview height is $ratio% of display", ratio >= 45.0)
            if (device.displayHeight / density >= 820) {
                assertTrue("style panel height is $panelDp dp", panelDp in 250f..275f)
            }
            assertTrue("scene strip must be one 32dp row", sceneRow.height() / density in 30f..33f)
            val reset = By.text("重置手动调色")
            waitForUi(reset)
            assertTrue("reset must be disabled when only the style and crop changed",
                generateSequence(device.findObject(reset)) { it.parent }.any { !it.isEnabled })
            screenshot("01-photo-style")
            clickText("调色")
            waitForUi(By.desc("胶片曝光调整"))
            val adjustPreview = previewHeight()
            assertEquals("photo preview must stay fixed between tabs", stylePreview, adjustPreview)
            clickText("基础")
            waitForUi(By.desc("画面曝光调整"))
            assertFalse(device.hasObject(By.desc("曝光调整")))
            assertEquals("basic adjustments keep the same preview height", stylePreview, previewHeight())
            screenshot("03-photo-basic")
            File(evidenceDir(), "preview-size.txt").writeText(
                "screen_px=${device.displayHeight}\nstyle_preview_px=$stylePreview\nadjust_preview_px=$adjustPreview\n" +
                    "preview_percent=$ratio\nstyle_panel_dp=$panelDp\nscene_row_dp=${sceneRow.height() / density}\n"
            )
            clickText("风格")
            onMain { model.undo(); model.undo() }
            await { model.state.displayedSettings == beforeBrowse }
            for (index in unifiedOrder) {
                val look = Looks[index]
                val action = "选择风格${look.name}"
                clickLook(action)
                await { model.state.settings.lookIndex == index && !model.state.rendering && model.state.displayedSettings == model.state.settings }
                assertEquals(.75f, model.state.settings.strength, 0f)
                assertEquals(EditSettings(lookIndex=index), model.state.settings)
                assertLookSelected(action)
                screenshot(look.id)
            }

            clickText("构图")
            waitForUi(By.text("重置"))
            screenshot("crop-title")
            clickText("取消")

            clickLook(demoAction)
            await { model.state.displayedSettings == model.state.settings }
            val before = model.state.settings.copy(cropAspect=1f, crop=CropRect(.1f,.1f,.9f,.9f),
                quarterTurns=1, flipHorizontal=true, exposure=.2f, filmExposure=.3f,
                hsl=HslSettings(blue=HslChannel(.1f,.1f,.1f)))
            onMain { model.edit(before); model.finishAdjustment() }
            await { model.state.displayedSettings == before }
            waitForUi(By.text("变体"))
            for ((index, child) in Looks[before.lookIndex].variants()) {
                clickText(child.name)
                val chosen = before.copy(lookIndex = index)
                await { !model.state.rendering && model.state.displayedSettings == chosen }
                assertEquals(chosen, model.state.settings)
                assertLookSelected(demoAction)
                screenshot(child.id)
            }
            clickText("标准")
            await { model.state.displayedSettings == before }
            val resetButton = By.clickable(true).enabled(true).hasDescendant(reset)
            waitForUi(resetButton)
            onMain {
                val source = checkNotNull(model.state.source)
                model.setGeometry(before.copy(cropAspect = .8f,
                    crop = PhotoGeometry.fitAspect(source.width, source.height, .8f),
                    quarterTurns = 0, flipHorizontal = false))
            }
            await { !model.state.rendering && model.state.displayedSettings == model.state.settings }
            val after = model.state.settings
            onMain { assertTrue(model.saveRecipe("参考配色验收")) }
            await { RecipeStore(app).read().recipes.any { it.name == "参考配色验收" } }
            assertEquals(after.colorRecipe(), RecipeStore(app).read().recipes.first().settings)
            assertEquals(after, settingsFromJson(settingsToJson(after)))

            clickText("配方")
            waitForUi(By.text("参考配色验收"))
            waitForUi(By.text("我的配方"))
            assertFalse(device.hasObject(By.text("细调配方")))
            clickText("官方配方")
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.clearCache()
            val header = listOf("我的配方", "官方配方", "复制", "保存").map(By::text)
            header.forEach(::waitForUi)
            val headerCenters = header.map { device.findObject(it).visibleBounds.centerY() }
            assertTrue("recipe tabs and actions must share the same row",
                headerCenters.max() - headerCenters.min() <= 4 * density)
            waitForUi(By.checked(true).hasDescendant(By.text("官方配方")))
            CreativeRecipes.forEach { waitForUi(By.text(it.name)) }
            waitForUi(By.text("整套套用"))
            clickSemantic("整套套用青橙夜色", description = true)
            val applied = after.withColorRecipe(CreativeRecipes.first().settings)
            await { model.state.settings == applied }
            assertEquals(after.crop, applied.crop)
            val undo = device.wait(Until.findObject(By.desc("撤销套用配方")), 3_000L)
                ?: error("recipe snackbar must expose undo")
            val undoBounds = undo.visibleBounds
            val headerTop = device.findObject(By.text("官方配方")).visibleBounds.top
            assertTrue("undo message must leave the recipe list clear", undoBounds.bottom < headerTop)
            val compare = By.text("对比原图")
            waitForUi(compare)
            assertTrue("undo message must sit above the original comparison chip",
                undoBounds.bottom < device.findObject(compare).visibleBounds.top)
            screenshot("02-photo-recipe")
            undo.click()
            await { model.state.displayedSettings == after }
            screenshot("recipes")
            clickText("风格")
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.clearCache()
            assertLookSelected(demoAction)
            clickLook("选择风格柔和黑白")
            await { model.state.settings.lookIndex == 6 && !model.state.rendering }
            clickLook("选择风格电影日光")
            await { model.state.settings.lookIndex == 1 && !model.state.rendering }

            clickText("‹ 照片库")
            waitForUi(By.text("选择"))
            waitForUi(By.desc(model.library.entries.single { it.id == DEMO_PHOTO_ID }.name))
            assertFalse(model.library.selectionMode)
            screenshot("04-library")
            clickText("选择")
            await { model.library.selectionMode && model.library.selected.isEmpty() }
            val demoName = model.library.entries.single { it.id == DEMO_PHOTO_ID }.name
            waitForUi(By.desc(demoName))
            clickSemantic(demoName, description = true)
            waitForUi(By.text("已选 1 张"))
            clickText("套用配方")
            waitForUi(By.text("选择套用配方"))
            waitForUi(By.text("最近复制"))
            waitForUi(By.text("我的配方"))
            waitForUi(By.text("官方配方"))
            waitForUi(By.text("套用"))
            waitForUi(By.text("夜色光晕 · 强度 75%"))
            assertFalse(device.hasObject(By.textStartsWith("基础 夜色光晕")))
            assertFalse(device.hasObject(By.text("官方配方 · 青橙夜色")))
            screenshot("03-batch-recipes")
            clickSemantic("整套套用参考配色验收", description = true)
            await { model.library.entries.single { it.id == DEMO_PHOTO_ID }.settings == after }
            clickText("取消选择")
            await { !model.library.selectionMode && model.library.selected.isEmpty() }
        } catch (failure: Throwable) {
            if (device.currentPackageName == "studio.pianyu.app") {
                screenshot("failure")
                device.dumpWindowHierarchy(File(instrumentation.targetContext.getExternalFilesDir(null), "editor-finish-ui/failure.xml"))
            }
            throw failure
        } finally {
            onMain { activity.recreate() }
            instrumentation.waitForIdleSync()
            onMain { models.clear() }
            app.cleanup()
        }
    }

    private fun waitForUi(selector: BySelector) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.uiAutomation.clearCache()
            if (device.hasObject(selector)) return
            SystemClock.sleep(100)
        }
        error("UI node did not appear: $selector")
    }

    private fun previewHeight(): Int {
        val preview = By.desc("照片预览")
        waitForUi(preview)
        return device.findObject(preview).visibleBounds.height()
    }

    private fun clickScene(label: String) {
        val selector = By.text(label)
        val strip = By.desc("按场景排序")
        waitForUi(strip)
        val direction = if (label == "全部") Direction.LEFT else Direction.RIGHT
        repeat(6) {
            instrumentation.uiAutomation.clearCache()
            if (device.wait(Until.hasObject(selector), 500L)) {
                clickText(label)
                return
            }
            device.findObject(strip)?.scroll(direction, .65f, 1600)
        }
        error("scene not reachable: $label")
    }

    private fun clickText(text: String) {
        clickSemantic(text)
    }

    private fun awaitPickerRoute(route: AtomicReference<String>, expected: String) {
        val until = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < until) {
            if (route.get() == expected && device.currentPackageName != "studio.pianyu.app") return
            SystemClock.sleep(50)
        }
        assertEquals("picker callback route", expected, route.get())
        error("system picker did not open for $expected")
    }

    private fun assertLookSelected(action: String) {
        val card = device.wait(Until.findObject(By.desc(action)), 5_000L)
            ?: error("look not visible: $action")
        var radio = card
        while (!radio.isCheckable) radio = radio.parent ?: error("look is not checkable: $action")
        assertTrue("look not selected: $action", radio.isChecked)
    }

    private fun clickLook(action: String) {
        val order = unifiedOrder.map { "选择风格${Looks[it].name}" }
        val target = order.indexOf(action)
        require(target >= 0) { "Unknown look: $action" }
        val cards = By.descStartsWith("选择风格")
        repeat(20) {
            instrumentation.uiAutomation.clearCache()
            if (device.wait(Until.hasObject(By.desc(action)), 700L)) {
                clickSemantic(action, description=true)
                return
            }
            val strip = device.findObjects(By.scrollable(true).hasDescendant(cards))
                .minByOrNull { it.visibleBounds.height() } ?: error("unified look strip missing")
            val first = strip.findObjects(cards).map { order.indexOf(it.contentDescription) }
                .filter { it >= 0 }.minOrNull()
            val direction = if (first != null && target < first) Direction.LEFT else Direction.RIGHT
            strip.scroll(direction, .30f, 600)
        }
        error("look not visible: $action")
    }

    private fun clickSemantic(value: String, description: Boolean = false) {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val label = if (description) node.contentDescription else node.text
            if (label?.toString() == value && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        val until = SystemClock.uptimeMillis() + 15_000L
        while (SystemClock.uptimeMillis() < until) {
            instrumentation.uiAutomation.clearCache()
            var node = find(instrumentation.uiAutomation.rootInActiveWindow)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            SystemClock.sleep(50)
        }
        error("semantic click failed: $value")
    }
    private fun evidenceDir() =
        File(instrumentation.targetContext.getExternalFilesDir(null), "editor-finish-ui").apply { mkdirs() }

    private fun screenshot(name: String) {
        val evidence = File(evidenceDir(), "$name.png")
        assertTrue(device.takeScreenshot(evidence))
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 45_000L
        while (SystemClock.uptimeMillis() < until) {
            var ready=false
            onMain { ready=condition() }
            if(ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError("reference palette state timed out")
    }
}
