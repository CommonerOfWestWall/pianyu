package studio.pianyu.app

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the bundled public demo and isolated stores; never selects user photos. */
@RunWith(AndroidJUnit4::class)
class ReferenceStyleDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private val unifiedOrder = listOf(1, 0, 8, 2, 5, 11, 12, 14, 7, 10, 3, 9, 13, 4, 6)
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
        lateinit var model: EditorModel
        try {
            onMain {
                model = EditorModel(app)
                models.put("reference19-model", model)
                activity.setContent { PianyuTheme { EditorScreen(model, pickPhoto={}) } }
            }
            assertNotNull(device.wait(Until.findObject(By.text("让日常，有一点胶片的味道。")), 15_000L))
            screenshot("empty-library")
            onMain { model.loadDemo() }
            await { !model.state.loading && !model.state.rendering && model.state.displayedSettings != null && !model.recipesLoading }
            assertNotNull(device.wait(Until.findObject(By.desc("选择风格电影日光")), 5_000L))
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

            clickText("裁切 / 旋转")
            assertNotNull(device.wait(Until.findObject(By.text("构图")), 5_000L))
            screenshot("crop-title")
            clickText("取消")

            val before = model.state.settings.copy(cropAspect=1f, crop=CropRect(.1f,.1f,.9f,.9f),
                quarterTurns=1, flipHorizontal=true)
            onMain { model.edit(before); model.finishAdjustment() }
            await { model.state.displayedSettings == before }
            val next = Looks.indexOfFirst { it.creative && it.id != Looks[before.lookIndex].id }
            onMain { model.chooseLook(next) }
            await { !model.state.rendering && model.state.displayedSettings == model.state.settings }
            val after = model.state.settings
            assertEquals(before.crop, after.crop)
            assertEquals(before.cropAspect, after.cropAspect, 0f)
            assertEquals(before.quarterTurns, after.quarterTurns)
            assertEquals(before.flipHorizontal, after.flipHorizontal)
            onMain { model.undo() }
            assertEquals(before, model.state.settings)
            onMain { model.redo() }
            assertEquals(after, model.state.settings)
            onMain { assertTrue(model.saveRecipe("参考配色验收")) }
            await { RecipeStore(app).read().recipes.any { it.name == "参考配色验收" } }
            assertEquals(after.colorRecipe(), RecipeStore(app).read().recipes.first().settings)
            assertEquals(after, settingsFromJson(settingsToJson(after)))

            clickText("配方")
            clickText("我的配方 · 1")
            waitForUi(By.text("参考配色验收"))
            clickText("创作搭配")
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.clearCache()
            waitForUi(By.desc("应用青橙夜色"))
            clickText("风格")
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.clearCache()
            assertLookSelected("选择风格${Looks[next].name}")
            clickLook("选择风格柔和黑白")
            await { model.state.settings.lookIndex == 6 && !model.state.rendering }
            clickLook("选择风格电影日光")
            await { model.state.settings.lookIndex == 1 && !model.state.rendering }
        } catch (failure: Throwable) {
            if (device.currentPackageName == "studio.pianyu.app") {
                screenshot("failure")
                device.dumpWindowHierarchy(File(instrumentation.targetContext.getExternalFilesDir(null), "unified-looks-v21-ui/failure.xml"))
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

    private fun clickText(text: String) {
        clickSemantic(text)
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
            val strip = device.findObject(By.scrollable(true).hasDescendant(cards))
                ?: error("unified look strip missing")
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
    private fun screenshot(name: String) {
        val evidence = File(instrumentation.targetContext.getExternalFilesDir(null), "unified-looks-v21-ui/$name.png")
        evidence.parentFile?.mkdirs()
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
