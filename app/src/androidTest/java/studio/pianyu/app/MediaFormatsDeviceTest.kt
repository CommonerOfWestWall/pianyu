package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MediaFormatsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val settings = EditSettings(lookIndex = 1, grain = 0f, autoFilmExposure = false,
        filmExposure = .5f, printStock = "kodak_2393", printExposure = .2f)

    @Test(timeout = 90_000)
    fun videoChangesAllColorPatchesAndPreservesAudioAndDuration() = runBlocking {
        withSample { video, input ->
            val source = checkNotNull(VideoIO.identify(context, input))
            assertNull(source.motion)
            val output = VideoIO.save(context, source, settings) {}
            try {
                assertEquals("video/mp4", resolver.getType(output))
                val outFile = File(video.parentFile, "export.mp4")
                resolver.openInputStream(output)!!.use { stream -> outFile.outputStream().use(stream::copyTo) }
                assertAudioTrack(outFile)
                assertVideoGrade(input, output)
            } finally { resolver.delete(output, null, null) }
        }
    }

    @Test(timeout = 90_000)
    fun jpegMotionPhotoRoundTripsWithAdjustedCoverAndVideo() = runBlocking {
        withSample { video, input ->
            val original = VideoIO.preview(context, VideoIO.Source(input))
            val coverTimeUs = 625_000L
            val container = File(video.parentFile, "input_MP.jpg")
            try {
                container.outputStream().use { MotionPhoto.write(original, video, coverTimeUs, it) }
                val source = checkNotNull(VideoIO.identify(context, uri(container)))
                assertEquals(MotionPhoto.Clip(video.length(), coverTimeUs), source.motion)
                val stripped = File(video.parentFile, "cover-only.jpg")
                container.copyTo(stripped)
                java.io.RandomAccessFile(stripped, "rw").use { it.setLength(container.length() - video.length()) }
                assertNull("stale XMP is insufficient", VideoIO.identify(context, uri(stripped)))
                val output = VideoIO.save(context, source, settings) {}
                try {
                    val outFile = File(video.parentFile, "motion_MP.jpg")
                    resolver.openInputStream(output)!!.use { stream -> outFile.outputStream().use(stream::copyTo) }
                    val clip = checkNotNull(outFile.inputStream().use(MotionPhoto::read))
                    assertEquals(coverTimeUs, clip.coverTimeUs)
                    val outVideo = File(video.parentFile, "motion.mp4")
                    outFile.inputStream().use { MotionPhoto.extract(it, outFile.length(), clip, outVideo) }
                    assertAudioTrack(outVideo)
                    assertVideoGrade(input, uri(outVideo))
                    val result = PhotoIO.decode(context, output, 0)
                    try {
                        assertEquals(original.width, result.width)
                        assertEquals(original.height, result.height)
                        assertTrue("the encoded still has changed",
                            distance(original.getPixel(80, 60), result.getPixel(80, 60)) > 15)
                    } finally { result.recycle() }
                } finally { resolver.delete(output, null, null) }
                val mp4 = VideoIO.save(context, source, settings, asMotion = false) {}
                try {
                    assertEquals("video/mp4", resolver.getType(mp4))
                    assertVideoGrade(input, mp4)
                } finally { resolver.delete(mp4, null, null) }
            } finally { original.recycle() }
        }
    }

    private suspend fun withSample(test: suspend (File, Uri) -> Unit) {
        val directory = File.createTempFile("media-test-", ".tmp", File(context.cacheDir, "exports").apply { mkdirs() })
        check(directory.delete() && directory.mkdir())
        try {
            val video = File(directory, "input.mp4")
            instrumentation.context.assets.open("media/grades.mp4").use { input ->
                video.outputStream().use(input::copyTo)
            }
            test(video, uri(video))
        } finally { directory.deleteRecursively() }
    }

    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun assertAudioTrack(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val audio = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            assertNotNull("audio track survives", audio)
            extractor.selectTrack(audio!!)
            assertTrue("audio contains samples", extractor.sampleTime >= 0)
        } finally { extractor.release() }
    }

    private suspend fun assertVideoGrade(input: Uri, output: Uri) {
        val originalSource = VideoIO.Source(input)
        val original = VideoIO.preview(context, originalSource)
        val outputFrame = VideoIO.preview(context, VideoIO.Source(output))
        val expected = VideoIO.grade(context, original, settings)
        try {
            assertEquals(original.width, outputFrame.width)
            assertEquals(original.height, outputFrame.height)
            assertTrue(abs(duration(input) - duration(output)) < 150)
            var previewError = 0
            var visualChange = 0
            listOf(80 to 60, 240 to 60, 80 to 180, 240 to 180).forEach { (x, y) ->
                val actual = outputFrame.getPixel(x, y)
                val rendered = expected.getPixel(x, y)
                previewError += distance(actual, rendered)
                visualChange += distance(actual, original.getPixel(x, y))
            }
            assertTrue("four colors change ($visualChange summed RGB difference)", visualChange > 65)
            assertTrue("LUT matches the still preview ($previewError summed RGB error)", previewError < 95)
            android.util.Log.i("PianyuMediaTest", "preview_rgb_mae=${previewError / 12f} grade_rgb_mae=${visualChange / 12f} duration_ms=${duration(output)} audio=true")
        } finally {
            original.recycle()
            expected.recycle()
            outputFrame.recycle()
        }
    }

    private fun duration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return checkNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
        } finally { retriever.release() }
    }

    private fun distance(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) +
        abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
}

