package studio.pianyu.app

import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InteractionStateDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context

    @Test
    fun grainEditRerendersBeforeCommitAndLibraryRemovalKeepsSource() {
        val app = IsolatedTestApplication.create(target)
        val model = EditorModel(app)
        modelForError = model
        try {
            onMain { model.loadDemo() }
            await { !read(model).loading && !read(model).rendering && read(model).source != null && read(model).displayedSettings != null }
            assertTrue(read(model).settings.autoFilmExposure)
            val metered = read(model).filtered!!.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            onMain { model.edit(model.state.settings.copy(autoFilmExposure = false)); model.finishAdjustment() }
            await { !read(model).rendering && read(model).displayedSettings?.autoFilmExposure == false }
            assertTrue("meter toggle must invalidate the native frame cache", !metered.sameAs(read(model).filtered!!))
            onMain { model.undo() }
            await { !read(model).rendering && read(model).displayedSettings?.autoFilmExposure == true }
            assertTrue("undo must restore the metered frame", metered.sameAs(read(model).filtered!!))
            metered.recycle()
            val initial = read(model).displayedSettings?.grain ?: -1f
            onMain { model.edit(model.state.settings.copy(grain = .72f)) }
            await { read(model).displayedSettings?.grain == .72f }
            assertTrue("grain edit did not rerender before finishAdjustment", read(model).displayedSettings!!.grain != initial)
            onMain { model.finishAdjustment(); model.undo() }
            assertEquals(initial, read(model).settings.grain, .0001f)
            onMain { model.redo() }
            assertEquals(.72f, read(model).settings.grain, .0001f)

            val sourceFile = File(target.cacheDir, "exports/remove-source-${UUID.randomUUID()}.jpg").apply {
                parentFile?.mkdirs(); testContext.assets.open("sample_orientation6.jpg").use { input ->
                    outputStream().use { output -> input.copyTo(output) }
                }
            }
            val uri = FileProvider.getUriForFile(target, "${target.packageName}.files", sourceFile)
            onMain { model.importPhotos(listOf(uri), openSingle = false) }
            await { !read(model).loading && readLibrary(model).entries.any { it.uri == uri.toString() } }
            val id = readLibrary(model).entries.first { it.uri == uri.toString() }.id
            onMain { model.toggleSelected(id); model.removeSelectedFromLibrary() }
            await { readLibrary(model).entries.none { it.id == id } }
            assertTrue("removing from library must keep source file", sourceFile.exists())
        } finally {
            onMain { model.viewModelScope.cancel() }
            app.cleanup()
        }
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun read(model: EditorModel): EditorState {
        lateinit var value: EditorState
        onMain { value = model.state }
        return value
    }
    private fun readLibrary(model: EditorModel): LibraryState {
        lateinit var value: LibraryState
        onMain { value = model.library }
        return value
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 60_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100L)
        }
        throw AssertionError("condition timed out: state=${read(modelForError)}")
    }

    // Only used to make a timeout useful without retaining a second model reference.
    private lateinit var modelForError: EditorModel
}
