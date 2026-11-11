package studio.pianyu.app

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecommendedRecipeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    @Test fun recommendedStartResetsColorKeepsGeometryAndCanUndo() {
        val app = IsolatedTestApplication.create(instrumentation.targetContext)
        lateinit var model: EditorModel
        try {
            onMain { model = EditorModel(app); model.loadDemo() }
            await { !model.state.loading && !model.state.rendering && model.state.source != null }
            val prior = EditSettings(lookIndex = 2, strength = .41f, grain = .72f,
                filmExposure = .65f, autoFilmExposure = false, exposure = -.4f,
                cropAspect = .8f, crop = CropRect(.1f, .2f, .9f, .95f), quarterTurns = 1,
                straightenDegrees = 4f, flipHorizontal = true, lookVersion = 3)
            onMain { model.edit(prior); model.finishAdjustment() }
            await { !model.state.rendering && model.state.settings == prior }
            onMain { model.applyRecommendedStart() }
            val applied = model.state.settings
            assertEquals(prior.lookIndex, applied.lookIndex)
            assertEquals(prior.cropAspect, applied.cropAspect)
            assertEquals(prior.crop, applied.crop)
            assertEquals(prior.quarterTurns, applied.quarterTurns)
            assertEquals(prior.straightenDegrees, applied.straightenDegrees)
            assertEquals(prior.flipHorizontal, applied.flipHorizontal)
            assertEquals(.75f, applied.strength)
            assertEquals(.125f, applied.grain)
            assertEquals(0f, applied.filmExposure)
            assertTrue(applied.autoFilmExposure)
            assertEquals(0f, applied.exposure)
            await { !model.state.rendering }
            onMain { model.undo() }
            assertEquals(prior, model.state.settings)
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
        throw AssertionError("recommended recipe condition timeout")
    }
}
