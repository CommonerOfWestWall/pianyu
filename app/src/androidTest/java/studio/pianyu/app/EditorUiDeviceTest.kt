package studio.pianyu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorUiDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private val packageName = "studio.pianyu.app"
    private val timeout = 30_000L

    @Test(timeout = 120_000)
    fun demoAdjustCropExportShareAndPickerFlow() {
        android.util.Log.i("PianyuUiTest", "copy evidence")
        val previous = File(instrumentation.targetContext.filesDir, "test-evidence")
        if (previous.exists()) previous.copyRecursively(
            File(instrumentation.targetContext.getExternalFilesDir(null), "pipeline-evidence"), overwrite = true)
        android.util.Log.i("PianyuUiTest", "launch activity")
        // HyperOS blocks background activity starts from the instrumentation UID.
        device.executeShellCommand("am start -W -n $packageName/.MainActivity")
        run {
            android.util.Log.i("PianyuUiTest", "activity launched; wait demo")
            val demo = device.wait(Until.findObject(By.text("试用示例")), 1500L) ?: device.wait(Until.findObject(By.text("打开示例")),1500L) ?: device.wait(Until.findObject(By.desc("窗边静物 · 示例")), timeout) ?: error("demo unavailable")
            assertTrue("demo action must be enabled", demo.isEnabled)
            demo.click()
            android.util.Log.i("PianyuUiTest", "demo clicked")

            waitText("日常暖色")
            val export = device.wait(Until.findObject(By.text("导出").enabled(true)), timeout)
                ?: error("Export remained disabled after rendering")
            assertTrue("export action must be enabled after rendering", export.isEnabled)
            takeEvidence("01-editor.png")

            waitText("调色").click()
            waitText("曝光")
            takeEvidence("02-adjustment.png")

            waitText("裁切 / 旋转").click()
            waitText("1:1").click()
            waitText("完成").click()
            waitText("风格").click()
            waitText("日常暖色")
            takeEvidence("03-crop-1x1.png")

            waitText("导出").click()
            waitText("保存到相册").click()
            assertTrue(
                "save notification missing",
                device.wait(Until.hasObject(By.text("已保存到相册 · 片屿")), timeout)
            )
            takeEvidence("04-saved.png")

            waitText("导出").click()
            waitText("分享编辑后照片").click()
            assertTrue(
                "system share chooser did not open",
                device.wait(Until.hasObject(By.pkg("com.android.intentresolver")), timeout)
            )
            assertTrue("share chooser must leave the app", device.currentPackageName != packageName)
            assertTrue("own exported filename missing from chooser",
                device.wait(Until.hasObject(By.textContains("Pianyu_")), timeout))
            device.pressBack()
            assertTrue("return from chooser failed", device.wait(Until.hasObject(By.text("导出")), timeout))

            waitText("‹ 照片库").click()
            waitText("导入").click()
            val pickerDeadline = System.currentTimeMillis() + timeout
            while (device.currentPackageName == packageName && System.currentTimeMillis() < pickerDeadline) {
                Thread.sleep(100)
            }
            assertTrue("system picker did not open", device.currentPackageName != packageName)
            val pickerPackage = device.currentPackageName
            assertTrue("picker package must be external", pickerPackage != packageName)
            val publicFixture = device.findObject(By.textContains("Pianyu-public-"))
            if (publicFixture != null) {
                // The fixture is public and uniquely named; selecting it is safe.
                publicFixture.click()
                assertTrue("picker did not return to editor", device.wait(Until.hasObject(By.text("导出")), timeout))
            } else {
                device.pressBack()
                assertTrue("picker back did not return to editor", device.wait(Until.hasObject(By.text("导入")), timeout))
            }
        }
    }

    private fun waitText(text: String) =
        device.wait(Until.findObject(By.text(text)), timeout)
            ?: error("Timed out waiting for text: $text; package=${device.currentPackageName}")

    private fun takeEvidence(name: String) {
        assertEquals("screenshot must be captured in the app", packageName, device.currentPackageName)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-evidence/$name")
        file.parentFile?.mkdirs()
        assertTrue("failed to save screenshot $name", device.takeScreenshot(file))
    }
}

