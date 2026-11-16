package studio.pianyu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

@RunWith(AndroidJUnit4::class)
class FilmPrintDeviceTest {
    @Test fun legacyJsonDefaultsPrintFieldsAndMeteringRoundTrips() {
        val legacy = settingsFromJson(JSONObject().put("lookIndex", 0).put("autoFilmExposure", true)
            .put("autoFilmExposureMethod", "center_weighted"))
        assertEquals("", legacy.printStock)
        assertEquals(0f, legacy.printExposure, 0f)
        assertEquals(Looks.first().paper, Looks.first().effectivePrintStock(legacy.printStock))
        assertEquals("center_weighted", legacy.autoFilmExposureMethod)
        for (method in listOf("balanced", "center", "protect_highlights", "protect_shadows", "center_weighted")) {
            assertEquals(method, settingsFromJson(settingsToJson(EditSettings(autoFilmExposureMethod = method))).autoFilmExposureMethod)
        }
        assertEquals("protect_highlights",
            settingsFromJson(JSONObject().put("autoFilmExposureMethod", "not-a-meter")).autoFilmExposureMethod)
    }

    @Test fun printStockAndExposureRecipeRoundTripKeepsComposition() {
        val settings = EditSettings(printStock = "kodak_ultra_endura", printExposure = .5f,
            crop = CropRect(.1f, .2f, .8f, .9f), quarterTurns = 1, flipHorizontal = true)
        val restored = settingsFromJson(settingsToJson(settings))
        assertEquals(settings, restored)
        val recipe = restored.colorRecipe()
        assertEquals(CropRect(), recipe.crop)
        assertEquals(0, recipe.quarterTurns)
        assertFalse(recipe.flipHorizontal)
        assertEquals(settings.printStock, recipe.printStock)
        assertEquals(settings.printExposure, recipe.printExposure, 0f)
        val destination = EditSettings(crop = CropRect(.2f, .1f, .9f, .8f), quarterTurns = 3)
        val applied = destination.withColorRecipe(recipe)
        assertEquals(destination.crop, applied.crop)
        assertEquals(destination.quarterTurns, applied.quarterTurns)
    }

    @Test fun lookParamsSeparateFilmPrintAndPositiveScanExposure() {
        val look = Looks.first()
        val halfPrint = JSONObject(look.params(filmExposure = .25f, printExposure = .5f))
        assertEquals(.25, halfPrint.getDouble("exposure_compensation_ev"), .0001)
        assertEquals(2.0.pow(.5), halfPrint.getDouble("print_exposure"), .0001)
        val positive = JSONObject(Looks.first { it.positive }.params(filmExposure = .5f,
            printStock = "kodak_ultra_endura", printExposure = .75f))
        assertEquals(.5, positive.getDouble("exposure_compensation_ev"), .0001)
        assertEquals(1.0, positive.getDouble("print_exposure"), 0.0)
        assertEquals("fujifilm_provia_100f", positive.getString("film_stock"))
        assertEquals("kodak_portra_endura", positive.getString("print_stock"))
        val nonFinite = JSONObject(look.params(filmExposure = Float.NaN, printExposure = Float.NaN))
        assertEquals(0.0, nonFinite.getDouble("exposure_compensation_ev"), 0.0)
        assertEquals(1.0, nonFinite.getDouble("print_exposure"), 0.0)
        assertEquals(2.0.pow(2.0), JSONObject(look.params(printExposure = 9f)).getDouble("print_exposure"), .0001)
    }

    @Test fun nativePaperPrintExposureAndPositiveOverrideHaveExpectedBoundaries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = PhotoIO.decode(context, null, 640)
        withContext(NativeCore.dispatcher) {
            val look = Looks.first()
            val base = NativeCore.render(context, source, look, grain = 0f, autoFilmExposure = false)
            val ultra = NativeCore.render(context, source, look, grain = 0f, autoFilmExposure = false,
                printStock = "kodak_ultra_endura")
            val print = NativeCore.render(context, source, look, grain = 0f, autoFilmExposure = false,
                printExposure = .5f)
            val positive = Looks.first { it.positive }
            val positiveBase = NativeCore.render(context, source, positive, grain = 0f, autoFilmExposure = false)
            val positiveOverride = NativeCore.render(context, source, positive, grain = 0f,
                autoFilmExposure = false, printStock = "kodak_ultra_endura", printExposure = .75f)
            try {
                assertFalse("paper stock must affect native output", base.sameAs(ultra))
                assertFalse("print exposure must affect native output", base.sameAs(print))
                assertTrue("positive scan must ignore paper and print exposure", positiveBase.sameAs(positiveOverride))
            } finally {
                base.recycle(); ultra.recycle(); print.recycle(); positiveBase.recycle(); positiveOverride.recycle(); source.recycle()
            }
        }
    }
}
