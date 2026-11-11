package studio.pianyu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
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
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "v12-ui/failure.xml")
            file.parentFile?.mkdirs(); device.dumpWindowHierarchy(file)
            capture("failure")
            error("Missing text: $value")
        }
    private fun desc(value: String) = device.wait(Until.findObject(By.desc(value)), 30_000)
        ?: error("Missing control: $value")
    private fun ready() { assertNotNull(device.wait(Until.findObject(By.text("导出").enabled(true)), 30_000)) }
    private fun meter() = desc("自动测光").parent ?: error("Missing switch parent")
    private fun enter(label: String, value: String) {
        desc("输入${label}数值").click()
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
        text("应用").click()
    }
    private fun capture(name: String) {
        assertEquals("studio.pianyu.app", device.currentPackageName)
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "v12-ui/$name.png")
        file.parentFile?.mkdirs()
        assertTrue(device.takeScreenshot(file))
    }

    @Test(timeout = 120_000) fun defaultMeterStrengthEntryAndUndoAreUsable() {
        // Debug entry point opens only the bundled public demo; never the system picker.
        device.executeShellCommand("am start -W -n studio.pianyu.app/.PipelineAuditActivity --es action editor --ez resetDemo true")
        ready()
        text("75 推荐 · 100 加强")
        text("75")
        capture("01-default-75")
        enter("风格强度", "100")
        text("100")
        ready()
        capture("02-strength-100")
        text("撤销").click()
        text("75")
        text("调色").click()
        val meter = meter()
        assertTrue("New photo should enable metering", meter.isChecked)
        capture("03-auto-meter-on")
        meter.click()
        ready()
        assertFalse(meter().isChecked)
        capture("04-auto-meter-off")
        text("撤销").click()
        ready()
        assertTrue(meter().isChecked)
        enter("胶片曝光", "0.30")
        text("0.30")
        ready()
        capture("05-auto-plus-manual-ev")
        text("风格").click()
        text("风格说明").click()
        requireNotNull(device.wait(Until.findObject(By.text("重置此风格调色").enabled(true)), 30_000)).click()
        ready()
        text("调色").click()
        assertTrue(meter().isChecked)
        text("0.00")
        capture("06-recommended-start")
        text("撤销").click()
        ready()
        capture("06b-after-recommended-undo")
        text("0.30")
        text("导出").click()
        text("HEIF · 高效存储（设备支持时）").click()
        text("取消").click()
        text("导出").click()
        assertTrue(text("HEIF · 高效存储（设备支持时）").parent.findObject(By.checkable(true)).isChecked)
        capture("07-heif-export-choice")
        text("JPEG · 最高质量（仍有损）").click()
        text("取消").click()
    }
}

