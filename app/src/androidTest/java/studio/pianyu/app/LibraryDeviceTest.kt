package studio.pianyu.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context

    @Test
    fun storeRoundTripKeepsCompleteGeometryAndRecipeSemantics() {
        val application = IsolatedTestApplication.create(target)
        try {
        val geometry = EditSettings(cropAspect = 1.5f, crop = CropRect(.1f, .2f, .8f, .9f),
            quarterTurns = 3, straightenDegrees = -12f, flipHorizontal = true, flipVertical = true)
        val color = EditSettings(lookIndex = 2, strength = .4f, exposure = 1.2f, contrast = -.3f,
            saturation = .7f, highlights = -.2f, shadows = .4f, fade = .3f, tint = -.5f,
            sharpen = .8f, vignette = .6f, brightness = .1f, warmth = -.2f, grain = .5f)
        val entry = PhotoEntry("one", "content://one", "one.jpg", geometry.copy(
            lookIndex = color.lookIndex, strength = color.strength, exposure = color.exposure,
            contrast = color.contrast, saturation = color.saturation, highlights = color.highlights,
            shadows = color.shadows, fade = color.fade, tint = color.tint, sharpen = color.sharpen,
            vignette = color.vignette, brightness = color.brightness, warmth = color.warmth, grain = color.grain))
        LibraryStore(application).write(StoredLibrary(listOf(entry), color.colorRecipe()))
        val read = LibraryStore(application).read()
        assertEquals(entry, read.entries.single())
        assertEquals(color.colorRecipe(), read.recipe)
        assertEquals(CropRect(), read.recipe?.crop)
        assertEquals(0, read.recipe?.quarterTurns)
        assertFalse(read.recipe?.flipHorizontal == true)
        } finally { application.cleanup() }
    }

    @Test
    fun modelImportsDeduplicatesCopiesColorRecipeAndExportsSelected() {
        val application = IsolatedTestApplication.create(target)
        val createdExports = linkedSetOf<Uri>()
        try {
            val model = EditorModel(application)
            awaitRestored(model)
            val uris = listOf("sample_orientation6.jpg", "sample_orientation6.heic", "demo.jpg")
                .mapIndexed { index, name -> fixtureUri(name, "input-$index-${UUID.randomUUID()}") }
        onMain { model.importPhotos(uris + uris[0], openSingle = false) }
        awaitImport(model, 3)
        onMain { model.importPhotos(listOf(uris[0]), openSingle = false) }
        awaitImport(model, 3)
        val firstId = readLibrary(model).entries.first().id
        onMain { model.openPhoto(firstId) }
        awaitEditor(model)
        val originalGeometry = CropRect(.12f, .18f, .77f, .91f)
        val edited = readEditor(model).settings.copy(crop = originalGeometry, cropAspect = 1.3f,
            quarterTurns = 1, straightenDegrees = 8f, flipHorizontal = true, exposure = .8f, tint = -.3f)
        onMain { model.edit(edited); model.finishAdjustment() }
        awaitEditor(model)
        onMain { model.copyRecipe(); model.backToLibrary() }
        awaitLibrary(model)
        onMain { model.selectAll(); model.applyRecipeToSelected() }
        val afterRecipe = readLibrary(model)
        assertEquals(3, afterRecipe.entries.size)
        val recipe = afterRecipe.recipe ?: error("recipe was not persisted in memory")
        afterRecipe.entries.forEach { entry ->
            assertEquals(recipe.lookIndex, entry.settings.lookIndex)
            assertEquals(recipe.exposure, entry.settings.exposure, .0001f)
            assertEquals(recipe.tint, entry.settings.tint, .0001f)
        }
        val firstAfter = afterRecipe.entries.first { it.id == firstId }.settings
        assertEquals(originalGeometry, firstAfter.crop)
        assertEquals(1, firstAfter.quarterTurns)
        assertEquals(8f, firstAfter.straightenDegrees, .0001f)
        assertTrue(firstAfter.flipHorizontal)
            onMain { model.chooseExportEdge(1080); model.exportSelected(); model.stopBatch() }
            awaitStoppedBatch(model)
            val cancelledExports = readLibrary(model).lastBatch!!.saved.toSet()
            createdExports += cancelledExports
            assertTrue("stopBatch should allow at most one item", cancelledExports.size <= 1)
            onMain { model.exportSelected() }
            awaitBatch(model, expectedFailed = 0)
            val firstExportIds = readLibrary(model).lastBatch!!.saved.toSet()
            createdExports += firstExportIds
            assertEquals(3, firstExportIds.size)
            assertExportsReadableAndBounded(firstExportIds, 1080)

        val bad = fixtureUri("does-not-exist.jpg", "missing-${UUID.randomUUID()}", create = true)
        onMain { model.importPhotos(listOf(bad), openSingle = false) }
        awaitImport(model, 4)
            onMain { model.selectAll(); model.exportSelected() }
            awaitBatch(model, expectedFailed = 1)
            val secondExportIds = readLibrary(model).lastBatch!!.saved.toSet()
            createdExports += secondExportIds
            assertEquals(3, secondExportIds.size)
            assertExportsReadableAndBounded(secondExportIds, 1080)
            assertTrue(readEditor(model).notice?.contains("失败 1 张") == true)

            Thread.sleep(300)
            val rebuilt = EditorModel(application)
            awaitRestored(rebuilt)
            assertEquals(4, readLibrary(rebuilt).entries.size)
            val rebuiltFirst = readLibrary(rebuilt).entries.first { it.id == firstId }.settings
            assertEquals(originalGeometry, rebuiltFirst.crop)
            assertEquals(1, rebuiltFirst.quarterTurns)
        } finally {
            createdExports.forEach { uri ->
                target.contentResolver.delete(uri, null, null)
            }
            application.cleanup()
        }
    }

    private fun fixtureUri(name: String, outputName: String, create: Boolean = false): Uri {
        val file = File(target.cacheDir, "exports/$outputName").apply {
            parentFile?.mkdirs()
            if (create) createNewFile() else {
                val assets = if (name == "demo.jpg") target.assets else testContext.assets
                assets.open(name).use { input -> outputStream().use(input::copyTo) }
            }
        }
        return FileProvider.getUriForFile(target, "${target.packageName}.files", file)
    }

    private fun awaitRestored(model: EditorModel) {
        poll(30_000L) { !readLibrary(model).restoring }
    }

    private fun awaitImport(model: EditorModel, count: Int) {
        poll(30_000L) { val library = readLibrary(model); !library.importing && library.entries.size == count }
    }

    private fun awaitLibrary(model: EditorModel) {
        poll(30_000L) { val library = readLibrary(model); !library.restoring && library.editorId == null }
    }

    private fun awaitEditor(model: EditorModel) {
        poll(45_000L) {
            val state = readEditor(model)
            !state.loading && !state.rendering && state.filtered != null && state.displayed != null &&
                state.displayedSettings == state.settings
        }
    }

    private fun awaitBatch(model: EditorModel, expectedFailed: Int) {
        poll(120_000L) {
            val batch = readLibrary(model).batch
            val notice = readEditor(model).notice.orEmpty()
            batch == null && if (expectedFailed == 0) {
                notice.contains("已保存") && !notice.contains("失败")
            } else notice.contains("失败 $expectedFailed 张")
        }
    }

    private fun awaitStoppedBatch(model: EditorModel) {
        poll(120_000L) {
            readLibrary(model).batch == null && readEditor(model).notice?.contains("已停止") == true
        }
    }

    private fun poll(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            android.os.SystemClock.sleep(100L)
        }
        throw AssertionError("condition did not become true before timeout")
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun readLibrary(model: EditorModel): LibraryState {
        lateinit var value: LibraryState
        onMain { value = model.library }
        return value
    }

    private fun readEditor(model: EditorModel): EditorState {
        lateinit var value: EditorState
        onMain { value = model.state }
        return value
    }

    private fun assertExportsReadableAndBounded(uris: Set<Uri>, edge: Int) {
        uris.forEach { uri ->
            val bitmap = target.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            assertNotNull("export is not decodable: $uri", bitmap)
            assertTrue("export exceeds selected edge", maxOf(bitmap!!.width, bitmap.height) <= edge)
            bitmap.recycle()
        }
    }
}

