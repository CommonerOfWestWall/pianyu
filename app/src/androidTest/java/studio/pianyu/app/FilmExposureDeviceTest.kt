package studio.pianyu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilmExposureDeviceTest {
    @Test fun oldRecipesKeepPostExposureAndNewRecipesRoundTrip() {
        val old = settingsFromJson(JSONObject().put("exposure", .8))
        assertEquals(.8f, old.exposure, .0001f)
        assertEquals(0f, old.filmExposure, 0f)
        val recipe = old.copy(filmExposure = -.65f, crop = CropRect(.1f,.1f,.9f,.9f))
        assertEquals(recipe, settingsFromJson(settingsToJson(recipe)))
        assertEquals(-.65f, recipe.colorRecipe().filmExposure, .0001f)
        assertEquals(CropRect(), recipe.colorRecipe().crop)
        assertEquals(2f, settingsFromJson(JSONObject().put("filmExposure", 9)).filmExposure, 0f)
        assertEquals(0.0, JSONObject(Looks.first().params(filmExposure = Float.NaN)).getDouble("exposure_compensation_ev"), 0.0)
    }

    @Test fun nativeExposureChangesFilmWithoutBeingAppliedTwice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = PhotoIO.decode(context, null, 640)
        withContext(NativeCore.dispatcher) {
            val look = Looks.first()
            val base = NativeCore.render(context, source, look)
            val film = NativeCore.render(context, source, look, filmExposure = .7f)
            try {
                assertFalse("pre-film exposure must change film response", base.sameAs(film))
                val settings = EditSettings(strength = 1f, lookVersion = 0, filmExposure = .7f)
                val finished = PhotoIO.finish(source, film, settings)
                val zero = PhotoIO.finish(source, film, settings.copy(strength = 0f))
                try {
                    assertTrue("film exposure must not run again in the CPU finish", finished.sameAs(film))
                    assertTrue("zero style strength must still bypass the film", zero.sameAs(source))
                } finally { finished.recycle(); zero.recycle() }
                assertEquals(.7, JSONObject(look.params(filmExposure = .7f)).getDouble("exposure_compensation_ev"), .0001)
            } finally { base.recycle(); film.recycle(); source.recycle() }
        }
    }
}
