package studio.pianyu.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.util.concurrent.Executors
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.asCoroutineDispatcher
import kotlin.coroutines.EmptyCoroutineContext
import org.json.JSONObject

object NativeCore {
    // One serialized engine per app process; sessions/results are released by JNI after each render.
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var handle = 0L
    private var loaded = false

    private external fun create(path: String): Long
    private external fun destroy(handle: Long)
    private external fun process(handle: Long, source: Bitmap, params: String): Bitmap
    private external fun createRaw(path: String, edge: Int): Long
    private external fun destroyRaw(handle: Long)
    private external fun previewRaw(rawHandle: Long): Bitmap
    private external fun processRaw(handle: Long, rawHandle: Long, params: String): Bitmap

    fun render(context: Context, source: Bitmap, look: Look, grain: Float = 0f, filmExposure: Float = 0f,
        stage: ProcessingStage = ProcessingStage.PREVIEW, autoFilmExposure: Boolean = false,
        autoFilmExposureMethod: String = "protect_highlights", printStock: String = "",
        printExposure: Float = 0f): Bitmap {
        if (handle == 0L) warmup(context)
        return tracked(context, stage, source.width, source.height, look.id) {
            retryLostDevice(invalidate = ::discardLostEngine) {
                if (handle == 0L) ensureEngine(context)
                process(handle, source, look.params(grain, filmExposure, autoFilmExposure, autoFilmExposureMethod, printStock, printExposure))
            }
        }
    }

    private fun discardLostEngine() {
        val lost = handle; handle = 0L
        Log.w("PianyuCore", "Vulkan device lost; discarding engine before retry/next photo")
        if (lost != 0L) destroy(lost)
    }

    fun warmup(context: Context) = tracked(context, ProcessingStage.ENGINE) { ensureEngine(context) }

    /** Called on dispatcher. The cache file is needed only during decode. */
    fun openRaw(context: Context, uri: Uri, edge: Int): Long = tracked(context, ProcessingStage.DECODE) {
        ensureLibrary()
        val directory = File(context.cacheDir, "raw-jobs").apply { mkdirs() }
        val file = File.createTempFile("raw-", ".dng", directory)
        try {
            val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "无法读取 RAW 原片" }
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            createRaw(file.absolutePath, edge).also { check(it != 0L) { "RAW 解码未能启动" } }
        } finally { file.delete() }
    }

    // Queue release after any render already running on the engine thread.
    fun releaseRaw(rawHandle: Long) {
        if (rawHandle != 0L) dispatcher.dispatch(EmptyCoroutineContext, Runnable { destroyRaw(rawHandle) })
    }

    fun rawPreview(rawHandle: Long): Bitmap = previewRaw(rawHandle)

    fun rawThumbnail(context: Context, uri: Uri, edge: Int): Bitmap {
        val raw = openRaw(context, uri, edge)
        return try { rawPreview(raw) } finally { destroyRaw(raw) }
    }

    fun renderRaw(context: Context, rawHandle: Long, look: Look, grain: Float = 0f,
        filmExposure: Float = 0f, stage: ProcessingStage = ProcessingStage.PREVIEW,
        autoFilmExposure: Boolean = false, autoFilmExposureMethod: String = "protect_highlights",
        printStock: String = "", printExposure: Float = 0f): Bitmap {
        require(rawHandle != 0L) { "RAW session is not initialized" }
        if (handle == 0L) warmup(context)
        return tracked(context, stage, look = look.id) {
            retryLostDevice(invalidate = ::discardLostEngine) {
                if (handle == 0L) ensureEngine(context)
                val params = JSONObject(look.params(grain, filmExposure, autoFilmExposure,
                    autoFilmExposureMethod, printStock, printExposure))
                    .put("input_color_space", "ProPhoto RGB").put("input_cctf_decoding", false)
                processRaw(handle, rawHandle, params.toString())
            }
        }
    }

    // Called on the serialized engine dispatcher, including the disk checkpoint.
    // A native crash cannot be caught by Kotlin; its last stage survives restart.
    private fun <T> tracked(context: Context, stage: ProcessingStage, width: Int? = null,
        height: Int? = null, look: String? = null, block: () -> T): T {
        val diagnostics = ProcessingDiagnostics(context, nativeCheckpoint = true)
        diagnostics.checkpoint(stage, width, height, look)
        try { return block() }
        catch (error: OutOfMemoryError) { throw ProcessingException(diagnostics.record(stage, error), error) }
        catch (error: LinkageError) {
            throw ProcessingException(diagnostics.record(stage, error), error)
        }
        catch (error: Exception) { throw ProcessingException(diagnostics.record(stage, error), error) }
        finally { diagnostics.finished() }
    }

    private fun ensureLibrary() {
        if (!loaded) { System.loadLibrary("pianyu_jni"); loaded = true }
    }

    private fun ensureEngine(context: Context) {
        val started = SystemClock.elapsedRealtime()
        ensureLibrary()
        if (handle == 0L) {
            val version = context.assets.open("engine-version.txt").bufferedReader().use { it.readText().trim() }
            val root = File(context.filesDir, "engine")
            val marker = File(root, "version.txt")
            if (!marker.exists() || marker.readText() != version) {
                // Only our private engine bundle is replaced, before any engine is created.
                check(root.canonicalFile.parentFile == context.filesDir.canonicalFile)
                if (root.exists()) check(root.deleteRecursively()) { "无法更新图像资源" }
                copyAssets(context, "engine", root)
                marker.writeText(version)
            }
            handle = create(root.absolutePath)
            check(handle != 0L) { "图像引擎未能启动" }
        }
        Log.d("PianyuPerf", "engine_ready_ms=${SystemClock.elapsedRealtime() - started}")
    }

    private fun copyAssets(context: Context, name: String, destination: File) {
        val children = context.assets.list(name).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(name).use { input -> destination.outputStream().use(input::copyTo) }
        } else {
            destination.mkdirs()
            children.forEach { copyAssets(context, "$name/$it", File(destination, it)) }
        }
    }
}
