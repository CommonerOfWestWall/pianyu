package studio.pianyu.app

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context

    @Test
    fun fullSizePhonePipelineTimings() = runBlocking<Unit> {
        val rows = JSONArray()
        for (edge in listOf(960, 1280, 2048)) {
            val decodeStarted = System.nanoTime()
            val original = decodeFixture("sample_orientation6.heic", edge)
            val decodeMs = (System.nanoTime() - decodeStarted) / 1_000_000.0
            for (iteration in 0..2) {
                val started = System.nanoTime()
                val rendered = withContext(NativeCore.dispatcher) {
                    NativeCore.render(context, original, Looks.first())
                }
                val renderMs = (System.nanoTime() - started) / 1_000_000.0
                val finishStarted = System.nanoTime()
                val finished = PhotoIO.finish(original, rendered, EditSettings())
                val uri = PhotoIO.save(context, finished, share = true)
                val check = BitmapFactoryCompat.decode(context, uri)
                assertEquals(original.width, check.width)
                assertEquals(original.height, check.height)
                rows.put(JSONObject().apply {
                    put("edge", edge); put("iteration", iteration); put("decode_ms", decodeMs)
                    put("render_ms", renderMs)
                    put("finish_save_readback_ms", (System.nanoTime() - finishStarted) / 1_000_000.0)
                    put("width", finished.width); put("height", finished.height)
                    put("pss_snapshot_kib", android.os.Debug.getPss())
                })
                check.recycle(); finished.recycle(); rendered.recycle()
            }
            original.recycle()
        }
        File(context.getExternalFilesDir(null), "pipeline-evidence/full-size-timings.json").apply {
            parentFile?.mkdirs(); writeText(rows.toString(2))
        }
    }

    @Test
    fun photoIoAndAllLooksProduceEvidence() = runBlocking {
        val jpeg = decodeFixture("sample_orientation6.jpg", 1280)
        val heif = decodeFixture("sample_orientation6.heic", 1280)
        checkDecoded(jpeg, "jpeg", 1280)
        checkDecoded(heif, "heif", 1280)
        val jpeg2048 = decodeFixture("sample_orientation6.jpg", 2048)
        val heif2048 = decodeFixture("sample_orientation6.heic", 2048)
        checkDecoded(jpeg2048, "jpeg", 2048)
        checkDecoded(heif2048, "heif", 2048)
        assertEquals(jpeg.width, heif.width)
        assertEquals(jpeg.height, heif.height)

        val renderSource = Bitmap.createScaledBitmap(jpeg, 171, 256, true)
        val evidence = File(context.filesDir, "test-evidence").apply { mkdirs() }
        val rows = JSONArray()
        for (look in Looks) {
            val started = System.nanoTime()
            val result = withContext(NativeCore.dispatcher) {
                NativeCore.render(context, renderSource, look)
            }
            val elapsed = (System.nanoTime() - started) / 1_000_000.0
            assertTrue("empty ${look.id}", result.width > 0 && result.height > 0)
            val stats = rgbStats(result)
            assertTrue("flat ${look.id}", stats[3] > 0.5)
            if (look.id == "softmono" || look.id == "documentary") {
                assertTrue("not monochrome ${look.id}: ${stats[4]}", stats[4] <= 2.0)
            }
            File(evidence, "${look.id}.png").outputStream().use {
                check(result.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            rows.put(JSONObject().apply {
                put("id", look.id); put("elapsed_ms", elapsed)
                put("mean_rgb", JSONArray(listOf(stats[0], stats[1], stats[2])))
                put("stddev", stats[3]); put("mean_channel_delta", stats[4])
                put("width", result.width); put("height", result.height)
            })
            result.recycle()
        }
        File(evidence, "looks.json").writeText(rows.toString(2))
        val grain = withContext(NativeCore.dispatcher) { NativeCore.render(context, renderSource, Looks.first(), .35f) }
        assertTrue("grain result empty", grain.width > 0 && grain.height > 0)
        grain.recycle()
        val night = withContext(NativeCore.dispatcher) { NativeCore.render(context, renderSource, Looks.first { it.id == "night" }) }
        assertTrue("night result empty", night.width > 0 && night.height > 0)
        night.recycle()

        val red = Bitmap.createBitmap(jpeg.width, jpeg.height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        val zero = PhotoIO.finish(jpeg, red, EditSettings(strength = 0f))
        val originalPixels = pixels(jpeg)
        val zeroPixels = pixels(zero)
        val zeroDiagnostic = identityDiagnostic("strength_zero", jpeg, zero, originalPixels, zeroPixels)
        File(evidence, "identity-diagnostic.json").writeText(zeroDiagnostic.toString(2))
        Log.i("PianyuPipelineTest", zeroDiagnostic.toString())
        assertEquals("strength zero pixel count", originalPixels.size, zeroPixels.size)
        assertTrue("strength zero changed pixels", originalPixels.contentEquals(zeroPixels))
        zero.recycle()
        val full = PhotoIO.finish(jpeg, red, EditSettings(strength = 1f))
        val redPixels = pixels(red)
        val fullPixels = pixels(full)
        val fullDiagnostic = identityDiagnostic("strength_one", red, full, redPixels, fullPixels)
        File(evidence, "strength-one-diagnostic.json").writeText(fullDiagnostic.toString(2))
        File(evidence, "identity-diagnostic.json").writeText(JSONObject().apply {
            put("strength_zero", zeroDiagnostic)
            put("strength_one", fullDiagnostic)
        }.toString(2))
        Log.i("PianyuPipelineTest", fullDiagnostic.toString())
        assertEquals("strength one pixel count", redPixels.size, fullPixels.size)
        assertTrue("strength one did not select filtered pixels", redPixels.contentEquals(fullPixels))
        full.recycle()
        val cropped = PhotoIO.finish(jpeg, red, EditSettings(strength = 0f, cropAspect = .8f))
        val expectedCropWidth = minOf(jpeg.width, kotlin.math.round(jpeg.height * .8f).toInt())
        val expectedCropHeight = minOf(jpeg.height, kotlin.math.round(jpeg.width / .8f).toInt())
        assertEquals(expectedCropWidth, cropped.width)
        assertEquals(expectedCropHeight, cropped.height)
        cropped.recycle()
        val horizontal = Bitmap.createBitmap(1280, 853, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }
        val horizontalCrop = PhotoIO.finish(horizontal, red, EditSettings(strength = 0f, cropAspect = 1.5f))
        assertEquals(1280, horizontalCrop.width)
        assertEquals(kotlin.math.round(1280f / 1.5f).toInt(), horizontalCrop.height)
        horizontalCrop.recycle(); horizontal.recycle(); red.recycle()

        val share = PhotoIO.save(context, jpeg, share = true)
        val saved = BitmapFactoryCompat.decode(context, share)
        assertEquals(jpeg.width, saved.width)
        assertEquals(jpeg.height, saved.height)
        saved.recycle()
        File(evidence, "pipeline.json").writeText(JSONObject().apply {
            put("fixture", "sample_orientation6")
            put("jpeg_decoded", dimensions(jpeg)); put("heif_decoded", dimensions(heif))
            put("looks", rows); put("share_uri", share.toString())
        }.toString(2))
    }

    private fun decodeFixture(name: String, edge: Int): Bitmap {
        val file = File(context.cacheDir, "exports/$name").apply {
            parentFile?.mkdirs()
            testContext.assets.open(name).use { input -> outputStream().use(input::copyTo) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return PhotoIO.decode(context, uri, edge)
    }

    private fun checkDecoded(bitmap: Bitmap, label: String, edge: Int) {
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
        assertTrue("$label orientation not applied", bitmap.width < bitmap.height)
        val expectedWidth = kotlin.math.round(edge * (2304f / 3456f)).toInt()
        assertEquals("$label width", expectedWidth, bitmap.width)
        assertEquals("$label height", edge, bitmap.height)
        assertTrue("$label not sRGB", bitmap.colorSpace?.isSrgb == true)
    }

    private fun dimensions(bitmap: Bitmap) = JSONArray(listOf(bitmap.width, bitmap.height))

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun identityDiagnostic(name: String, expected: Bitmap, actual: Bitmap, expectedPixels: IntArray, actualPixels: IntArray): JSONObject {
        var changed = 0
        var maxDelta = 0
        val count = minOf(expectedPixels.size, actualPixels.size)
        for (index in 0 until count) {
            if (expectedPixels[index] != actualPixels[index]) changed++
            val a = expectedPixels[index]; val b = actualPixels[index]
            maxDelta = maxOf(maxDelta, kotlin.math.abs(((a shr 16) and 255) - ((b shr 16) and 255)), kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255)), kotlin.math.abs((a and 255) - (b and 255)), kotlin.math.abs((a ushr 24) - (b ushr 24)))
        }
        return JSONObject().apply {
            put("name", name); put("changed_count", changed); put("max_channel_delta", maxDelta)
            put("expected_size", dimensions(expected)); put("actual_size", dimensions(actual))
            put("expected_config", expected.config?.name); put("actual_config", actual.config?.name)
            put("expected_has_alpha", expected.hasAlpha()); put("actual_has_alpha", actual.hasAlpha())
            put("expected_premultiplied", expected.isPremultiplied); put("actual_premultiplied", actual.isPremultiplied)
            put("expected_color_space", expected.colorSpace?.name); put("actual_color_space", actual.colorSpace?.name)
        }
    }

    private fun rgbStats(bitmap: Bitmap): DoubleArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var r = 0.0; var g = 0.0; var b = 0.0; var delta = 0.0
        for (pixel in pixels) { val rr = (pixel shr 16) and 255; val gg = (pixel shr 8) and 255; val bb = pixel and 255; r += rr; g += gg; b += bb; delta += (kotlin.math.abs(rr - gg) + kotlin.math.abs(gg - bb)) / 2.0 }
        val n = pixels.size.toDouble()
        val mean = (r + g + b) / (3.0 * n)
        var variance = 0.0
        for (pixel in pixels) variance += (((pixel shr 16) and 255) - mean) * (((pixel shr 16) and 255) - mean)
        return doubleArrayOf(r / n, g / n, b / n, kotlin.math.sqrt(variance / n), delta / n)
    }
}

private object BitmapFactoryCompat {
    fun decode(context: android.content.Context, uri: Uri): Bitmap =
        context.contentResolver.openInputStream(uri).use { requireNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
}
