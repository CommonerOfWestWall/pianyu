package studio.pianyu.app

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchPipelineDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val testContext get() = instrumentation.context
    private val createdFixtureFiles = mutableListOf<File>()

    @Test
    fun controlled100ImportDoesNotBlockMain() {
        val application = IsolatedTestApplication.create(target)
        val models = ViewModelStore()
        lateinit var model: EditorModel
        val heartbeat = Handler(Looper.getMainLooper())
        var lastTick = 0L
        var maxGap = 0L
        val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                if (lastTick != 0L) maxGap = maxOf(maxGap, now - lastTick)
                lastTick = now
                heartbeat.postDelayed(this, 16L)
            }
        }
        try {
            model = EditorModel(application)
            models.put("model", model)
            awaitRestored(model)
            val uris = (0 until 100).map { index ->
                val fixture = when {
                    index < 4 -> "sample_orientation6.heic"
                    index % 3 == 0 -> "sample_orientation6.jpg"
                    else -> "demo.jpg"
                }
                fixtureUri(fixture, "batch-$index-${UUID.randomUUID()}")
            }
            onMain { heartbeat.post(tick) }
            val started = SystemClock.elapsedRealtime()
            onMain { model.importPhotos(uris + uris.first(), openSingle = false) }
            awaitImport(model, 100)
            val importMs = SystemClock.elapsedRealtime() - started
            val entries = readLibrary(model).entries
            assertEquals("duplicate URI must be ignored", 100, entries.size)
            assertTrue("import must finish without model error", readEditor(model).error == null)
            entries.chunked(12).forEach { visible ->
                onMain { visible.forEach { model.requestLibraryThumbnail(it.id) } }
                poll(30_000L) {
                    val library = readLibrary(model)
                    visible.all { it.id in library.thumbnails } &&
                        visible.none { it.id in library.thumbnailErrors || it.id in library.thumbnailLoading }
                }
            }
            val totalMs = SystemClock.elapsedRealtime() - started
            Log.i("PianyuBatchProbe", "controlled_import count=100 import_ms=$importMs total_ms=$totalMs main_max_gap_ms=$maxGap")
            assertTrue("main heartbeat stalled for ${maxGap}ms", maxGap < 500L)
        } finally {
            onMain { heartbeat.removeCallbacks(tick); models.clear() }
            createdFixtureFiles.forEach { it.delete() }
            application.cleanup()
        }
    }

    @Test
    fun badInputDoesNotBlockFollowingExportsAndRetryTargetsFailure() {
        val application = IsolatedTestApplication.create(target)
        val models = ViewModelStore()
        val createdExports = linkedSetOf<Uri>()
        lateinit var model: EditorModel
        try {
            model = EditorModel(application)
            models.put("model", model)
            awaitRestored(model)
            val valid = listOf("sample_orientation6.jpg", "demo.jpg").mapIndexed { index, name ->
                fixtureUri(name, "bad-input-valid-$index-${UUID.randomUUID()}")
            }
            val bad = fixtureUri("sample_orientation6.jpg", "bad-input-empty-${UUID.randomUUID()}", empty = true)
            onMain { model.importPhotos(valid + bad, openSingle = false) }
            awaitImport(model, 3)
            onMain { model.selectAll(); model.exportSelected() }
            awaitBatch(model)
            val first = readLibrary(model).lastBatch ?: error("missing first batch result")
            createdExports += first.saved
            assertEquals(2, first.saved.size)
            assertEquals(1, first.failed)
            assertEquals(1, first.failures.size)
            assertTrue(readLibrary(model).entries.any { it.id == first.failures.single().photoId })

            onMain { model.retryFailedBatch() }
            awaitBatch(model)
            val retry = readLibrary(model).lastBatch ?: error("missing retry batch result")
            createdExports += retry.saved
            assertEquals(0, retry.saved.size)
            assertEquals(1, retry.failed)
            assertEquals(first.failures.single().photoId, retry.failures.single().photoId)
        } finally {
            onMain { models.clear() }
            createdExports.forEach { target.contentResolver.delete(it, null, null) }
            createdFixtureFiles.forEach { it.delete() }
            application.cleanup()
        }
    }

    @Test
    fun existingPhonePhotosOriginalBatchProbe() {
        assumeTrue("realPhotos=true is required for the device probe",
            InstrumentationRegistry.getArguments().getString("realPhotos") == "true")
        val originals = LibraryStore(target).read().entries.filter { entry ->
            entry.contentUri() != null && !entry.name.lowercase().contains("demo") &&
                entry.id != "demo-original-v1"
        }.take(6)
        assumeTrue("fewer than six non-demo library photos", originals.size == 6)
        val count = InstrumentationRegistry.getArguments().getString("count", "6").toInt()
        require(count in 6..24)
        val format = ExportFormat.valueOf(InstrumentationRegistry.getArguments().getString("format", "JPEG"))
        val application = IsolatedTestApplication.create(target)
        val models = ViewModelStore()
        lateinit var model: EditorModel
        val createdExports = linkedSetOf<Uri>()
        try {
            val sourceEntries = if (count == 6) originals else List(count) { index ->
                val original = originals[index % originals.size]
                val file = File(target.cacheDir, "exports/private-batch-${UUID.randomUUID()}.img")
                createdFixtureFiles += file
                file.parentFile?.mkdirs()
                target.contentResolver.openInputStream(original.contentUri()!!).use { input ->
                    requireNotNull(input).use { stream -> file.outputStream().use(stream::copyTo) }
                }
                original.copy(id = "stress-${UUID.randomUUID()}", name = "隔离压力测试 ${index + 1}",
                    uri = FileProvider.getUriForFile(target, "${target.packageName}.files", file).toString())
            }
            LibraryStore(application).write(StoredLibrary(sourceEntries))
            model = EditorModel(application)
            models.put("model", model)
            awaitRestored(model)
            poll(30_000L) { modelRecipesLoaded(model) }
            onMain { model.chooseExportEdge(0); model.chooseExportFormat(format) }
            originals.forEachIndexed { index, entry ->
                val uri = entry.contentUri()!!
                val dimensions = bounds(uri)
                val decodeStart = SystemClock.elapsedRealtime()
                val decode = runCatching { PhotoIO.decode(target, uri, 240) }
                val decodeMs = SystemClock.elapsedRealtime() - decodeStart
                decode.getOrNull()?.recycle()
                val thumbnailStart = SystemClock.elapsedRealtime()
                val thumbnail = runCatching { target.contentResolver.loadThumbnail(uri, Size(240, 240), null) }
                val thumbnailMs = SystemClock.elapsedRealtime() - thumbnailStart
                thumbnail.getOrNull()?.recycle()
                Log.i("PianyuBatchProbe", "real_photo index=$index width=${dimensions.first} height=${dimensions.second} " +
                    "decode240_ms=$decodeMs thumbnail240_ms=$thumbnailMs thumbnail_error=${thumbnail.exceptionOrNull()?.javaClass?.simpleName}")
            }
            val batchStarted = SystemClock.elapsedRealtime()
            onMain { model.selectAll(); model.exportSelected() }
            awaitBatch(model)
            val batchMs = SystemClock.elapsedRealtime() - batchStarted
            val result = readLibrary(model).lastBatch ?: error("batch completed without result")
            createdExports += result.saved
            val editor = readEditor(model)
            Log.i("PianyuBatchProbe", "real_batch count=$count format=$format saved=${result.saved.size} failed=${result.failed} canceled=${result.canceled} " +
                "wall_ms=$batchMs notice=${editor.notice} error=${editor.error}")
            assertEquals("batch must export all original-size inputs", 0, result.failed)
            assertEquals("batch must account for every selected photo", sourceEntries.size,
                result.saved.size + result.failed)
            result.saved.forEachIndexed { index, uri ->
                val output = bounds(uri)
                val entry = sourceEntries[index]
                val input = bounds(entry.contentUri()!!)
                val settings = entry.settings
                val expected = if (settings.quarterTurns % 2 == 0) input else input.second to input.first
                if (settings.crop == CropRect()) assertEquals("original-size output was silently resized", expected, output)
                assertTrue("export is not readable", output.first > 0 && output.second > 0)
            }
        } finally {
            onMain { models.clear() }
            createdExports.forEach { target.contentResolver.delete(it, null, null) }
            createdFixtureFiles.forEach { it.delete() }
            application.cleanup()
        }
    }

    private fun fixtureUri(name: String, outputName: String, empty: Boolean = false): Uri {
        val file = File(target.cacheDir, "exports/$outputName").apply {
            parentFile?.mkdirs()
            if (empty) createNewFile() else {
                val assets = if (name == "demo.jpg") target.assets else testContext.assets
                assets.open(name).use { input -> outputStream().use(input::copyTo) }
            }
        }
        createdFixtureFiles += file
        return FileProvider.getUriForFile(target, "${target.packageName}.files", file)
    }

    private fun bounds(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        target.contentResolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input, null, options) }
        if (options.outWidth > 0 && options.outHeight > 0) return options.outWidth to options.outHeight
        val source = ImageDecoder.createSource(target.contentResolver, uri)
        return try {
            ImageDecoder.decodeBitmap(source) { _, info, _ -> throw HeaderDimensions(info.size.width, info.size.height) }
            error("decoder returned a bitmap without reporting header dimensions")
        } catch (header: HeaderDimensions) {
            header.width to header.height
        }
    }

    private fun modelRecipesLoaded(model: EditorModel): Boolean {
        var loaded = false
        onMain { loaded = !model.recipesLoading }
        return loaded
    }

    private fun awaitRestored(model: EditorModel) = poll(30_000L) { !readLibrary(model).restoring }
    private fun awaitImport(model: EditorModel, count: Int) = poll(120_000L) {
        val library = readLibrary(model)
        !library.importing && library.entries.size == count
    }
    private fun awaitBatch(model: EditorModel) = poll(180_000L) { readLibrary(model).batch == null }
    private fun poll(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100L)
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
}

private class HeaderDimensions(val width: Int, val height: Int) : RuntimeException()
