package studio.pianyu.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeRenderDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext

    @Test
    fun singleOriginalRenderProbe() {
        assumeTrue("realPhotos=true is required for the device probe",
            InstrumentationRegistry.getArguments().getString("realPhotos") == "true")
        val variant = InstrumentationRegistry.getArguments().getString("probe", "base")
        assumeTrue("probe must be base/no-grain/no-ae/plain", variant in setOf("base", "no-grain", "no-ae", "plain"))
        val entry = LibraryStore(target).read().entries.firstOrNull { it.contentUri() != null &&
            !it.name.lowercase().contains("demo") && it.id != "demo-original-v1" }
        assumeTrue("no non-demo library photo is available", entry != null)
        val photo = entry!!.contentUri()!!
        var original: Bitmap? = null
        var rendered: Bitmap? = null
        try {
            val input = PhotoIO.decode(target, photo, 0)
            original = input
            val settings = entry.settings
            val grain = if (variant == "no-grain" || variant == "plain") 0f else settings.grain
            val autoExposure = if (variant == "no-ae" || variant == "plain") false else settings.autoFilmExposure
            val started = SystemClock.elapsedRealtime()
            try {
                rendered = runBlocking { withContext(NativeCore.dispatcher) { NativeCore.render(target, input, Looks[settings.lookIndex], grain,
                    settings.filmExposure, ProcessingStage.EXPORT, autoExposure,
                    settings.autoFilmExposureMethod, settings.printStock, settings.printExposure) } }
                Log.i("PianyuLargeRender", "variant=$variant width=${original.width} height=${original.height} " +
                    "render_ms=${SystemClock.elapsedRealtime() - started} result=${rendered.width}x${rendered.height}")
            } catch (error: Throwable) {
                Log.e("PianyuLargeRender", "variant=$variant width=${original.width} height=${original.height} " +
                    "render_ms=${SystemClock.elapsedRealtime() - started} error=${causeChain(error)}")
                throw error
            }
        } finally {
            rendered?.takeIf { it !== original }?.recycle()
            original?.recycle()
        }
    }

    private fun causeChain(error: Throwable): String = generateSequence(error) { it.cause }
        .joinToString(" -> ") { cause ->
            val message = cause.message.orEmpty().replace(Regex("content://\\S+"), "<uri>")
            "${cause.javaClass.simpleName}:${message.take(160)}"
        }
}
