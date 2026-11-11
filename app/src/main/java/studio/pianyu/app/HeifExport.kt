package studio.pianyu.app

import android.graphics.Bitmap
import android.media.MediaCodecList
import androidx.heifwriter.HeifWriter
import java.io.File

object HeifExport {
    private const val STOP_TIMEOUT_MS = 30_000L

    fun isEncoderAvailable(): Boolean = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
        info.isEncoder && info.supportedTypes.any {
            it.equals("video/hevc", ignoreCase = true) || it.equals("image/vnd.android.heic", ignoreCase = true)
        }
    }

    fun encode(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        var writer: HeifWriter? = null
        try {
            writer = HeifWriter.Builder(file.absolutePath, bitmap.width, bitmap.height,
                HeifWriter.INPUT_MODE_BITMAP).setQuality(100).setGridEnabled(true).build()
            writer.start(); writer.addBitmap(bitmap); writer.stop(STOP_TIMEOUT_MS)
        } catch (error: OutOfMemoryError) {
            file.delete(); throw error
        } catch (error: Exception) {
            file.delete(); throw IllegalStateException("HEIF编码失败，请改用JPEG或PNG", error)
        } finally {
            // WriterBase.close is exposed through its public AutoCloseable contract;
            // calling the interface avoids the library-internal restricted declaration.
            runCatching { (writer as AutoCloseable?)?.close() }
            if (!file.isFile || file.length() == 0L) file.delete()
        }
    }
}
