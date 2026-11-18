package studio.pianyu.app

import android.graphics.Bitmap
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** JPEG Motion Photo 1.0, plus legacy JPEG MicroVideo input. */
internal object MotionPhoto {
    private const val CAMERA = "http://ns.google.com/photos/1.0/camera/"
    private const val CONTAINER = "http://ns.google.com/photos/1.0/container/"
    private const val ITEM = "http://ns.google.com/photos/1.0/container/item/"
    private val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    data class Clip(val length: Long, val coverTimeUs: Long)

    fun read(input: InputStream): Clip? {
        val data = DataInputStream(input.buffered())
        if (data.read() != 0xff || data.read() != 0xd8) return null
        // XMP is in a JPEG APP1 segment preceding the image scan.
        return try {
            while (data.readUnsignedByte() == 0xff) {
                var marker = data.readUnsignedByte()
                while (marker == 0xff) marker = data.readUnsignedByte()
                if (marker == 0xda || marker == 0xd9) break
                val size = data.readUnsignedShort() - 2
                if (size < 0) break
                if (marker == 0xe1) {
                    val segment = ByteArray(size)
                    data.readFully(segment)
                    if (size >= xmpHeader.size && xmpHeader.indices.all { segment[it] == xmpHeader[it] }) {
                        parseXmp(ByteArrayInputStream(segment, xmpHeader.size, size - xmpHeader.size))?.let { return it }
                    }
                } else {
                    data.skipFully(size.toLong())
                }
            }
            null
        } catch (_: java.io.EOFException) { null }
        catch (_: org.xmlpull.v1.XmlPullParserException) { null }
    }

    private fun parseXmp(input: InputStream): Clip? {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, "UTF-8")
        }
        var motion: Int? = null
        var time = -1L
        var length = 0L
        var legacy = false
        var legacyTime = -1L
        var legacyLength = 0L
        var lastItemWasMotion = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            fun attribute(namespace: String, name: String) = parser.getAttributeValue(namespace, name)
            attribute(CAMERA, "MotionPhoto")?.toIntOrNull()?.let { motion = it }
            attribute(CAMERA, "MotionPhotoPresentationTimestampUs")?.toLongOrNull()?.let { time = it }
            attribute(CAMERA, "MicroVideo")?.let { legacy = it == "1" }
            attribute(CAMERA, "MicroVideoOffset")?.toLongOrNull()?.let { legacyLength = it }
            attribute(CAMERA, "MicroVideoPresentationTimestampUs")?.toLongOrNull()?.let { legacyTime = it }
            val semantic = attribute(ITEM, "Semantic") ?: continue
            lastItemWasMotion = semantic == "MotionPhoto" && attribute(ITEM, "Mime") in setOf("video/mp4", "video/quicktime")
            if (lastItemWasMotion) length = attribute(ITEM, "Length")?.toLongOrNull() ?: 0L
        }
        return when {
            motion == 1 && lastItemWasMotion && length > 0 -> Clip(length, time.coerceAtLeast(-1))
            motion == null && legacy && legacyLength > 0 -> Clip(legacyLength, legacyTime.coerceAtLeast(-1))
            else -> null
        }
    }

    private fun videoHeader(input: InputStream, totalBytes: Long, clip: Clip): ByteArray? {
        if (totalBytes < 16 || clip.length !in 12..(totalBytes - 4)) return null
        return try {
            input.skipFully(totalBytes - clip.length)
            val header = ByteArray(8)
            DataInputStream(input).readFully(header)
            header.takeIf { String(it, 4, 4, Charsets.US_ASCII) == "ftyp" }
        } catch (_: java.io.EOFException) { null }
    }

    fun hasVideo(input: InputStream, totalBytes: Long, clip: Clip) = videoHeader(input, totalBytes, clip) != null

    fun extract(input: InputStream, totalBytes: Long, clip: Clip, output: File) {
        val header = requireNotNull(videoHeader(input, totalBytes, clip)) { "实况照片的尾部视频格式不受支持或数据不完整" }
        var remaining = clip.length - header.size
        output.outputStream().buffered().use { out ->
            out.write(header)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                check(count > 0) { "实况照片的视频数据不完整" }
                out.write(buffer, 0, count)
                remaining -= count
            }
        }
    }

    fun write(cover: Bitmap, video: File, coverTimeUs: Long, output: OutputStream) {
        // Add XMP to the small JPEG stream header without buffering the whole cover.
        val jpeg = File.createTempFile("cover-", ".jpg", video.parentFile)
        try {
            jpeg.outputStream().use { check(cover.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            jpeg.inputStream().buffered().use { still ->
                check(still.read() == 0xff && still.read() == 0xd8)
                output.write(0xff)
                output.write(0xd8)
                val xmp = xmpHeader + xmp(video.length(), coverTimeUs).toByteArray(Charsets.UTF_8)
                val segmentLength = xmp.size + 2
                output.write(0xff)
                output.write(0xe1)
                output.write(segmentLength shr 8)
                output.write(segmentLength and 0xff)
                output.write(xmp)
                still.copyTo(output)
            }
            video.inputStream().use { it.copyTo(output) }
        } finally { jpeg.delete() }
    }

    private fun xmp(length: Long, timeUs: Long) = """
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:Camera="$CAMERA" xmlns:Container="$CONTAINER" xmlns:Item="$ITEM"
                Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1" Camera:MotionPhotoPresentationTimestampUs="$timeUs">
              <Container:Directory><rdf:Seq>
                <rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg" Item:Length="0" Item:Padding="0"/></rdf:li>
                <rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="MotionPhoto" Item:Mime="video/mp4" Item:Length="$length"/></rdf:li>
              </rdf:Seq></Container:Directory>
            </rdf:Description>
          </rdf:RDF>
        </x:xmpmeta>
    """.trimIndent()

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (read() >= 0) remaining--
            else throw java.io.EOFException()
        }
    }
}

