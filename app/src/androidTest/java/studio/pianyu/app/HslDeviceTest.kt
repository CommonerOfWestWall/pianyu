package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HslDeviceTest {
    private fun bitmap(vararg colors: Int): Bitmap = Bitmap.createBitmap(colors, colors.size, 1, Bitmap.Config.ARGB_8888)

    @Test fun zeroHslIsExactIdentityAndAlphaIsPreserved() {
        val source = bitmap(0x00112233, 0x80445566.toInt(), 0xff778899.toInt())
        val result = PhotoIO.finish(source, source, EditSettings(strength = 1f, lookVersion = 0))
        assertEquals(source.width, result.width)
        val a = IntArray(3); val b = IntArray(3); source.getPixels(a, 0, 3, 0, 0, 3, 1); result.getPixels(b, 0, 3, 0, 0, 3, 1)
        assertTrue(a.contentEquals(b))
    }

    @Test fun redEditDoesNotChangeRemoteBlueAndGrayStaysNeutral() {
        val source = bitmap(0xffff3030.toInt(), 0xffaaa0d2.toInt(), 0xff808080.toInt())
        val settings = EditSettings(strength = 1f, lookVersion = 0, hsl = HslSettings(red = HslChannel(hue = .8f, saturation = .8f)))
        val result = PhotoIO.finish(source, source, settings)
        val pixels = IntArray(3); result.getPixels(pixels, 0, 3, 0, 0, 3, 1)
        assertNotEquals(source.getPixel(0, 0), pixels[0])
        assertEquals(source.getPixel(1, 0), pixels[1])
        val gray = pixels[2] and 0xffffff
        assertEquals((gray shr 16) and 255, (gray shr 8) and 255)
        assertEquals((gray shr 8) and 255, gray and 255)
    }

    @Test fun jsonRoundTripClampAndLegacyDefaults() {
        val original = EditSettings(hsl = HslSettings(magenta = HslChannel(-.4f, .6f, 1f)))
        assertEquals(original, settingsFromJson(settingsToJson(original)))
        val malformed = JSONObject("{\"hsl\":{\"red\":{\"hue\":99,\"saturation\":\"bad\",\"lightness\":-99}}}")
        val parsed = settingsFromJson(malformed).hsl.red
        assertEquals(1f, parsed.hue)
        assertEquals(0f, parsed.saturation)
        assertEquals(-1f, parsed.lightness)
        assertEquals(HslSettings(), settingsFromJson(JSONObject()).hsl)
    }

    @Test fun colorRecipeKeepsHslAndOwnGeometry() {
        val settings = EditSettings(cropAspect = 1.4f, crop = CropRect(.1f, .2f, .8f, .9f), hsl = HslSettings(blue = HslChannel(lightness = .3f)))
        val recipe = settings.colorRecipe()
        assertEquals(settings.hsl, recipe.hsl)
        assertEquals(CropRect(), recipe.crop)
        assertEquals(settings.crop, settings.withColorRecipe(recipe).crop)
    }
    @Test fun circularHueWeightChangesSmoothlyNearRedWrap() {
        val settings = EditSettings(strength = 1f, hsl = HslSettings(red = HslChannel(hue = .8f)))
        val left = bitmap(0xffff0001.toInt())
        val right = bitmap(0xffff0100.toInt())
        val a = PhotoIO.finish(left, left, settings).getPixel(0, 0)
        val b = PhotoIO.finish(right, right, settings).getPixel(0, 0)
        assertTrue(kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) < 16)
    }

    @Test fun activeHslPreservesAlphaAndYellowUsesSixtyDegreeAnchor() {
        val source = bitmap(0x80ffff00.toInt())
        val settings = EditSettings(strength = 1f, hsl = HslSettings(yellow = HslChannel(lightness = -.5f)))
        val result = PhotoIO.finish(source, source, settings).getPixel(0, 0)
        assertEquals(0x80, result ushr 24)
        assertTrue((result and 0xff) < 16)
        assertTrue(((result shr 16) and 255) < 255)
    }
    @Test fun writesHslVisualEvidenceAndSyntheticIsolationChart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(checkNotNull(context.getExternalFilesDir(null)), "hsl-evidence-v03").apply { mkdirs() }
        val demo = PhotoIO.decode(context, null, 960)
        writePng(demo, File(dir, "before.png"))
        val redStart = System.nanoTime()
        val red = PhotoIO.finish(demo, demo, EditSettings(strength = 0f,
            hsl = HslSettings(red = HslChannel(saturation = -.6f, lightness = -.1f))))
        val redElapsed = System.nanoTime() - redStart
        writePng(red, File(dir, "red-desaturated.png"))
        val green = PhotoIO.finish(demo, demo, EditSettings(strength = 0f,
            hsl = HslSettings(green = HslChannel(saturation = -.4f))))
        writePng(green, File(dir, "greens-muted.png"))
        val colors = intArrayOf(0xffff0000.toInt(), 0xffff8000.toInt(), 0xffffff00.toInt(), 0xff00ff00.toInt(),
            0xff00ffff.toInt(), 0xff0000ff.toInt(), 0xff8000ff.toInt(), 0xffff00ff.toInt(), 0xffaaa0d2.toInt(), 0xff808080.toInt())
        val chart = strip(colors)
        val baselineStart = System.nanoTime()
        val baseline = PhotoIO.finish(chart, chart, EditSettings(strength = 0f))
        val baselineElapsed = System.nanoTime() - baselineStart
        val chartStart = System.nanoTime()
        val chartAfter = PhotoIO.finish(chart, chart, EditSettings(strength = 0f, hsl = HslSettings(red = HslChannel(saturation = -.8f))))
        val chartElapsed = System.nanoTime() - chartStart
        writePng(chart, File(dir, "chart-before.png")); writePng(chartAfter, File(dir, "chart-after-red.png"))
        assertNotEquals(chart.getPixel(0, 0), chartAfter.getPixel(0, 0))
        assertEquals(chart.getPixel(8 * 96, 0), chartAfter.getPixel(8 * 96, 0))
        assertEquals(chart.getPixel(9 * 96, 0), chartAfter.getPixel(9 * 96, 0))
        File(dir, "metadata.json").writeText(JSONObject().apply {
            put("demoWidth", demo.width); put("demoHeight", demo.height); put("baselineNanos", baselineElapsed)
            put("hslChartNanos", chartElapsed); put("demoRedHslNanos", redElapsed); put("chartWidth", chart.width); put("chartHeight", chart.height)
        }.toString())
        baseline.recycle(); chartAfter.recycle(); chart.recycle(); red.recycle(); green.recycle(); demo.recycle()
    }

    private fun strip(colors: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(colors.size * 96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val paint = Paint()
        colors.forEachIndexed { index, color -> paint.color = color; canvas.drawRect(index * 96f, 0f, (index + 1) * 96f, 96f, paint) }
        return bitmap
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
    }
}
