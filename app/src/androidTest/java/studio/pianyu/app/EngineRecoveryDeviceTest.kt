package studio.pianyu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineRecoveryDeviceTest {
    @Test fun injectedLossRecreatesRealNativeEngineWithoutChangingPixels() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = PhotoIO.decode(context, null, 180)
        withContext(NativeCore.dispatcher) {
            val baseline = NativeCore.render(context, source, Looks.first(), grain = 0f)
            try {
                var attempts = 0; var resets = 0
                val invalidate = NativeCore::class.java.getDeclaredMethod("discardLostEngine").apply { isAccessible = true }
                val rebuilt = retryLostDevice(invalidate = { resets++; invalidate.invoke(NativeCore) }) {
                    if (++attempts == 1) throw IllegalStateException("injected VkResult -4")
                    NativeCore.render(context, source, Looks.first(), grain = 0f)
                }
                try {
                    assertEquals(2, attempts)
                    assertEquals(1, resets)
                    assertTrue("recovery must preserve source dimensions and film pixels", baseline.sameAs(rebuilt))
                } finally { rebuilt.recycle() }
            } finally { baseline.recycle(); source.recycle() }
        }
    }
}
