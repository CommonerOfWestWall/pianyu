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
import kotlinx.coroutines.currentCoroutineContext
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
    val originalThumbnails: Set<String> = emptySet(), val thumbnailLoading: Set<String> = emptySet(),
    val thumbnailErrors: Set<String> = emptySet(),
    val editorId: String? = null, val restoring: Boolean = true, val importing: Boolean = false,
    val recipe: EditSettings? = null, val batch: BatchProgress? = null,
    val lastBatch: BatchResult? = null)

class EditorModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(EditorState())
        private set
    var library by mutableStateOf(LibraryState())
        private set
    var exportEdge by mutableStateOf(0)
        private set
    var exportFormat by mutableStateOf(ExportFormat.JPEG)
        private set
    val exportLossless: Boolean get() = exportFormat == ExportFormat.PNG
    var savedRecipes by mutableStateOf<List<NamedRecipe>>(emptyList())
        private set
    var recipesLoading by mutableStateOf(true)
        private set
    var diagnosticReport by mutableStateOf<String?>(null)
        private set
    private val diagnostics = ProcessingDiagnostics(application)
    private val diagnosticsReady = CompletableDeferred<Unit>()
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
    private val thumbnailSlots = Semaphore(1)
    private val history = mutableListOf(EditSettings())
    private var position = 0
    private var renderedLook = -1
    private var renderedGrain = -1f
    private var renderedFilmExposure = Float.NaN
    private var renderedAutoFilmExposure: Boolean? = null
    private var renderedMeterMethod: String? = null
    private var batchCancel = false
    private var automaticThumbnailsPaused = false
    private val sourceCache = bitmapCache(8 * 1024)
    private val frameCache = bitmapCache(24 * 1024)

    init {
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { recipeStore.read() }
                savedRecipes = saved.recipes; exportEdge = saved.exportEdge; exportFormat = saved.format
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
            try {
                val recovered = withContext(Dispatchers.IO) {
                    diagnostics.recoverPreviousExit().also { diagnostics.finished() }
                }
                diagnosticReport = withContext(Dispatchers.IO) { diagnostics.lastReport() }
                if (recovered != null) {
                    automaticThumbnailsPaused = true
                    state = state.copy(error = "上次照片处理未完成，可查看退出诊断。原片未改动。")
                }
            } catch (error: Exception) { Log.w("Pianyu", "exit diagnostics unavailable", error) }
            finally { diagnosticsReady.complete(Unit) }
            // Initialize on first use so a driver crash cannot prevent opening diagnostics.
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
        if (state.loading || state.rendering || state.exporting || library.batch != null) return
        if (id in library.thumbnails || id in library.thumbnailErrors || libraryThumbJobs[id]?.isActive == true) return
        val photo = library.entries.firstOrNull { it.id == id } ?: return
        library = library.copy(thumbnailLoading = library.thumbnailLoading + id)
        libraryThumbJobs[id] = viewModelScope.launch {
            val requestJob = currentCoroutineContext()[Job]
            try {
                val (thumb, original) = withContext(Dispatchers.IO) {
                    thumbnailSlots.withPermit {
                        val file = store.thumbnail(id, photo.settings)
                        val saved = BitmapFactory.decodeFile(file.absolutePath)
                        if (saved != null) saved to false
                        else PhotoIO.decode(getApplication(), photo.contentUri(), 240) to true
                    }
                }
                if (library.entries.firstOrNull { it.id == id }?.settings == photo.settings) {
                    val thumbs = (library.thumbnails + (id to thumb)).entries.toList()
                        .takeLast(80).associate { it.key to it.value }
                    library = library.copy(thumbnails = thumbs, originalThumbnails =
                        (if (original) library.originalThumbnails + id else library.originalThumbnails - id)
                            .intersect(thumbs.keys))
                }
            } catch (error: CancellationException) { throw error }
            catch (error: OutOfMemoryError) {
                library = library.copy(thumbnailErrors = library.thumbnailErrors + id)
                reportFailure(ProcessingStage.DECODE, error)
            }
            catch (error: Exception) {
                library = library.copy(thumbnailErrors = library.thumbnailErrors + id)
                reportFailure(ProcessingStage.DECODE, error)
            } finally {
                if (libraryThumbJobs[id] === requestJob) {
                    libraryThumbJobs.remove(id)
                    library = library.copy(thumbnailLoading = library.thumbnailLoading - id)
                }
            }
        }
    }

    fun openPhoto(id: String) {
        if (state.exporting || library.batch != null) return
        val photo = library.entries.firstOrNull { it.id == id } ?: return
        loadJob?.cancel(); renderJob?.cancel(); composeJob?.cancel(); cancelThumbnails()
        pendingComposition = null
        cancelLibraryThumbnails()
        val token = ++generation
        activeId = id; sourceUri = photo.contentUri()
        library = library.copy(editorId = id)
        history.clear(); history.add(photo.settings); position = 0
        renderedLook = -1; renderedGrain = -1f
        renderedFilmExposure = Float.NaN
        state = EditorState(displayed = library.thumbnails[id], settings = photo.settings, loading = true)
        val started = SystemClock.elapsedRealtime()
        loadJob = viewModelScope.launch {
            try {
                diagnosticsReady.await()
                val source = sourceCache.get(id) ?: withContext(Dispatchers.IO) {
                    diagnostics.checkpoint(ProcessingStage.DECODE)
                    try { PhotoIO.decode(getApplication(), photo.contentUri(), 960) }
                    finally { diagnostics.finished() }
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
            catch (error: OutOfMemoryError) {
                if (token == generation) {
                    releasePreviewCaches()
                    state = state.copy(loading = false)
                    reportFailure(ProcessingStage.DECODE, error)
                }
            }
            catch (error: Exception) {
                if (token == generation) {
                    state = state.copy(loading = false)
                    reportFailure(ProcessingStage.DECODE, error)
                }
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
                    library = library.copy(thumbnails = library.thumbnails + (id to thumb),
                        originalThumbnails = library.originalThumbnails - id, thumbnailErrors = library.thumbnailErrors - id)
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { Log.w("Pianyu", "edited thumbnail unavailable", error) }
            }
        }
        cancelThumbnails()
        library = library.copy(editorId = null)
    }

    fun chooseLook(index: Int) {
        if (state.loading || index !in Looks.indices || (index == state.settings.lookIndex && state.settings.lookVersion > 0)) return
        state = state.copy(settings = state.settings.copy(lookIndex = index, strength = .75f, lookVersion = 3))
        commit(); renderPreview()
    }
    fun edit(settings: EditSettings) {
        if (state.source == null || state.loading) return
        val filmChanged = state.settings.grain != settings.grain || state.settings.filmExposure != settings.filmExposure || state.settings.autoFilmExposure != settings.autoFilmExposure || state.settings.autoFilmExposureMethod != settings.autoFilmExposureMethod
        state = state.copy(settings = settings)
        if (filmChanged) renderPreview(120) else composePreview()
    }
    fun setLookFinish(enabled: Boolean) {
        if (state.source == null || state.loading) return
        val current = state.settings
        val look = Looks[current.lookIndex]
        edit(if (enabled) current.copy(lookVersion = 3,
            strength = (look.filmStrength(current) / look.defaultStrength * .75f).coerceIn(0f, 1f))
        else current.copy(lookVersion = 0, strength = look.filmStrength(current)))
        finishAdjustment()
    }
    fun applyRecommendedStart() {
        if (state.source == null || state.loading || state.exporting) return
        val current = state.settings
        edit(current.withColorRecipe(EditSettings(lookIndex = current.lookIndex)))
        finishAdjustment()
        state = state.copy(notice = "已应用推荐起点：仅重置调色，构图保留；可撤销")
    }
    fun updateStrength(value: Float) = edit(state.settings.withDisplayedStrength(value))
    fun updateBrightness(value: Float) = edit(state.settings.copy(brightness = value.coerceIn(-1f, 1f)))
    fun updateWarmth(value: Float) = edit(state.settings.copy(warmth = value.coerceIn(-1f, 1f)))
    fun updateGrain(value: Float) = edit(state.settings.copy(grain = value.coerceIn(0f, 1f)))
    fun finishAdjustment() {
        commit()
        if (state.source != null && !state.loading && (state.settings.grain != renderedGrain || state.settings.filmExposure != renderedFilmExposure || state.settings.autoFilmExposure != renderedAutoFilmExposure || state.settings.autoFilmExposureMethod != renderedMeterMethod)) renderPreview()
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

    private fun releasePreviewCaches() {
        sourceCache.evictAll(); frameCache.evictAll()
        library = library.copy(thumbnails = emptyMap(), originalThumbnails = emptySet())
        state = state.copy(thumbnails = emptyMap())
    }

    private suspend fun reportFailure(stage: ProcessingStage, error: Throwable) {
        automaticThumbnailsPaused = true
        Log.w("Pianyu", "${stage.name} failed", error)
        diagnosticReport = if (error is ProcessingException) error.report
            else withContext(Dispatchers.IO) { diagnostics.record(stage, error) }
        state = state.copy(error = ProcessingFailure.message(stage, error))
    }
    fun chooseExportLossless(value: Boolean) {
        if (!recipesLoading) { exportFormat = if (value) ExportFormat.PNG else ExportFormat.JPEG; persistRecipes() }
    }
    fun chooseExportFormat(value: ExportFormat) {
        if (!recipesLoading) { exportFormat = value; persistRecipes() }
    }
    fun chooseExportEdge(edge: Int) {
        if (!recipesLoading && edge in listOf(0, 1080, 2048)) { exportEdge = edge; persistRecipes() }
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
        pendingRecipes = SavedRecipes(savedRecipes, exportEdge, exportFormat == ExportFormat.PNG, exportFormat)
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
        if (state.rendering || renderedLook != state.settings.lookIndex || renderedGrain != state.settings.grain || renderedFilmExposure != state.settings.filmExposure || renderedAutoFilmExposure != state.settings.autoFilmExposure || renderedMeterMethod != state.settings.autoFilmExposureMethod) renderPreview()
        else composePreview()
    }
    private fun saveCurrentSettings() {
        val id = activeId ?: return
        libraryThumbJobs.remove(id)?.cancel()
        library = library.copy(entries = library.entries.map { if (it.id == id) it.copy(settings = state.settings) else it },
            thumbnails = library.thumbnails - id, originalThumbnails = library.originalThumbnails - id,
            thumbnailLoading = library.thumbnailLoading - id, thumbnailErrors = library.thumbnailErrors - id)
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

    private fun cacheKey(id: String, settings: EditSettings, source: Bitmap) =
        "$id:${source.width}x${source.height}:${settings.lookIndex}:${settings.grain}:${settings.filmExposure}:${settings.autoFilmExposure}:${settings.autoFilmExposureMethod}"
    private fun cancelThumbnails() { thumbnailJobs.values.forEach { it.cancel() }; thumbnailJobs.clear() }
    private fun cancelLibraryThumbnails() {
        libraryThumbJobs.values.forEach { it.cancel() }; libraryThumbJobs.clear()
        library = library.copy(thumbnailLoading = emptySet())
    }
    private fun renderPreview(debounceMillis: Long = 0) {
        val source = state.source ?: return
        if (state.loading) return
        renderJob?.cancel(); cancelThumbnails()
        val token = ++generation
        val settings = state.settings
        val key = cacheKey(activeId ?: "demo", settings, source)
        frameCache.get(key)?.let {
            renderedLook = settings.lookIndex; renderedGrain = settings.grain
            renderedFilmExposure = settings.filmExposure; renderedAutoFilmExposure = settings.autoFilmExposure; renderedMeterMethod = settings.autoFilmExposureMethod
            state = state.copy(filtered = it, rendering = false, error = null)
            Log.i("PianyuPerf", "preview_cache_hit=true look=${Looks[settings.lookIndex].id}")
            composePreview(); return
        }
        state = state.copy(rendering = true, error = null)
        renderJob = viewModelScope.launch {
            try {
                if (debounceMillis > 0) delay(debounceMillis)
                val start = SystemClock.elapsedRealtime()
                val filtered = withContext(NativeCore.dispatcher) {
                    diagnosticsReady.await()
                    NativeCore.render(getApplication(), source, Looks[settings.lookIndex], settings.grain, settings.filmExposure, autoFilmExposure = settings.autoFilmExposure, autoFilmExposureMethod = settings.autoFilmExposureMethod)
                }
                if (token != generation) return@launch
                frameCache.put(key, filtered)
                renderedLook = settings.lookIndex; renderedGrain = settings.grain
                renderedFilmExposure = settings.filmExposure; renderedAutoFilmExposure = settings.autoFilmExposure; renderedMeterMethod = settings.autoFilmExposureMethod
                state = state.copy(filtered = filtered, rendering = false)
                Log.i("PianyuPerf", "preview_ms=${SystemClock.elapsedRealtime() - start} look=${Looks[settings.lookIndex].id}")
                composePreview()
            } catch (error: CancellationException) { throw error }
            catch (error: OutOfMemoryError) {
                if (token == generation && !recoverPreview(error)) {
                    state = state.copy(rendering = false)
                    reportFailure(ProcessingStage.PREVIEW, error)
                }
            }
            catch (error: Exception) {
                if (token == generation && !recoverPreview(error)) {
                    state = state.copy(rendering = false)
                    reportFailure(ProcessingStage.PREVIEW, error)
                }
            }
        }
    }

    // A single smaller preview retry for allocation failures, never for GPU capability
    // errors. Export always decodes the original URI independently at the chosen size.
    private suspend fun recoverPreview(error: Throwable): Boolean {
        val source = state.source ?: return false
        if (ProcessingFailure.classify(error) != ProcessingFailureKind.ALLOCATION ||
            maxOf(source.width, source.height) <= 640) return false
        releasePreviewCaches()
        val token = generation
        val smaller = try {
            withContext(PhotoIO.previewDispatcher) {
                val scale = 640f / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1), true)
            }
        } catch (_: OutOfMemoryError) { return false }
        if (token != generation) { smaller.recycle(); return true }
        renderedLook = -1; renderedGrain = -1f; renderedFilmExposure = Float.NaN
        state = state.copy(source = smaller, filtered = null,
            notice = "已降低预览分辨率以节省内存，导出尺寸不变")
        // Schedule outside this render job: renderPreview cancels its predecessor.
        viewModelScope.launch { renderPreview() }
        return true
    }

    private fun composePreview() {
        if (state.rendering || renderedLook != state.settings.lookIndex || renderedGrain != state.settings.grain || renderedFilmExposure != state.settings.filmExposure || renderedAutoFilmExposure != state.settings.autoFilmExposure || renderedMeterMethod != state.settings.autoFilmExposureMethod) return
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
                catch (error: OutOfMemoryError) {
                    if (!recoverPreview(error)) reportFailure(ProcessingStage.PREVIEW, error)
                }
                catch (error: Exception) { reportFailure(ProcessingStage.PREVIEW, error) }
                // Keep one computation running and replace only the pending request. Cancelling
                // every slider event would prevent all intermediate frames during a fast drag.
                delay(16)
            }
        }
    }

    fun requestLookThumbnail(index: Int) {
        val source = state.source ?: return
        val look = Looks.getOrNull(index) ?: return
        if (automaticThumbnailsPaused || state.loading || state.rendering || look.id in state.thumbnails || thumbnailJobs[look.id]?.isActive == true) return
        val token = generation
        thumbnailJobs[look.id] = viewModelScope.launch {
            try {
                delay(120)
                val rendered = withContext(NativeCore.dispatcher) {
                    val scale = minOf(1f, 128f / maxOf(source.width, source.height))
                    val small = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1),
                        (source.height * scale).toInt().coerceAtLeast(1), true)
                    val defaults = EditSettings(lookIndex = index)
                    val film = NativeCore.render(getApplication(), small, look, defaults.grain, defaults.filmExposure, autoFilmExposure = defaults.autoFilmExposure, autoFilmExposureMethod = defaults.autoFilmExposureMethod)
                    try { PhotoIO.finish(small, film, defaults) }
                    finally { film.recycle(); if (small !== source) small.recycle() }
                }
                if (token == generation) state = state.copy(thumbnails = state.thumbnails + (look.id to rendered))
            } catch (error: CancellationException) { throw error }
            catch (error: OutOfMemoryError) { Log.w("Pianyu", "look thumbnail allocation failed", error) }
            catch (error: Exception) { Log.w("Pianyu", "look thumbnail unavailable", error) }
        }
    }

    fun toggleSelected(id: String) {
        library = library.copy(selected = if (id in library.selected) library.selected - id else library.selected + id)
    }
    fun selectAll() { library = library.copy(selected = library.entries.map { it.id }.toSet()) }
    fun clearSelection() { library = library.copy(selected = emptySet()) }
    fun removeSelectedFromLibrary() {
        if (library.batch != null || library.importing || state.exporting) return
        val ids = library.selected
        if (ids.isEmpty()) return
        ids.forEach { id ->
            libraryThumbJobs.remove(id)?.cancel()
            thumbnailJobs.keys.filter { it == id }.forEach { thumbnailJobs.remove(it)?.cancel() }
            sourceCache.remove(id)
            frameCache.snapshot().keys.filter { it.startsWith("$id:") }.forEach { frameCache.remove(it) }
        }
        library = library.copy(entries = library.entries.filterNot { it.id in ids },
            thumbnails = library.thumbnails - ids, originalThumbnails = library.originalThumbnails - ids,
            thumbnailErrors = library.thumbnailErrors - ids, thumbnailLoading = library.thumbnailLoading - ids,
            selected = emptySet())
        persist()
        state = state.copy(notice = "已移出照片库 ${ids.size} 张，原文件保留")
    }
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
        }, thumbnails = library.thumbnails.filterKeys { it !in changedIds },
            originalThumbnails = library.originalThumbnails - changedIds, thumbnailErrors = library.thumbnailErrors - changedIds,
            thumbnailLoading = library.thumbnailLoading - changedIds)
        frameCache.evictAll()
        persist()
        state = state.copy(notice = "已为 $count 张照片套用调色，裁切与旋转已保留")
    }

    fun export(share: Boolean, onReady: (Uri) -> Unit) {
        if (state.source == null || state.loading || state.exporting || library.batch != null) return
        cancelThumbnails()
        cancelLibraryThumbnails()
        val settings = state.settings; val uri = sourceUri; val edge = exportEdge; val format = exportFormat
        state = state.copy(exporting = true, error = null)
        viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                val output = renderExport(uri, settings, edge, share, format)
                state = state.copy(exporting = false, notice = if (share) null else "已保存到相册 · 片屿")
                Log.i("PianyuPerf", "export_ms=${SystemClock.elapsedRealtime() - start} edge=$edge share=$share")
                onReady(output)
            } catch (error: CancellationException) { throw error }
            catch (error: OutOfMemoryError) { state = state.copy(exporting = false); reportFailure(ProcessingStage.EXPORT, error) }
            catch (error: Exception) { state = state.copy(exporting = false); reportFailure(ProcessingStage.EXPORT, error) }
        }
    }

    fun shareOriginal(onReady: (Uri) -> Unit) {
        val uri = sourceUri ?: return
        if (state.exporting || state.loading || library.batch != null) return
        state = state.copy(exporting = true, error = null)
        viewModelScope.launch {
            try {
                val copy = withContext(Dispatchers.IO) { PhotoIO.shareSource(getApplication(), uri) }
                state = state.copy(exporting = false)
                onReady(copy)
            } catch (error: Exception) {
                state = state.copy(exporting = false, error = "分享原片失败：${error.message}")
            }
        }
    }

    private suspend fun renderExport(uri: Uri?, settings: EditSettings, edge: Int, share: Boolean, format: ExportFormat): Uri =
        withContext(Dispatchers.IO) {
            diagnosticsReady.await()
            diagnostics.checkpoint(ProcessingStage.EXPORT)
            val original = try { PhotoIO.decode(getApplication(), uri, edge) }
                finally { diagnostics.finished() }
            var rendered: Bitmap? = null; var finished: Bitmap? = null
            try {
                val film = withContext(NativeCore.dispatcher) {
                    NativeCore.render(getApplication(), original, Looks[settings.lookIndex], settings.grain, settings.filmExposure, ProcessingStage.EXPORT, settings.autoFilmExposure, settings.autoFilmExposureMethod)
                }
                rendered = film
                diagnostics.checkpoint(ProcessingStage.EXPORT, original.width, original.height, Looks[settings.lookIndex].id)
                try {
                    val output = PhotoIO.finish(original, film, settings); finished = output
                    PhotoIO.save(getApplication(), output, share, format == ExportFormat.PNG, format)
                } catch (error: CancellationException) { throw error }
                catch (error: OutOfMemoryError) { throw ProcessingException(diagnostics.record(ProcessingStage.EXPORT, error), error) }
                catch (error: Exception) { throw ProcessingException(diagnostics.record(ProcessingStage.EXPORT, error), error) }
                finally { diagnostics.finished() }
            } finally { listOfNotNull(original, rendered, finished).distinct().forEach { it.recycle() } }
        }

    fun exportSelected() {
        if (library.batch != null || state.exporting) return
        val selected = library.entries.filter { it.id in library.selected }
        if (selected.isEmpty()) return
        cancelThumbnails(); batchCancel = false
        cancelLibraryThumbnails()
        val edge = exportEdge; val format = exportFormat
        library = library.copy(batch = BatchProgress(0, selected.size), lastBatch = null)
        viewModelScope.launch {
            var saved = 0; var failed = 0
            val outputs = mutableListOf<Uri>()
            for (photo in selected) {
                if (batchCancel) break
                try { outputs += renderExport(photo.contentUri(), photo.settings, edge, false, format); saved++ }
                catch (error: CancellationException) { throw error }
                catch (error: OutOfMemoryError) { failed++; Log.w("Pianyu", "batch item failed: out of memory", error) }
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


