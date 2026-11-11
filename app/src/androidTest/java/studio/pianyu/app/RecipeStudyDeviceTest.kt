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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Runs supplied recipes through the real Android/native pipeline in an isolated filesDir. */
@RunWith(AndroidJUnit4::class)
class RecipeStudyDeviceTest {
    @Test
    fun renderRecipes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val study = InstrumentationRegistry.getArguments().getString("study", "default")!!
        require(study.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid study name" }
        val directory = File(context.filesDir, "recipe-study/$study")
        val inputFile = File(directory, "input.png")
        val recipesFile = File(directory, "recipes.json")
        require(inputFile.isFile) { "missing recipe-study/$study/input.png" }
        require(recipesFile.isFile) { "missing recipe-study/$study/recipes.json" }
        val outputDirectory = File(directory, "outputs").apply { mkdirs() }
        val recipes = JSONArray(recipesFile.readText())
        val inputBytes = inputFile.readBytes()
        val inputSha = sha256(inputBytes)
        val original = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
        assertNotNull("input.png is not decodable", original)
        val decoded = original!!
        assertTrue("recipe study input must be sRGB-sized", decoded.width <= 1536 && decoded.height <= 1536)
        val originalArgb = if (decoded.config == Bitmap.Config.ARGB_8888) decoded
            else decoded.copy(Bitmap.Config.ARGB_8888, false)
        if (originalArgb !== decoded) decoded.recycle()
        outputDirectory.resolve("original.png").outputStream().use {
            assertTrue("could not save original.png", originalArgb.compress(Bitmap.CompressFormat.PNG, 100, it))
        }

        val rows = JSONArray()
        for (index in 0 until recipes.length()) {
                val recipe = recipes.getJSONObject(index)
                val id = recipe.getString("id")
                require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid recipe id: $id" }
                val settings = settingsFromJson(recipe.getJSONObject("settings"))
                assertEquals("recipe study must not apply crop", 0f, settings.cropAspect, .0001f)
                assertEquals("recipe study must not rotate", 0, settings.quarterTurns)
                assertEquals("recipe study must keep the full frame", CropRect(), settings.crop)
                assertEquals("recipe study must not straighten", 0f, settings.straightenDegrees, .0001f)
                assertTrue("recipe study must not flip", !settings.flipHorizontal && !settings.flipVertical)
                val start = System.nanoTime()
                val film = withContext(NativeCore.dispatcher) {
                    NativeCore.render(context, originalArgb, Looks[settings.lookIndex], settings.grain, settings.filmExposure, autoFilmExposure = settings.autoFilmExposure, autoFilmExposureMethod = settings.autoFilmExposureMethod)
                }
                val output = try {
                    PhotoIO.finish(originalArgb, film, settings)
                } finally {
                    film.recycle()
                }
                try {
                    assertEquals(originalArgb.width, output.width)
                    assertEquals(originalArgb.height, output.height)
                    val outputFile = File(outputDirectory, "$id.png")
                    outputFile.outputStream().use {
                        assertTrue("could not save $id.png", output.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    rows.put(JSONObject().put("id", id)
                        .put("width", output.width).put("height", output.height)
                        .put("input_sha256", inputSha).put("output_sha256", sha256(outputFile.readBytes()))
                        .put("settings", settingsToJson(settings))
                        .put("effective_finish", settingsToJson(Looks[settings.lookIndex].finishSettings(settings)))
                        .put("params", JSONObject(Looks[settings.lookIndex].params(settings.grain, settings.filmExposure, settings.autoFilmExposure, settings.autoFilmExposureMethod)))
                        .put("elapsed_ms", (System.nanoTime() - start) / 1_000_000.0))
                    File(directory, "manifest.json").writeText(rows.toString(2))
                } finally {
                    output.recycle()
                }
        }
        originalArgb.recycle()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
