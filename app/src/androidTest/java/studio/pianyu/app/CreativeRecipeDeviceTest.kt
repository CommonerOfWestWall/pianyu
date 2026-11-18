package studio.pianyu.app

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreativeRecipeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Test fun applyingCreativeRecipeKeepsGeometrySupportsHistoryAndPersistsSavedCopy() {
        val app = IsolatedTestApplication.create(instrumentation.targetContext)
        lateinit var model: EditorModel
        try {
            onMain { model = EditorModel(app); model.loadDemo() }
            await { !model.state.loading && !model.state.rendering && model.state.source != null && !model.recipesLoading }

            val prior = EditSettings(lookIndex = 1, strength = .41f, grain = .62f,
                filmExposure = .35f, autoFilmExposure = false, exposure = -.4f,
                warmth = .22f, contrast = -.18f, saturation = -.2f,
                cropAspect = .8f, crop = CropRect(.1f, .2f, .9f, .95f), quarterTurns = 1,
                straightenDegrees = 4f, flipHorizontal = true, lookVersion = 3)
            onMain { model.edit(prior); model.finishAdjustment() }
            await { !model.state.rendering && model.state.settings == prior }

            val creative = CreativeRecipes.first { it.id == "teal-orange-night" }
            val expected = prior.withColorRecipe(creative.settings)
            onMain { model.applyCreativeRecipe("teal-orange-night") }
            await { !model.state.rendering && model.state.settings == expected }
            assertEquals(expected, model.state.settings)
            assertEquals(creative.settings.colorRecipe(), model.library.recipe)
            assertEquals(prior.crop, model.state.settings.crop)
            assertEquals(prior.quarterTurns, model.state.settings.quarterTurns)
            assertEquals(creative.settings.lookIndex, model.state.settings.lookIndex)
            assertEquals(creative.settings.hsl, model.state.settings.hsl)
            assertTrue(model.state.settings != prior)

            onMain { model.undo() }
            assertEquals(prior, model.state.settings)
            onMain { model.redo() }
            assertEquals(expected, model.state.settings)

            val beforeInvalid = model.state.settings
            onMain { model.applyCreativeRecipe("does-not-exist") }
            assertEquals(beforeInvalid, model.state.settings)

            onMain { assertTrue(model.saveRecipe("青橙夜色副本")) }
            await { RecipeStore(app).read().recipes.any { it.name == "青橙夜色副本" } }
            val saved = RecipeStore(app).read().recipes.first { it.name == "青橙夜色副本" }
            assertEquals(expected.colorRecipe(), saved.settings)
            assertFalse(saved.settings.cropAspect != 0f || saved.settings.quarterTurns != 0 || saved.settings.flipHorizontal)
        } finally {
            onMain { model.viewModelScope.cancel() }
            app.cleanup()
        }
    }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < end) {
            var passed = false
            onMain { passed = condition() }
            if (passed) return
            SystemClock.sleep(40)
        }
        throw AssertionError("creative recipe condition timeout")
    }
}
