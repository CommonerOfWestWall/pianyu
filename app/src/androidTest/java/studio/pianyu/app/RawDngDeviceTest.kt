package studio.pianyu.app

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RawDngDeviceTest {
    @Test(timeout = 90_000)
    fun openDngThroughPhotoEditorAdjustFilmExposureAndExport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val isolated = IsolatedTestApplication.create(context)
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val input = File.createTempFile("raw-test-", ".dng", directory)
        val models = ViewModelStore()
        try {
            instrumentation.context.assets.open("media/sensor.dng").use { stream ->
                input.outputStream().use(stream::copyTo)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", input)
            assertTrue(PhotoIO.isRaw(context, uri))
            runBlocking {
                withContext(NativeCore.dispatcher) {
                    val raw = NativeCore.openRaw(context, uri, 160)
                    try {
                        val preview = NativeCore.rawPreview(raw)
                        val result = NativeCore.renderRaw(context, raw, Looks[1], filmExposure = .4f)
                        try {
                            assertEquals(160, preview.width)
                            assertEquals(120, preview.height)
                            assertEquals(preview.width, result.width)
                            assertEquals(preview.height, result.height)
                        } finally { result.recycle(); preview.recycle() }
                    } finally { NativeCore.releaseRaw(raw) }
                }
            }
            lateinit var model: EditorModel
            instrumentation.runOnMainSync {
                model = EditorModel(isolated)
                models.put("raw-test", model)
                model.load(uri)
            }
            await(instrumentation) { model.state.displayedSettings != null && !model.state.rendering }
            val positions = (16..304 step 16).toList()
            fun exposure(ev: Float): List<Int> {
                // Use a direct-scan film to measure exposure without print compensation.
                val settings = EditSettings(lookIndex = 3, grain = 0f, filmExposure = ev, autoFilmExposure = false)
                instrumentation.runOnMainSync { model.edit(settings); model.finishAdjustment() }
                await(instrumentation) { model.state.displayedSettings == settings && !model.state.rendering }
                var value = emptyList<Int>()
                instrumentation.runOnMainSync { value = positions.map { model.state.displayed!!.getPixel(it, 120) } }
                return value
            }
            val low = exposure(-1f)
            val high = exposure(1f)
            val delta = low.zip(high) { a, b ->
                abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
            }
            android.util.Log.i("PianyuMediaTest", "dng_exposure_ramp_rgb_difference=$delta")
            assertTrue("DNG film exposure changes the ramp ($delta summed RGB levels)", delta.count { it > 12 } >= 3)

            var output: android.net.Uri? = null
            instrumentation.runOnMainSync { model.export(share = false) { output = it } }
            await(instrumentation) { output != null }
            val savedUri = checkNotNull(output)
            try {
                val rendered = context.contentResolver.openInputStream(savedUri)!!.use { BitmapFactory.decodeStream(it) }
                try {
                    assertEquals(320, rendered.width)
                    assertEquals(240, rendered.height)
                    android.util.Log.i("PianyuMediaTest", "dng_export=320x240")
                } finally { rendered.recycle() }
            } finally { context.contentResolver.delete(savedUri, null, null) }
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            input.delete()
            isolated.cleanup()
        }
    }

    private fun await(instrumentation: android.app.Instrumentation, condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 30_000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(50)
        }
        error("RAW editor did not reach the expected state")
    }
}

