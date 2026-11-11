package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.security.MessageDigest

/** Produces public examples through the actual Android pipeline, without touching the library. */
@RunWith(AndroidJUnit4::class)
class ShowcaseDeviceTest {
    @Test fun renderPublicComparisons() = runBlocking {
        val test = InstrumentationRegistry.getInstrumentation()
        val context = test.targetContext
        val directory = File(context.filesDir, "showcase-v032").apply { mkdirs() }
        val rows = JSONArray()
        fun save(name: String, bitmap: Bitmap, values: JSONObject) {
            val file = File(directory, "$name.png")
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            values.put("file", file.name).put("width", bitmap.width).put("height", bitmap.height)
                .put("sha256", MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
            rows.put(values)
        }
        for (scene in listOf("portrait", "landscape", "night", "stilllife", "humanity", "phone-landscape", "concert", "candid")) {
            val bytes = if (scene == "stilllife") context.assets.open("demo.jpg").use { it.readBytes() }
                else test.context.assets.open("showcase/$scene.png").use { it.readBytes() }
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            save("$scene-original", original, JSONObject().put("scene", scene).put("type", "input").put("ai_generated_input", true))
            for ((index, look) in Looks.withIndex()) {
                val start = System.nanoTime()
                val film = withContext(NativeCore.dispatcher) { NativeCore.render(context, original, look) }
                val settings = EditSettings(lookIndex = index, strength = look.defaultStrength, lookVersion = 1, grain = 0f)
                val finished = PhotoIO.finish(original, film, settings)
                assertEquals(original.width, finished.width)
                assertEquals(original.height, finished.height)
                save("$scene-${look.id}", finished, JSONObject().put("scene", scene).put("look", look.id)
                    .put("strength", settings.strength).put("params", JSONObject(look.params()))
                    .put("settings", settingsToJson(settings))
                    .put("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0))
                finished.recycle()
                if (scene == "humanity" && index == 1) {
                    val tuned = settings.copy(strength=.55f, exposure=.15f, warmth=.08f, contrast=.05f, highlights=-.15f)
                    val output=PhotoIO.finish(original,film,tuned)
                    save("humanity-tuned",output,JSONObject().put("settings",settingsToJson(tuned)))
                    output.recycle()
                }
                if (scene == "phone-landscape" && index == 3) {
                    val tuned = settings.copy(exposure=.2f, shadows=.12f, warmth=.06f, saturation=.08f, highlights=-.15f)
                    val output=PhotoIO.finish(original,film,tuned)
                    save("phone-landscape-tuned",output,JSONObject().put("settings",settingsToJson(tuned)))
                    output.recycle()
                }
                if (scene == "concert" && index == 2) {
                    val tuned=settings.copy(strength=.45f, exposure=.15f, highlights=-.2f, warmth=-.05f, shadows=.08f)
                    val output=PhotoIO.finish(original,film,tuned)
                    save("concert-tuned",output,JSONObject().put("settings",settingsToJson(tuned)))
                    output.recycle()
                }
                if (scene == "candid" && index == 0) {
                    val tuned = settings.copy(strength=.8f, exposure=.2f, warmth=.08f, contrast=.08f,
                        highlights=-.12f, fade=.12f, grain=.25f)
                    val grained = withContext(NativeCore.dispatcher) { NativeCore.render(context, original, look, tuned.grain) }
                    val output = PhotoIO.finish(original, grained, tuned)
                    save("candid-tuned", output, JSONObject().put("settings",settingsToJson(tuned)))
                    grained.recycle(); output.recycle()
                    for (strength in listOf(.35f, 1f)) {
                        val strengthSettings = tuned.copy(strength = strength)
                        val strengthFilm = withContext(NativeCore.dispatcher) { NativeCore.render(context, original, look, strengthSettings.grain) }
                        val strengthOutput = PhotoIO.finish(original, strengthFilm, strengthSettings)
                        save("candid-strength-${(strength * 100).toInt()}", strengthOutput,
                            JSONObject().put("settings",settingsToJson(strengthSettings)))
                        strengthFilm.recycle(); strengthOutput.recycle()
                    }
                }
                if (scene == "candid" && index == 6) {
                    val tuned = settings.copy(strength=1f, exposure=.2f, contrast=.15f,
                        highlights=-.12f, fade=.08f, grain=.3f, vignette=.1f)
                    val grained = withContext(NativeCore.dispatcher) { NativeCore.render(context, original, look, tuned.grain) }
                    val output = PhotoIO.finish(original, grained, tuned)
                    save("candid-monochrome-tuned", output, JSONObject().put("settings",settingsToJson(tuned)))
                    grained.recycle(); output.recycle()
                }
                if (scene == "portrait" && index == 0) {
                    for (strength in listOf(.0f, .35f, 1f)) {
                        val output = PhotoIO.finish(original, film, settings.copy(strength = strength))
                        save("portrait-strength-${(strength * 100).toInt()}", output, JSONObject().put("strength", strength))
                        output.recycle()
                    }
                    for (exposure in listOf(-.5f, .5f)) {
                        val output = PhotoIO.finish(original, film, settings.copy(exposure = exposure))
                        save("portrait-exposure-$exposure", output, JSONObject().put("exposure_ev", exposure))
                        output.recycle()
                    }
                    val output = PhotoIO.finish(original, film, settings.copy(hsl = HslSettings(red = HslChannel(saturation = -.6f))))
                    save("portrait-hsl-red-minus60", output, JSONObject().put("red_saturation", -.6))
                    output.recycle()
                }
                if (scene == "landscape" && index == 3) {
                    val output = PhotoIO.finish(original, film, settings.copy(hsl = HslSettings(green = HslChannel(saturation = -.6f))))
                    save("landscape-hsl-green-minus60", output, JSONObject().put("green_saturation", -.6))
                    output.recycle()
                }
                if (scene == "stilllife" && index == 0) {
                    for (grain in listOf(.5f, 1f)) {
                        val grained = withContext(NativeCore.dispatcher) { NativeCore.render(context, original, look, grain) }
                        val output = PhotoIO.finish(original, grained, settings.copy(grain = grain))
                        save("stilllife-grain-${(grain * 100).toInt()}", output, JSONObject().put("grain", grain).put("strength", settings.strength))
                        grained.recycle(); output.recycle()
                    }
                }
                film.recycle()
            }
            original.recycle()
        }
        File(directory, "manifest.json").writeText(rows.toString(2))
    }
}
