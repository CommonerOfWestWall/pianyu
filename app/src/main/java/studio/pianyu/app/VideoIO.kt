package studio.pianyu.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
internal object VideoIO {
    data class Source(val uri: Uri, val motion: MotionPhoto.Clip? = null)

    fun identify(context: Context, uri: Uri): Source? {
        val mime = context.contentResolver.getType(uri)
        if (mime?.startsWith("video/") == true) return Source(uri)
        if (mime != null && mime != "image/jpeg" && mime != "image/jpg" &&
            mime != "application/octet-stream") return null
        val resolver = context.contentResolver
        val clip = resolver.openInputStream(uri)?.use(MotionPhoto::read) ?: return null
        // Some providers return only the still image and retain its XMP.
        val complete = resolver.openAssetFileDescriptor(uri, "r")?.use { asset ->
            asset.length < 0 || asset.createInputStream().use { MotionPhoto.hasVideo(it, asset.length, clip) }
        } ?: true
        return if (complete) Source(uri, clip) else null
    }

    fun preview(context: Context, source: Source): Bitmap {
        if (source.motion != null) return PhotoIO.decode(context, source.uri, 960)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, source.uri)
            fun metadata(key: Int) = retriever.extractMetadata(key)?.toIntOrNull()
            val width = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            check(width != null && height != null && width > 0 && height > 0) { "无法读取视频尺寸" }
            val rotated = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) in listOf(90, 270)
            val scale = minOf(1.0, 960.0 / maxOf(width, height))
            val w = ((if (rotated) height else width) * scale).roundToInt().coerceAtLeast(1)
            val h = ((if (rotated) width else height) * scale).roundToInt().coerceAtLeast(1)
            val positionUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) * 500
            val bitmap = retriever.getScaledFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST, w, h)
                ?: error("无法读取视频预览")
            if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
            return try { bitmap.copy(Bitmap.Config.ARGB_8888, false) } finally { bitmap.recycle() }
        } finally { retriever.release() }
    }

    suspend fun grade(context: Context, original: Bitmap, settings: EditSettings): Bitmap =
        withContext(NativeCore.dispatcher) {
            // Use fixed exposure and no spatial filters, like the video LUT.
            val look = Looks[settings.lookIndex].copy(halo = false)
            val film = NativeCore.render(context, original, look, filmExposure = settings.filmExposure,
                printStock = settings.printStock, printExposure = settings.printExposure)
            try { PhotoIO.finish(original, film, settings) } finally { film.recycle() }
        }

    suspend fun save(context: Context, source: Source, settings: EditSettings,
        asMotion: Boolean = source.motion != null, progress: (Int) -> Unit): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "video-jobs").apply { mkdirs() }
        val output = File.createTempFile("graded-", ".mp4", directory)
        val motion = source.motion
        var extracted: File? = null
        var lut: Bitmap? = null
        try {
            val input = if (motion == null) source.uri else {
                val clip = File.createTempFile("source-", ".mp4", directory)
                extracted = clip
                extractMotion(context, source.uri, motion, clip)
                Uri.fromFile(clip)
            }
            lut = colorLut(context, settings)
            val result = transform(context, input, lut, output, progress)
            check(output.length() > 0) { "视频导出为空" }
            if (motion == null || !asMotion) {
                publish(context, false) { out -> output.inputStream().use { it.copyTo(out) } }
            } else {
                val cover = PhotoIO.decode(context, source.uri, 0)
                try {
                    val graded = grade(context, cover, settings)
                    try {
                        val time = motion.coverTimeUs.takeIf { it >= 0 &&
                            (result.durationMs < 0 || it / 1000 <= result.durationMs) } ?: -1
                        publish(context, true) { out -> MotionPhoto.write(graded, output, time, out) }
                    } finally { graded.recycle() }
                } finally { cover.recycle() }
            }
        } finally {
            lut?.recycle()
            extracted?.delete()
            output.delete()
        }
    }

    private fun extractMotion(context: Context, uri: Uri, motion: MotionPhoto.Clip, output: File) {
        val resolver = context.contentResolver
        val length = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
        if (length >= 0) {
            checkNotNull(resolver.openInputStream(uri)).use { MotionPhoto.extract(it, length, motion, output) }
            return
        }
        // Some document providers expose a stream without a declared length.
        val container = File.createTempFile("motion-", ".jpg", output.parentFile)
        try {
            checkNotNull(resolver.openInputStream(uri)).use { input ->
                container.outputStream().use { input.copyTo(it) }
            }
            container.inputStream().use { MotionPhoto.extract(it, container.length(), motion, output) }
        } finally { container.delete() }
    }

    private suspend fun colorLut(context: Context, settings: EditSettings): Bitmap {
        val size = 33
        // Media3's HALD layout is red on planes, green on rows, blue on columns.
        // Its SDR video values use the SMPTE 170M transfer, while photo input is sRGB.
        val samples = IntArray(size) { i ->
            val v = i.toDouble() / (size - 1)
            val linear = if (v < .081) v / 4.5 else ((v + .099) / 1.099).pow(1 / .45)
            val srgb = if (linear <= .0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - .055
            (srgb * 255).roundToInt().coerceIn(0, 255)
        }
        val pixels = IntArray(size * size * size) { i ->
            Color.rgb(samples[i / (size * size)], samples[i / size % size], samples[i % size])
        }
        val input = Bitmap.createBitmap(pixels, size, size * size, Bitmap.Config.ARGB_8888)
        val graded = try { grade(context, input, settings) } finally { input.recycle() }
        try { graded.getPixels(pixels, 0, size, 0, 0, size, size * size) } finally { graded.recycle() }
        val encode = IntArray(256) { i ->
            val v = i / 255.0
            val linear = if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
            val video = if (linear < .018) 4.5 * linear else 1.099 * linear.pow(.45) - .099
            (255 * video).roundToInt().coerceIn(0, 255)
        }
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = Color.rgb(encode[Color.red(p)], encode[Color.green(p)], encode[Color.blue(p)])
        }
        return Bitmap.createBitmap(pixels, size, size * size, Bitmap.Config.ARGB_8888)
    }

    private suspend fun transform(context: Context, uri: Uri, lut: Bitmap, output: File,
        progress: (Int) -> Unit): ExportResult = withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            val holder = ProgressHolder()
            val builder = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoFrameProcessorFactory(DefaultVideoFrameProcessor.Factory.Builder()
                    .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL).build())
            var polling: kotlinx.coroutines.Job? = null
            var active: Transformer? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    val transformer = builder.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(exportResult)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult,
                            exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    }).build()
                    active = transformer
                    val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                        .setEffects(Effects(emptyList(), listOf(SingleColorLut.createFromBitmap(lut)))).build()
                    val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build())
                        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
                    // Transformer creates the output itself.
                    check(output.delete())
                    transformer.start(composition, output.absolutePath)
                    polling = launch {
                        while (true) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                progress(holder.progress.coerceIn(0, 99))
                            }
                            delay(200)
                        }
                    }
                }
            } finally {
                polling?.cancel()
                active?.cancel()
            }
        }
    }

    private suspend fun publish(context: Context, motion: Boolean, write: (java.io.OutputStream) -> Unit): Uri {
        val job = currentCoroutineContext()
        job.ensureActive()
        val collection = if (motion) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            val suffix = if (motion) "_MP.jpg" else ".mp4"
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Pianyu_${System.currentTimeMillis()}$suffix")
            put(MediaStore.MediaColumns.MIME_TYPE, if (motion) "image/jpeg" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (motion) "Pictures/片屿" else "Movies/片屿")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(collection, values)) { "无法创建相册文件" }
        var published = false
        try {
            checkNotNull(resolver.openOutputStream(uri)).use(write)
            job.ensureActive()
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) > 0)
            published = true
            return uri
        } finally {
            if (!published) resolver.delete(uri, null, null)
        }
    }
}

