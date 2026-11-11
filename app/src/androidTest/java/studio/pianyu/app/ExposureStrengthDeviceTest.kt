package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExposureStrengthDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun newDefaultsAndLegacyRecipesKeepTheirExposureIntent() {
        val fresh = EditSettings()
        assertEquals(3, fresh.lookVersion)
        assertEquals(.75f, fresh.strength, 0f)
        assertTrue(fresh.autoFilmExposure)
        assertEquals("protect_highlights", fresh.autoFilmExposureMethod)
        assertEquals(fresh, settingsFromJson(settingsToJson(fresh)))
        for (method in listOf("center_weighted", "balanced", "protect_highlights")) {
            val recipe = fresh.copy(autoFilmExposureMethod = method)
            assertEquals(recipe, settingsFromJson(settingsToJson(recipe)))
            assertEquals(method, fresh.withColorRecipe(recipe).autoFilmExposureMethod)
        }
        assertEquals("center_weighted", settingsFromJson(JSONObject().put("autoFilmExposure", true)).autoFilmExposureMethod)
        assertEquals("protect_highlights", settingsFromJson(JSONObject().put("autoFilmExposureMethod", "invalid")).autoFilmExposureMethod)
        val old = settingsFromJson(JSONObject().put("lookVersion", 2).put("strength", 1.0))
        assertFalse(old.autoFilmExposure)
        assertEquals(2, old.lookVersion)
        assertEquals(.75f, old.displayedStrength, 0f)
        assertEquals(old, settingsFromJson(settingsToJson(old)))
        assertTrue(old.withColorRecipe(fresh).autoFilmExposure)
        assertFalse(fresh.withColorRecipe(old).autoFilmExposure)
        val params = JSONObject(Looks.first().params(filmExposure = .6f, autoFilmExposure = true))
        assertTrue(params.getBoolean("auto_exposure"))
        assertEquals("protect_highlights", params.getString("auto_exposure_method"))
        assertEquals(.6, params.getDouble("exposure_compensation_ev"), .00001)
    }

    /** Actual Android outputs: the same native frames feed the 75 and 100 CPU finishes. */
    @Test fun renderExposureAndStrengthComparisons() = runBlocking {
        val directory = File(context.filesDir, "exposure-strength-v11").apply { mkdirs() }
        val rows = JSONArray()
        for (scene in listOf("portrait", "phone-landscape", "concert", "night", "candid")) {
            val bytes = instrumentation.context.assets.open("showcase/$scene.png").use { it.readBytes() }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val factor = minOf(1f, 640f / maxOf(decoded.width, decoded.height))
            val source = Bitmap.createScaledBitmap(decoded, (decoded.width * factor).toInt(),
                (decoded.height * factor).toInt(), true)
            if (source !== decoded) decoded.recycle()
            fun save(id: String, bitmap: Bitmap, settings: EditSettings?) {
                val file = File(directory, "$scene-$id.png")
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                rows.put(JSONObject().put("scene", scene).put("id", id)
                    .put("input_sha256", sha(bytes)).put("output_sha256", sha(file.readBytes()))
                    .put("width", bitmap.width).put("height", bitmap.height)
                    .put("settings", settings?.let(::settingsToJson))
                    .put("params", settings?.let { JSONObject(Looks[it.lookIndex].params(it.grain, it.filmExposure, it.autoFilmExposure, it.autoFilmExposureMethod)) }))
            }
            try {
                save("source", source, null)
                for ((index, look) in Looks.withIndex()) {
                    val recommended = EditSettings(lookIndex = index)
                    val off = withContext(NativeCore.dispatcher) { NativeCore.render(context, source, look,
                        recommended.grain, autoFilmExposure = false) }
                    val on = withContext(NativeCore.dispatcher) { NativeCore.render(context, source, look,
                        recommended.grain, autoFilmExposure = true) }
                    try {
                        if (scene == "concert" && index == 0) {
                            val center = withContext(NativeCore.dispatcher) { NativeCore.render(context, source, look,
                                recommended.grain, autoFilmExposure = true, autoFilmExposureMethod = "center_weighted") }
                            try { assertFalse("Meter method must affect the actual native output", center.sameAs(on)) }
                            finally { center.recycle() }
                        }
                        val noMeter = recommended.copy(autoFilmExposure = false)
                        val a = PhotoIO.finish(source, off, noMeter)
                        val legacy = PhotoIO.finish(source, off, noMeter.copy(lookVersion = 2, strength = 1f))
                        val b = PhotoIO.finish(source, on, recommended)
                        val strongSettings = recommended.copy(strength = 1f)
                        val c = PhotoIO.finish(source, on, strongSettings)
                        try {
                            assertTrue("75 must preserve v2 recommendation: $scene/${look.id}", a.sameAs(legacy))
                            assertFalse("100 must add visible processing: $scene/${look.id}", b.sameAs(c))
                            if (look.id == "softmono") assertMonochromeAndOrdered(b, c)
                            if (scene == "portrait" && index == 0) assertFalse("meter toggle did not change rendering", a.sameAs(b))
                            save("${look.id}-75-off", a, noMeter)
                            save("${look.id}-75-auto", b, recommended)
                            save("${look.id}-100-auto", c, strongSettings)
                        } finally { a.recycle(); legacy.recycle(); b.recycle(); c.recycle() }
                    } finally { off.recycle(); on.recycle() }
                }
                File(directory, "manifest.json").writeText(rows.toString(2))
            } finally { source.recycle() }
        }
        assertEquals(110, rows.length())
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun assertMonochromeAndOrdered(a: Bitmap, b: Bitmap) {
        var sumA = 0.0; var sumB = 0.0; var product = 0.0; var count = 0
        for (y in 0 until a.height step 4) for (x in 0 until a.width step 4) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            for (pixel in listOf(p, q)) {
                val channels = listOf((pixel shr 16) and 255, (pixel shr 8) and 255, pixel and 255)
                assertTrue("Full black-and-white must not mix color back in", channels.max() - channels.min() <= 1)
            }
            val u = (p and 255).toDouble(); val v = (q and 255).toDouble()
            sumA += u; sumB += v; product += u * v; count++
        }
        assertTrue("Stronger monochrome must preserve luminance direction", product - sumA * sumB / count > 0)
    }
}
