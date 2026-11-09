package studio.pianyu.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class EditorState(val source: Bitmap? = null, val filtered: Bitmap? = null,
    val displayed: Bitmap? = null, val thumbnails: Map<String, Bitmap> = emptyMap(),
    val displayedSettings: EditSettings? = null,
    val settings: EditSettings = EditSettings(), val loading: Boolean = false,
    val rendering: Boolean = false, val exporting: Boolean = false,
    val notice: String? = null, val error: String? = null,
    val canUndo: Boolean = false, val canRedo: Boolean = false)

data class BatchProgress(val completed: Int, val total: Int, val failed: Int = 0,
    val stopping: Boolean = false)
data class BatchResult(val saved: List<Uri>, val failed: Int, val canceled: Boolean)
data class LibraryState(val entries: List<PhotoEntry> = emptyList(),
    val thumbnails: Map<String, Bitmap> = emptyMap(), val selected: Set<String> = emptySet(),
    val editorId: String? = null, val restoring: Boolean = true, val importing: Boolean = false,
    val recipe: EditSettings? = null, val batch: BatchProgress? = null,
    val lastBatch: BatchResult? = null)

class EditorModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(EditorState())
        private set
    var library by mutableStateOf(LibraryState())
        private set
    var exportEdge by mutableStateOf(2048)
        private set
    var savedRecipes by mutableStateOf<List<NamedRecipe>>(emptyList())
        private set
    var recipesLoading by mutableStateOf(true)
        private set
    private val store = LibraryStore(application)
    private val recipeStore = RecipeStore(application)
    private var recipePersistenceBlocked = false
    private var recipePersistJob: Job? = null
    private var pendingRecipes: SavedRecipes? = null
    private val restored = CompletableDeferred<Unit>()
    private var persistenceBlocked = false
    private var generation = 0
    private data class Composition(val photoId: String?, val source: Bitmap,
        val filtered: Bitmap, val settings: EditSettings)
    private var pendingComposition: Composition? = null
    private var activeId: String? = null
    private var sourceUri: Uri? = null
    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var composeJob: Job? = null
    private var persistJob: Job? = null
    private var pendingLibrary: StoredLibrary? = null
    private val thumbnailJobs = mutableMapOf<String, Job>()
    private val libraryThumbJobs = mutableMapOf<String, Job>()
    private val thumbnailSlots = Semaphore(2)
    private val history = mutableListOf(EditSettings())
    private var position = 0
    private var renderedLook = -1
    private var renderedGrain = -1f
    private var batchCancel = false
    private val sourceCache = bitmapCache(8 * 1024)
    private val frameCache = bitmapCache(24 * 1024)

    init {
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { recipeStore.read() }
                savedRecipes = saved.recipes; exportEdge = saved.exportEdge
            } catch (error: Exception) {
                recipePersistenceBlocked = true
                state = state.copy(error = "配方库读取失败，已有文件已保留：${error.message}")
            } finally { recipesLoading = false }
        }
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.read() }
                library = library.copy(entries = saved.entries, recipe = saved.recipe, restoring = false)
            } catch (error: Exception) {
                persistenceBlocked = true
                library = library.copy(restoring = false)
                state = state.copy(error = "照片库读取失败，已有文件已保留：${error.message}")
            } finally { restored.complete(Unit) }
        }
        viewModelScope.launch {
            try { withContext(NativeCore.dispatcher) { NativeCore.warmup(application) } }
            catch (error: Exception) { Log.w("Pianyu", "engine warmup", error) }
        }
    }

    fun load(uri: Uri) = importPhotos(listOf(uri), openSingle = true)
    fun loadDemo() {
        viewModelScope.launch {
            restored.await()
            val demo = library.entries.firstOrNull { it.id == "demo-original-v1" }
                ?: PhotoEntry("demo-original-v1", null, "窗边静物 · 示例").also {
                    library = library.copy(entries = listOf(it) + library.entries)
                    persist()
                }
            openPhoto(demo.id)
        }
    }

    fun importPhotos(uris: List<Uri>, openSingle: Boolean = uris.size == 1) {
        if (uris.isEmpty() || library.importing) return
        viewModelScope.launch {
            restored.await()
            library = library.copy(importing = true)
            try {
                val photos = withContext(Dispatchers.IO) {
                    uris.distinct().map { uri ->
                        try { getApplication<Application>().contentResolver.takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        catch (_: SecurityException) { /* Opening still works for a temporary grant. */ }
                        val name = try {
                            getApplication<Application>().contentResolver.query(uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) it.getString(0) else null
                            }
                        } catch (_: Exception) { null }
                        PhotoEntry(UUID.randomUUID().toString(), uri.toString(), name ?: "导入的照片")
                    }
                }
                val existing = library.entries.mapNotNull { it.uri }.toSet()
                val added = photos.filter { it.uri !in existing }
                library = library.copy(entries = added + library.entries, importing = false)
                persist()
                if (openSingle && photos.size == 1) {
                    val entry = added.firstOrNull() ?: library.entries.first { it.uri == photos.first().uri }
                    openPhoto(entry.id)
                } else state = state.copy(notice = "已导入 ${added.size} 张照片")
            } catch (error: Exception) {
                library = library.copy(importing = false)
                state = state.copy(error = "导入失败：${error.message}")
            }
        }
    }

    fun requestLibraryThumbnail(id: String) {
        if (id in library.thumbnails || libraryThumbJobs[id]?.isActive == true) return
        val photo = library.entries.firstOrNull { it.id == id } ?: return
        libraryThumbJobs[id] = viewModelScope.launch {
            try {
                val thumb = withContext(Dispatchers.IO) {
                    thumbnailSlots.withPermit {
                        val file = store.thumbnail(id, photo.settings)
                        BitmapFactory.decodeFile(file.absolutePath) ?: run {
                            val source = PhotoIO.decode(getApplication(), photo.contentUri(), 240)
                            var film: Bitmap? = null
                            try {
                                film = withContext(NativeCore.dispatcher) {
                                    NativeCore.render(getApplication(), source, Looks[photo.settings.lookIndex], photo.settings.grain)
                                }
                                PhotoIO.finish(source, film, photo.settings).also {
                                    file.outputStream().use { output -> it.compress(Bitmap.CompressFormat.JPEG, 86, output) }
                                }
                            } finally { source.recycle(); film?.recycle() }
                        }
                    }
                }
                if (library.entries.firstOrNull { it.id == id }?.settings == photo.settings)
                    library = library.copy(thumbnails = (library.thumbnails + (id to thumb)).entries.toList()
                        .takeLast(80).associate { it.key to it.value })
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("Pianyu", "library thumbnail unavailable", error) }
        }
    }

    fun openPhoto(id: String) {
        if (state.exporting || library.batch != null) return
        val photo = library.entries.firstOrNull { it.id == id } ?: return
        loadJob?.cancel(); renderJob?.cancel(); composeJob?.cancel(); cancelThumbnails()
        pendingComposition = null
        libraryThumbJobs.values.forEach { it.cancel() }; libraryThumbJobs.clear()
        val token = ++generation
        activeId = id; sourceUri = photo.contentUri()
        library = library.copy(editorId = id)
        history.clear(); history.add(photo.settings); position = 0
        renderedLook = -1; renderedGrain = -1f
        state = EditorState(displayed = library.thumbnails[id], settings = photo.settings, loading = true)
        val started = SystemClock.elapsedRealtime()
        loadJob = viewModelScope.launch {
            try {
                val source = sourceCache.get(id) ?: withContext(Dispatchers.IO) {
                    PhotoIO.decode(getApplication(), photo.contentUri(), 960)
                }.also { sourceCache.put(id, it) }
                if (token != generation) return@launch
                val provisional = withContext(PhotoIO.previewDispatcher) {
                    PhotoGeometry.apply(source, photo.settings)
                }
                if (token != generation) return@launch
                state = state.copy(source = source, displayed = provisional, loading = false)
                Log.i("PianyuPerf", "photo_visible_ms=${SystemClock.elapsedRealtime() - started} size=${source.width}x${source.height}")
                renderPreview()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (token == generation) state = state.copy(loading = false,
                    error = "无法打开照片，请从相册重新导入：${error.message}")
            }
        }
    }

    fun backToLibrary() {
        if (state.exporting) return
        finishAdjustment()
        val id = activeId
        val shown = state.displayed
        val settings = state.settings
        if (id != null && shown != null && !state.loading && !state.rendering && state.displayedSettings == state.settings) {
            viewModelScope.launch {
                try {
                val thumb = withContext(Dispatchers.IO) {
                    val scale = minOf(1f, 240f / maxOf(shown.width, shown.height))
                    Bitmap.createScaledBitmap(shown, (shown.width * scale).toInt().coerceAtLeast(1),
                        (shown.height * scale).toInt().coerceAtLeast(1), true).also {
                        store.thumbnail(id, settings).outputStream().use { output -> it.compress(Bitmap.CompressFormat.JPEG, 86, output) }
                    }
                }
                if (library.entries.firstOrNull { it.id == id }?.settings == settings)
                    library = library.copy(thumbnails = library.thumbnails + (id to thumb))
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { Log.w("Pianyu", "edited thumbnail unavailable", error) }
            }
        }
        cancelThumbnails()
        library = library.copy(editorId = null)
    }

    fun chooseLook(index: Int) {
        if (state.loading || index !in Looks.indices || index == state.settings.lookIndex) return
        state = state.copy(settings = state.settings.copy(lookIndex = index, strength = Looks[index].defaultStrength))
        commit(); renderPreview()
    }
    fun edit(settings: EditSettings) {
        if (state.source == null || state.loading) return
        state = state.copy(settings = settings)
        composePreview()
    }
    fun updateStrength(value: Float) = edit(state.settings.copy(strength = value.coerceIn(0f, 1f)))
    fun updateBrightness(value: Float) = edit(state.settings.copy(brightness = value.coerceIn(-1f, 1f)))
    fun updateWarmth(value: Float) = edit(state.settings.copy(warmth = value.coerceIn(-1f, 1f)))
    fun updateGrain(value: Float) = edit(state.settings.copy(grain = value.coerceIn(0f, 1f)))
    fun finishAdjustment() {
        commit()
        if (state.source != null && !state.loading && state.settings.grain != renderedGrain) renderPreview()
    }
    fun setCrop(aspect: Float) {
        edit(state.settings.copy(cropAspect = aspect, crop = CropRect())); commit()
    }
    fun setGeometry(draft: EditSettings) {
        edit(state.settings.copy(cropAspect = draft.cropAspect, crop = draft.crop.normalized(),
            quarterTurns = draft.quarterTurns, straightenDegrees = draft.straightenDegrees,
            flipHorizontal = draft.flipHorizontal, flipVertical = draft.flipVertical))
        commit()
    }
    fun clearNotice() { state = state.copy(notice = null) }
    fun clearError() { state = state.copy(error = null) }
    fun chooseExportEdge(edge: Int) {
        if (!recipesLoading && edge in listOf(1080, 2048)) { exportEdge = edge; persistRecipes() }
    }

    fun saveRecipe(name: String): Boolean {
        if (recipesLoading || recipePersistenceBlocked || state.source == null || state.loading) return false
        val label = name.trim().take(40)
        if (label.isEmpty()) return false
        finishAdjustment()
        val recipe = NamedRecipe(UUID.randomUUID().toString(), label, state.settings.colorRecipe())
        savedRecipes = listOf(recipe) + savedRecipes
        persistRecipes()
        state = state.copy(notice = "已保存配方「$label」")
        return true
    }

    fun renameRecipe(id: String, name: String) {
        if (recipesLoading || recipePersistenceBlocked) return
        val label = name.trim().take(40)
        if (label.isEmpty()) return
        savedRecipes = savedRecipes.map { if (it.id == id) it.copy(name = label) else it }
        persistRecipes()
    }

    fun deleteRecipe(id: String) {
        if (recipesLoading || recipePersistenceBlocked) return
        savedRecipes = savedRecipes.filterNot { it.id == id }
        persistRecipes()
    }

    fun applySavedRecipe(id: String) {
        if (state.source == null || state.loading || state.exporting) return
        val recipe = savedRecipes.firstOrNull { it.id == id } ?: return
        finishAdjustment()
        edit(state.settings.withColorRecipe(recipe.settings)); finishAdjustment()
        renderPreviewIfNeeded()
        library = library.copy(recipe = recipe.settings.colorRecipe())
        persist()
        state = state.copy(notice = "已应用「${recipe.name}」，构图已保留；可返回照片库批量套用")
    }

    private fun persistRecipes() {
        if (recipePersistenceBlocked || recipesLoading) return
        pendingRecipes = SavedRecipes(savedRecipes, exportEdge)
        if (recipePersistJob?.isActive == true) return
        recipePersistJob = viewModelScope.launch {
            delay(80)
            while (true) {
                val snapshot = pendingRecipes ?: break
                pendingRecipes = null
                try { withContext(Dispatchers.IO) { recipeStore.write(snapshot) } }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { state = state.copy(error = "配方与导出设置未能保存：${error.message}") }
                delay(80)
            }
        }
    }

    private fun commit() {
        if (history.isEmpty() || state.source == null || state.loading) return
        if (history[position] == state.settings) return
        while (history.lastIndex > position) history.removeAt(history.lastIndex)
        history.add(state.settings); position = history.lastIndex
        state = state.copy(canUndo = position > 0, canRedo = false)
        saveCurrentSettings()
    }
    fun undo() { if (position > 0) restore(--position) }
    fun redo() { if (position < history.lastIndex) restore(++position) }
    fun reset() { edit(EditSettings()); commit(); renderPreviewIfNeeded() }
    private fun restore(index: Int) {
        state = state.copy(settings = history[index], canUndo = index > 0, canRedo = index < history.lastIndex)
        saveCurrentSettings(); renderPreviewIfNeeded()
    }
    private fun renderPreviewIfNeeded() {
        if (state.rendering || renderedLook != state.settings.lookIndex || renderedGrain != state.settings.grain) renderPreview()
        else composePreview()
    }
    private fun saveCurrentSettings() {
        val id = activeId ?: return
        libraryThumbJobs.remove(id)?.cancel()
        library = library.copy(entries = library.entries.map { if (it.id == id) it.copy(settings = state.settings) else it },
            thumbnails = library.thumbnails - id)
        persist()
    }
    private fun persist() {
        if (persistenceBlocked) return
        pendingLibrary = StoredLibrary(library.entries, library.recipe)
        if (persistJob?.isActive == true) return
        persistJob = viewModelScope.launch {
            delay(80)
            while (true) {
                val snapshot = pendingLibrary ?: break
                pendingLibrary = null
                try { withContext(Dispatchers.IO) { store.write(snapshot) } }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { state = state.copy(error = "编辑记录未能保存：${error.message}") }
                delay(80)
            }
        }
    }

    private fun cacheKey(id: String, settings: EditSettings) = "$id:${settings.lookIndex}:${settings.grain}"
    private fun cancelThumbnails() { thumbnailJobs.values.forEach { it.cancel() }; thumbnailJobs.clear() }
    private fun renderPreview() {
        val source = state.source ?: return
        if (state.loading) return
        renderJob?.cancel(); cancelThumbnails()
        val token = ++generation
        val settings = state.settings
        val key = cacheKey(activeId ?: "demo", settings)
        frameCache.get(key)?.let {
            renderedLook = settings.lookIndex; renderedGrain = settings.grain
            state = state.copy(filtered = it, rendering = false, error = null)
            Log.i("PianyuPerf", "preview_cache_hit=true look=${Looks[settings.lookIndex].id}")
            composePreview(); return
        }
        state = state.copy(rendering = true, error = null)
        renderJob = viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                val filtered = withContext(NativeCore.dispatcher) {
                    NativeCore.render(getApplication(), source, Looks[settings.lookIndex], settings.grain)
                }
                if (token != generation) return@launch
                frameCache.put(key, filtered)
                renderedLook = settings.lookIndex; renderedGrain = settings.grain
                state = state.copy(filtered = filtered, rendering = false)
                Log.i("PianyuPerf", "preview_ms=${SystemClock.elapsedRealtime() - start} look=${Looks[settings.lookIndex].id}")
                composePreview()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (token == generation) state = state.copy(rendering = false, error = "风格处理失败：${error.message}")
            }
        }
    }

    private fun composePreview() {
        val original = state.source ?: return
        val filtered = state.filtered ?: original
        pendingComposition = Composition(activeId, original, filtered, state.settings)
        if (composeJob?.isActive == true) return
        composeJob = viewModelScope.launch {
            while (true) {
                val request = pendingComposition ?: break
                pendingComposition = null
                try {
                    val shown = withContext(PhotoIO.previewDispatcher) {
                        PhotoIO.finish(request.source, request.filtered, request.settings)
                    }
                    if (request.photoId == activeId && request.source === state.source &&
                        (request.filtered === state.filtered || state.filtered == null))
                        state = state.copy(displayed = shown, displayedSettings = request.settings)
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { state = state.copy(error = "调整预览失败：${error.message}") }
                // Keep one computation running and replace only the pending request. Cancelling
                // every slider event would prevent all intermediate frames during a fast drag.
                delay(16)
            }
        }
    }

    fun requestLookThumbnail(index: Int) {
        val source = state.source ?: return
        val look = Looks.getOrNull(index) ?: return
        if (state.loading || state.rendering || look.id in state.thumbnails || thumbnailJobs[look.id]?.isActive == true) return
        val token = generation
        thumbnailJobs[look.id] = viewModelScope.launch {
            try {
                delay(120)
                val rendered = withContext(NativeCore.dispatcher) {
                    val scale = minOf(1f, 128f / maxOf(source.width, source.height))
                    val small = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
                        (source.height * scale).toInt().coerceAtLeast(1), true)
                    val film = NativeCore.render(getApplication(), small, look)
                    try { PhotoIO.finish(small, film, EditSettings(lookIndex = index, strength = look.defaultStrength)) }
                    finally { film.recycle(); if (small !== source) small.recycle() }
                }
                if (token == generation) state = state.copy(thumbnails = state.thumbnails + (look.id to rendered))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { Log.w("Pianyu", "look thumbnail unavailable", error) }
        }
    }

    fun toggleSelected(id: String) {
        library = library.copy(selected = if (id in library.selected) library.selected - id else library.selected + id)
    }
    fun selectAll() { library = library.copy(selected = library.entries.map { it.id }.toSet()) }
    fun clearSelection() { library = library.copy(selected = emptySet()) }
    fun copyRecipe() {
        library = library.copy(recipe = state.settings.colorRecipe())
        persist(); state = state.copy(notice = "调色配方已复制，保留每张照片自己的构图")
    }
    fun applyRecipeToSelected() {
        val recipe = library.recipe ?: return
        val count = library.selected.size
        if (count == 0) return
        val changedIds = library.selected
        changedIds.forEach { libraryThumbJobs.remove(it)?.cancel() }
        library = library.copy(entries = library.entries.map {
            if (it.id in changedIds) it.copy(settings = it.settings.withColorRecipe(recipe)) else it
        }, thumbnails = library.thumbnails.filterKeys { it !in changedIds })
        frameCache.evictAll()
        persist()
        state = state.copy(notice = "已为 $count 张照片套用调色，裁切与旋转已保留")
    }

    fun export(share: Boolean, onReady: (Uri) -> Unit) {
        if (state.source == null || state.loading || state.exporting || library.batch != null) return
        cancelThumbnails()
        val settings = state.settings; val uri = sourceUri; val edge = exportEdge
        state = state.copy(exporting = true, error = null)
        viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                val output = renderExport(uri, settings, edge, share)
                state = state.copy(exporting = false, notice = if (share) null else "已保存到相册 · 片屿")
                Log.i("PianyuPerf", "export_ms=${SystemClock.elapsedRealtime() - start} edge=$edge share=$share")
                onReady(output)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { state = state.copy(exporting = false, error = "导出失败：${error.message}") }
        }
    }

    private suspend fun renderExport(uri: Uri?, settings: EditSettings, edge: Int, share: Boolean): Uri =
        withContext(Dispatchers.IO) {
            val original = PhotoIO.decode(getApplication(), uri, edge)
            var rendered: Bitmap? = null; var finished: Bitmap? = null
            try {
                val film = withContext(NativeCore.dispatcher) {
                    NativeCore.render(getApplication(), original, Looks[settings.lookIndex], settings.grain)
                }
                rendered = film
                val output = PhotoIO.finish(original, film, settings); finished = output
                PhotoIO.save(getApplication(), output, share)
            } finally { listOfNotNull(original, rendered, finished).distinct().forEach { it.recycle() } }
        }

    fun exportSelected() {
        if (library.batch != null || state.exporting) return
        val selected = library.entries.filter { it.id in library.selected }
        if (selected.isEmpty()) return
        cancelThumbnails(); batchCancel = false
        val edge = exportEdge
        library = library.copy(batch = BatchProgress(0, selected.size), lastBatch = null)
        viewModelScope.launch {
            var saved = 0; var failed = 0
            val outputs = mutableListOf<Uri>()
            for (photo in selected) {
                if (batchCancel) break
                try { outputs += renderExport(photo.contentUri(), photo.settings, edge, false); saved++ }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { failed++; Log.w("Pianyu", "batch item failed", error) }
                library = library.copy(batch = BatchProgress(saved + failed, selected.size, failed, batchCancel))
            }
            library = library.copy(batch = null, lastBatch = BatchResult(outputs.toList(), failed, batchCancel))
            state = state.copy(notice = "${if (batchCancel) "已停止 · " else ""}已保存 $saved 张${if (failed > 0) "，失败 $failed 张" else ""}")
        }
    }
    fun stopBatch() { batchCancel = true; library = library.copy(batch = library.batch?.copy(stopping = true)) }

    private fun bitmapCache(kib: Int) = object : LruCache<String, Bitmap>(kib) {
        override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
}


