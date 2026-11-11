package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HeifExportDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context

    @Test fun heifColorBlocksHaveContainerMimeExtensionAndRoundTrip() {
        assertTrue("device has no HEVC encoder", HeifExport.isEncoderAvailable())
        val input = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until input.height) for (x in 0 until input.width)
            input.setPixel(x, y, when { x < 32 -> Color.RED; x < 64 -> Color.GREEN; else -> Color.BLUE })
        val uri = PhotoIO.save(context, input, share = true, format = ExportFormat.HEIF)
        val bytes = context.contentResolver.openInputStream(uri).use { requireNotNull(it).readBytes() }
        assertEquals("ftyp", String(bytes, 4, 4, Charsets.US_ASCII))
        assertTrue("HEIF brand missing", String(bytes, 8, minOf(bytes.size - 8, 32), Charsets.US_ASCII).contains("heic") ||
            String(bytes, 8, minOf(bytes.size - 8, 32), Charsets.US_ASCII).contains("mif1"))
        assertEquals("image/heic", context.contentResolver.getType(uri))
        assertTrue(cacheFile(uri).name.endsWith(".heic"))
        val output = context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it)) }
        assertEquals(input.width, output.width); assertEquals(input.height, output.height)
        fun rgb(x: Int, y: Int) = output.getPixel(x, y)
        assertColorNear(rgb(16, 32), Color.RED, "red")
        assertColorNear(rgb(48, 32), Color.GREEN, "green")
        assertColorNear(rgb(80, 32), Color.BLUE, "blue")
        output.recycle(); input.recycle(); cacheFile(uri).delete()
    }

    @Test fun orientationSixJpegRendersAndExportsHeifAtOriginalSize() = runBlocking {
        assertTrue("device has no HEVC encoder", HeifExport.isEncoderAvailable())
        for (name in listOf("sample_orientation6.jpg", "sample_orientation6.heic")) {
            var source: Bitmap? = null; var film: Bitmap? = null; var finished: Bitmap? = null; var decoded: Bitmap? = null
            var uri: android.net.Uri? = null
            try {
                source = decodeFixture(name)
                assertEquals(2304, source.width); assertEquals(3456, source.height)
                val settings = EditSettings(autoFilmExposure = true)
                film = withContext(NativeCore.dispatcher) {
                    NativeCore.render(context, source, Looks.first(), settings.grain, settings.filmExposure,
                        ProcessingStage.EXPORT, settings.autoFilmExposure, settings.autoFilmExposureMethod)
                }
                finished = PhotoIO.finish(source, film, settings)
                uri = PhotoIO.save(context, finished, share = true, format = ExportFormat.HEIF)
                decoded = context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it)) }
                assertEquals(finished.width, decoded.width); assertEquals(finished.height, decoded.height)
                assertTrue(decoded.width < decoded.height); assertEquals(Bitmap.Config.ARGB_8888, decoded.config)
            } finally {
                decoded?.recycle(); finished?.recycle(); film?.recycle(); source?.recycle()
                uri?.let { cacheFile(it).delete() }
            }
        }
    }

    @Test fun heifMediaStoreOutputIsReadableAndOwnRowCanBeDeleted() {
        assertTrue("device has no HEVC encoder", HeifExport.isEncoderAvailable())
        val input = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 120, 220)) }
        val uri = PhotoIO.save(context, input, share = false, format = ExportFormat.HEIF)
        try {
            assertEquals("image/heic", context.contentResolver.getType(uri))
            val name = context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
                .use { requireNotNull(it).run { moveToFirst(); getString(0) } }
            assertTrue(name.endsWith(".heic"))
            val output = context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it)) }
            assertEquals(input.width, output.width); assertEquals(input.height, output.height)
            output.recycle()
        } finally {
            context.contentResolver.delete(uri, null, null)
            input.recycle()
        }
    }

    private fun decodeFixture(name: String): Bitmap {
        val file = File(context.cacheDir, "exports/heif-test-$name").apply {
            parentFile?.mkdirs(); testContext.assets.open(name).use { input -> outputStream().use(input::copyTo) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return PhotoIO.decode(context, uri, 0)
    }

    private fun cacheFile(uri: android.net.Uri) = File(context.cacheDir, "exports/${requireNotNull(uri.lastPathSegment)}")

    private fun assertColorNear(actual: Int, expected: Int, label: String) {
        val channels = intArrayOf(Color.red(actual), Color.green(actual), Color.blue(actual))
        val wanted = intArrayOf(Color.red(expected), Color.green(expected), Color.blue(expected))
        for (i in 0..2) assertTrue("$label channel $i error", kotlin.math.abs(channels[i] - wanted[i]) <= 30)
        val dominant = wanted.indices.maxBy { wanted[it] }
        for (i in 0..2) if (i != dominant) assertTrue("$label channel separation", channels[dominant] - channels[i] >= 100)
    }
}
