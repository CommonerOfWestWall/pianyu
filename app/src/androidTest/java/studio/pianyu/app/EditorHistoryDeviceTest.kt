package studio.pianyu.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.content.Context
import android.app.Application
import java.io.File
import java.util.UUID
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorHistoryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun lookUndoDoesNotAcceptStaleRenderAndScalarHistoryRoundTrips() {
        val application = IsolatedTestApplication.create(instrumentation.targetContext)
        lateinit var model: EditorModel
        instrumentation.runOnMainSync { model = EditorModel(application).also { it.loadDemo() } }

        awaitIdle(model)
        val initial = readBitmap(model) ?: error("demo render completed without filtered bitmap")
        val initialCopy = initial.copy(Bitmap.Config.ARGB_8888, false)
        assertEquals(0, readState(model).settings.lookIndex)

        instrumentation.runOnMainSync {
            model.chooseLook(1)
            model.undo()
        }
        awaitIdle(model)
        val afterLookUndo = readBitmap(model) ?: error("look undo lost filtered bitmap")
        assertEquals("undo must restore the original look", 0, readState(model).settings.lookIndex)
        assertTrue("stale look render overwrote undo", afterLookUndo.sameAs(initialCopy))
        initialCopy.recycle()

        instrumentation.runOnMainSync {
            model.updateStrength(.2f)
            model.finishAdjustment()
        }
        assertEquals(.2f, readState(model).settings.strength, .0001f)
        instrumentation.runOnMainSync { model.undo() }
        assertEquals(.75f, readState(model).settings.strength, .0001f)
        instrumentation.runOnMainSync { model.redo() }
        assertEquals(.2f, readState(model).settings.strength, .0001f)

        instrumentation.runOnMainSync {
            model.updateBrightness(.2f)
            model.finishAdjustment()
        }
        assertEquals(.2f, readState(model).settings.brightness, .0001f)
        instrumentation.runOnMainSync { model.undo() }
        assertEquals(0f, readState(model).settings.brightness, .0001f)
        instrumentation.runOnMainSync { model.redo() }
        assertEquals(.2f, readState(model).settings.brightness, .0001f)
        awaitIdle(model)
        val beforeFilmExposure = readBitmap(model)!!.copy(Bitmap.Config.ARGB_8888, false)
        instrumentation.runOnMainSync {
            model.edit(model.state.settings.copy(filmExposure = .7f)); model.finishAdjustment()
        }
        awaitIdle(model)
        assertEquals(.7f, readState(model).displayedSettings!!.filmExposure, .0001f)
        val exposedFilm = readBitmap(model)!!.copy(Bitmap.Config.ARGB_8888, false)
        assertTrue("film exposure must invalidate the native frame", !exposedFilm.sameAs(beforeFilmExposure))
        instrumentation.runOnMainSync { model.undo() }
        awaitIdle(model)
        assertTrue("undo must restore the zero-exposure cached frame", readBitmap(model)!!.sameAs(beforeFilmExposure))
        instrumentation.runOnMainSync { model.redo() }
        awaitIdle(model)
        assertTrue("redo must restore the exposed cached frame", readBitmap(model)!!.sameAs(exposedFilm))
        instrumentation.runOnMainSync {
            model.edit(model.state.settings.copy(filmExposure = -.5f)); model.finishAdjustment(); model.undo()
        }
        awaitIdle(model)
        assertTrue("a stale exposure render must not overwrite undo", readBitmap(model)!!.sameAs(exposedFilm))
        beforeFilmExposure.recycle(); exposedFilm.recycle()
        val beforeFinishToggle = readState(model).settings
        val modelOnly = beforeFinishToggle.copy(lookVersion = 0,
            strength = Looks[beforeFinishToggle.lookIndex].filmStrength(beforeFinishToggle))
        instrumentation.runOnMainSync { model.setLookFinish(false) }
        awaitIdle(model)
        assertEquals(modelOnly, readState(model).settings)
        assertEquals(Looks[beforeFinishToggle.lookIndex].filmStrength(beforeFinishToggle),
            Looks[modelOnly.lookIndex].filmStrength(modelOnly), .00001f)
        instrumentation.runOnMainSync { model.undo() }
        awaitIdle(model)
        assertEquals(beforeFinishToggle, readState(model).settings)
        instrumentation.runOnMainSync { model.redo() }
        awaitIdle(model)
        assertEquals(modelOnly, readState(model).displayedSettings)
        instrumentation.runOnMainSync { model.undo() }
        awaitIdle(model)
        var intermediateFrames = 0
        for (step in 1..30) {
            instrumentation.runOnMainSync { model.edit(model.state.settings.copy(exposure = step / 30f)) }
            SystemClock.sleep(12)
            val frame = readState(model).displayedSettings
            if (frame != null && frame.exposure > 0f && step < 30) intermediateFrames++
        }
        instrumentation.runOnMainSync { model.finishAdjustment() }
        awaitIdle(model)
        assertTrue("continuous dragging must show intermediate photo frames", intermediateFrames > 0)
        assertEquals(1f, readState(model).displayedSettings!!.exposure, .0001f)
    }

    private fun awaitIdle(model: EditorModel) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val state = readState(model)
            if (!state.loading && !state.rendering && state.filtered != null && state.displayed != null &&
                state.displayedSettings == state.settings) return
            SystemClock.sleep(75L)
        }
        val state = readState(model)
        throw AssertionError("EditorModel did not become idle: loading=${state.loading}, rendering=${state.rendering}, displayed=${state.displayed != null}, displayedSettings=${state.displayedSettings}, error=${state.error}")
    }

    private fun readState(model: EditorModel): EditorState {
        lateinit var state: EditorState
        instrumentation.runOnMainSync { state = model.state }
        return state
    }

    private fun readBitmap(model: EditorModel): Bitmap? {
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync { bitmap = model.state.filtered }
        assertNotNull("filtered result missing", bitmap)
        return bitmap
    }
}

/** Application whose LibraryStore is isolated from the test target's real filesDir. */
class IsolatedTestApplication private constructor() : Application() {
    private lateinit var isolatedFiles: File
    private lateinit var cacheRoot: File

    fun initialize(base: Context) {
        cacheRoot = base.cacheDir
        isolatedFiles = File(cacheRoot, "library-device-test-${UUID.randomUUID()}").apply { mkdirs() }
        attachBaseContext(base)
    }

    override fun getFilesDir(): File = isolatedFiles

    fun cleanup() {
        val directory = isolatedFiles
        if (directory.name.startsWith("library-device-test-") &&
            directory.parentFile?.canonicalFile == cacheRoot.canonicalFile) {
            directory.deleteRecursively()
        }
    }

    companion object {
        fun create(base: Context): IsolatedTestApplication = IsolatedTestApplication().apply { initialize(base) }
    }
}
