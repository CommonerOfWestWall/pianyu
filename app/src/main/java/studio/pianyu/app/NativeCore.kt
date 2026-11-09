package studio.pianyu.app

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.Executors
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.asCoroutineDispatcher

object NativeCore {
    // One serialized engine per app process; sessions/results are released by JNI after each render.
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var handle = 0L
    private var loaded = false

    private external fun create(path: String): Long
    private external fun process(handle: Long, source: Bitmap, params: String): Bitmap

    fun render(context: Context, source: Bitmap, look: Look, grain: Float = 0f): Bitmap {
        warmup(context)
        return process(handle, source, look.params(grain))
    }

    fun warmup(context: Context) {
        val started = SystemClock.elapsedRealtime()
        if (!loaded) { System.loadLibrary("pianyu_jni"); loaded = true }
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
