package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoQualityDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context

    @Test
    fun fullResolutionJpegAndHeifRenderToLosslessPng() = runBlocking {
        val evidence = JSONArray()
        for (name in listOf("sample_orientation6.jpg", "sample_orientation6.heic")) {
            val uri = fixtureUri(name)
            val started = System.nanoTime()
            val source = PhotoIO.decode(context, uri, 0)
            assertEquals(2304, source.width)
            assertEquals(3456, source.height)
            val settings = EditSettings(strength = .8f, exposure = .25f, sharpen = .35f,
                hsl = HslSettings(red = HslChannel(saturation = -.2f)))
            val film = withContext(NativeCore.dispatcher) { NativeCore.render(context, source, Looks.first(),
                settings.grain, settings.filmExposure, ProcessingStage.EXPORT,
                settings.autoFilmExposure, settings.autoFilmExposureMethod) }
            val finished = PhotoIO.finish(source, film, settings)
            val output = PhotoIO.save(context, finished, share = true, lossless = true)
            val readback = context.contentResolver.openInputStream(output).use { input ->
                requireNotNull(BitmapFactory.decodeStream(input))
            }
            assertEquals(2304, readback.width)
            assertEquals(3456, readback.height)
            assertSamePixels(finished, readback)
            evidence.put(JSONObject().put("fixture", name).put("width", finished.width)
                .put("height", finished.height).put("elapsed_ms", (System.nanoTime() - started) / 1_000_000.0)
                .put("pss_kib", android.os.Debug.getPss()).put("png_uri", output.toString()))
            readback.recycle(); finished.recycle(); film.recycle(); source.recycle()
        }
        File(context.filesDir, "fullres-v032.json").writeText(evidence.toString(2))
    }

    private fun fixtureUri(name: String): Uri {
        val file = File(context.cacheDir, "exports/fullres-$name").apply {
            parentFile?.mkdirs()
            testContext.assets.open(name).use { input -> outputStream().use { output -> input.copyTo(output) } }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun assertSamePixels(expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val expectedRow = IntArray(expected.width)
        val actualRow = IntArray(actual.width)
        for (y in 0 until expected.height) {
            expected.getPixels(expectedRow, 0, expected.width, 0, y, expected.width, 1)
            actual.getPixels(actualRow, 0, actual.width, 0, y, actual.width, 1)
            assertTrue("PNG pixel mismatch at row $y", expectedRow.contentEquals(actualRow))
        }
    }
}
