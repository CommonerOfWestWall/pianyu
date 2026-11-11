package studio.pianyu.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.ColorMatrix
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers

object PhotoIO {
    // Do not manually recycle UI bitmaps: Compose may still be drawing the previous frame.
    // Bound CPU previews instead; unreachable frames are reclaimed by Android's bitmap GC.
    val previewDispatcher = Dispatchers.Default.limitedParallelism(1)
    fun decode(context: Context, uri: Uri?, edge: Int): Bitmap {
        val source = if (uri != null) ImageDecoder.createSource(context.contentResolver, uri)
        else ImageDecoder.createSource(ByteBuffer.wrap(context.assets.open("demo.jpg").use { it.readBytes() }))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // A non-positive edge means original dimensions.  Preview callers pass 960;
            // exports may pass 0 and must never be silently capped at 2048.
            if (edge > 0) {
                val scale = minOf(1.0, edge.toDouble() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }.let { decoded ->
            if (decoded.config == Bitmap.Config.ARGB_8888) decoded
            else try { decoded.copy(Bitmap.Config.ARGB_8888, false) }
            finally { decoded.recycle() } // Private intermediate, never handed to Compose.
        }
    }

    fun shareSource(context: Context, uri: Uri): Uri {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        directory.listFiles()?.filter { it.name.startsWith("Pianyu_source_") &&
            it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val suffix = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val file = File(directory, "Pianyu_source_${System.currentTimeMillis()}.$suffix")
        context.contentResolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "无法读取原片" }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun crop(source: Bitmap, aspect: Float): Bitmap {
        if (aspect <= 0f) return source
        val width = minOf(source.width, (source.height * aspect).roundToInt()).coerceAtLeast(1)
        val height = minOf(source.height, (source.width / aspect).roundToInt()).coerceAtLeast(1)
        return Bitmap.createBitmap(source, (source.width - width) / 2, (source.height - height) / 2, width, height)
    }

    fun colorMatrix(brightness: Float, warmth: Float): ColorMatrix {
        val offset = brightness.coerceIn(-1f, 1f) * 45f
        val warm = warmth.coerceIn(-1f, 1f) * .12f
        return ColorMatrix(floatArrayOf(
            1f + warm, 0f, 0f, 0f, offset,
            0f, 1f, 0f, 0f, offset,
            0f, 0f, 1f - warm, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    fun finish(original: Bitmap, filtered: Bitmap, settings: EditSettings): Bitmap {
        val strength = Looks[settings.lookIndex].filmStrength(settings)
        val blended = when {
            strength <= 0f -> original.copy(Bitmap.Config.ARGB_8888, false)
            strength >= 1f -> filtered.copy(Bitmap.Config.ARGB_8888, false)
            else -> blend(original, filtered, strength)
        }
        val adjusted = applyAdjustments(blended, Looks[settings.lookIndex].finishSettings(settings))
        if (adjusted !== blended) blended.recycle()
        val result = PhotoGeometry.apply(adjusted, settings)
        if (result !== adjusted) adjusted.recycle()
        return if (result.config == Bitmap.Config.ARGB_8888) result else result.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun blend(original: Bitmap, filtered: Bitmap, strength: Float): Bitmap {
        require(original.width == filtered.width && original.height == filtered.height)
        // Skia performs this crossfade in native code, without three full-size IntArrays
        // or a Kotlin per-pixel loop. Complementary weights also handle image alpha.
        val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val weight = (strength.coerceIn(0f, 1f) * 255f).roundToInt()
        val paint = Paint().apply { alpha = 255 - weight; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        Canvas(output).apply {
            drawBitmap(original, 0f, 0f, paint)
            paint.alpha = weight; paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            drawBitmap(filtered, 0f, 0f, paint)
        }
        return output
    }

    private fun applyAdjustments(source: Bitmap, settings: EditSettings): Bitmap {
        val hasAdjustments = settings.brightness != 0f || settings.warmth != 0f ||
            settings.exposure != 0f || settings.contrast != 0f || settings.saturation != 0f ||
            settings.highlights != 0f || settings.shadows != 0f || settings.fade != 0f || settings.tint != 0f ||
            settings.sharpen != 0f || settings.vignette != 0f
            || !settings.hsl.isZero()
        if (!hasAdjustments) return source
        val width = source.width; val height = source.height
        val bandRows = minOf(64, height)
        val output = IntArray(width * bandRows)
        val adjusted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val exposure = Math.pow(2.0, settings.exposure.coerceIn(-2f, 2f).toDouble()).toFloat()
        val contrast = 1f + settings.contrast.coerceIn(-1f, 1f)
        val saturation = 1f + settings.saturation.coerceIn(-1f, 1f)
        val brightness = settings.brightness.coerceIn(-1f, 1f) * 45f
        val warmth = settings.warmth.coerceIn(-1f, 1f) * 30f
        val tint = settings.tint.coerceIn(-1f, 1f) * 30f
        val fade = settings.fade.coerceIn(0f, 1f)
        val hsl = if (settings.hsl.isZero()) settings.hsl else settings.hsl.bounded()
        val hslActive = !hsl.isZero()
        for (top in 0 until height step bandRows) {
        val rows = minOf(bandRows, height - top)
        source.getPixels(output, 0, width, 0, top, width, rows)
        for (row in 0 until rows) for (x in 0 until width) {
            val y = top + row
            val i = row * width + x; val p = output[i]; val alpha = p ushr 24
            var r = ((p shr 16) and 255).toFloat(); var g = ((p shr 8) and 255).toFloat(); var b = (p and 255).toFloat()
            r *= exposure; g *= exposure; b *= exposure
            val lum = (r * .299f + g * .587f + b * .114f)
            val shadow = (1f - (lum / 255f)).coerceIn(0f, 1f) * settings.shadows.coerceIn(-1f, 1f) * 45f
            val highlight = (lum / 255f).coerceIn(0f, 1f) * settings.highlights.coerceIn(-1f, 1f) * 45f
            r += shadow + highlight; g += shadow + highlight; b += shadow + highlight
            val newLum = r * .299f + g * .587f + b * .114f
            r = (newLum + (r - newLum) * saturation) * contrast + (128f * (1f - contrast)) + brightness + warmth + tint
            g = (newLum + (g - newLum) * saturation) * contrast + (128f * (1f - contrast)) + brightness - tint
            b = (newLum + (b - newLum) * saturation) * contrast + (128f * (1f - contrast)) + brightness - warmth + tint
            // Fade lifts blacks and compresses highlights while retaining mid-tone contrast.
            val fadedScale = 1f - (62f / 255f) * fade
            r = r * fadedScale + 32f * fade
            g = g * fadedScale + 32f * fade
            b = b * fadedScale + 32f * fade
            if (settings.vignette > 0f) {
                val dx = (x - width / 2f) / max(1f, width / 2f); val dy = (y - height / 2f) / max(1f, height / 2f)
                val factor = 1f - settings.vignette.coerceIn(0f, 1f) * (dx * dx + dy * dy).coerceIn(0f, 1f) * .75f
                r *= factor; g *= factor; b *= factor
            }
            if (hslActive) {
                // HSL mapping: each slider is -1..1; hue moves at most 1/8 turn (45°),
                // saturation at most 0.5, and lightness at most 0.35. Actual color anchors
                // are R0/O30/Y60/G120/C180/B240/P270/M300; adjacent anchors use smoothstep.
                r = r.coerceIn(0f, 255f); g = g.coerceIn(0f, 255f); b = b.coerceIn(0f, 255f)
                val maxRgb = maxOf(r, g, b); val minRgb = minOf(r, g, b); val chroma = maxRgb - minRgb
                if (chroma > .0001f) {
                    var h: Float
                    val rn = r / 255f; val gn = g / 255f; val bn = b / 255f
                    val maxN = maxRgb / 255f; val minN = minRgb / 255f; val chromaN = chroma / 255f
                    val l = (maxN + minN) * .5f
                    val s = chromaN / (1f - abs(2f * l - 1f)).coerceAtLeast(.0001f)
                    h = when (maxRgb) {
                        r -> ((gn - bn) / chromaN).let { it / 6f + if (it < 0f) 1f else 0f }
                        g -> ((bn - rn) / chromaN + 2f) / 6f
                        else -> ((rn - gn) / chromaN + 4f) / 6f
                    }
                    // Actual anchors: R0, O30, Y60, G120, C180, B240, P270, M300 degrees.
                    var wa: Float; var wb: Float; var ca: HslChannel; var cb: HslChannel
                    var t: Float
                    when {
                        h < 1f / 12f -> { ca=hsl.red; cb=hsl.orange; t=h/(1f/12f) }
                        h < 1f / 6f -> { ca=hsl.orange; cb=hsl.yellow; t=(h-1f/12f)/(1f/12f) }
                        h < 1f / 3f -> { ca=hsl.yellow; cb=hsl.green; t=(h-1f/6f)/(1f/6f) }
                        h < .5f -> { ca=hsl.green; cb=hsl.cyan; t=(h-1f/3f)/(1f/6f) }
                        h < 2f/3f -> { ca=hsl.cyan; cb=hsl.blue; t=(h-.5f)/(1f/6f) }
                        h < .75f -> { ca=hsl.blue; cb=hsl.purple; t=(h-2f/3f)/(1f/12f) }
                        h < 5f/6f -> { ca=hsl.purple; cb=hsl.magenta; t=(h-.75f)/(1f/12f) }
                        else -> { ca=hsl.magenta; cb=hsl.red; t=(h-5f/6f)/(1f/6f) }
                    }
                    t = t.coerceIn(0f, 1f); wb=t*t*(3f-2f*t); wa=1f-wb
                    val hueEdit = wa*ca.hue + wb*cb.hue
                    val satEdit = wa*ca.saturation + wb*cb.saturation
                    val lightEdit = wa*ca.lightness + wb*cb.lightness
                    val neutral = ((s - .03f) / .09f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                    if (hueEdit != 0f || satEdit != 0f || lightEdit != 0f) {
                    val editedHue = (h + hueEdit * .125f * neutral + 1f) % 1f
                    val editedS = (s + satEdit * .5f * neutral).coerceIn(0f, 1f)
                    val editedL = (l + lightEdit * .35f * neutral).coerceIn(0f, 1f)
                    val chroma2 = (1f - abs(2f * editedL - 1f)) * editedS
                    val x = chroma2 * (1f - abs((editedHue * 6f) % 2f - 1f))
                    val m = editedL - chroma2 * .5f
                    val sector = (editedHue * 6f).toInt()
                    var rr = 0f; var gg = 0f; var bb = 0f
                    when (sector) {
                        0 -> { rr = chroma2; gg = x }; 1 -> { rr = x; gg = chroma2 }; 2 -> { gg = chroma2; bb = x }
                        3 -> { gg = x; bb = chroma2 }; 4 -> { rr = x; bb = chroma2 }; else -> { rr = chroma2; bb = x }
                    }
                    r = (rr + m) * 255f; g = (gg + m) * 255f; b = (bb + m) * 255f
                    }
                }
            }
            output[i] = (alpha shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
        adjusted.setPixels(output, 0, width, 0, top, width, rows)
        }
        if (settings.sharpen <= 0f) return adjusted
        val sharpened = sharpen(adjusted, settings.sharpen.coerceIn(0f, 1f))
        adjusted.recycle()
        return sharpened
    }

    private fun sharpen(source: Bitmap, amount: Float): Bitmap {
        val width=source.width; val height=source.height
        val result=source.copy(Bitmap.Config.ARGB_8888, true)
        val input=IntArray(width * 3)
        val output=IntArray(width)
        for (y in 1 until height - 1) {
        source.getPixels(input,0,width,0,y-1,width,3)
        input.copyInto(output,0,width,width*2)
        for (x in 1 until width - 1) {
            val i = width + x; val p = input[i]
            fun channel(shift: Int): Int {
                val center = (p shr shift) and 255
                val avg = (((input[i - 1] shr shift) and 255) + ((input[i + 1] shr shift) and 255) +
                    ((input[i - width] shr shift) and 255) + ((input[i + width] shr shift) and 255)) / 4
                return (center + (center - avg) * amount).roundToInt().coerceIn(0, 255)
            }
            output[x] = (p and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        result.setPixels(output,0,width,0,y,width,1)
        }
        return result
    }

    fun save(context: Context, bitmap: Bitmap, share: Boolean, lossless: Boolean = false,
        format: ExportFormat = if (lossless) ExportFormat.PNG else ExportFormat.JPEG): Uri {
        val rasterFormat = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val name = "Pianyu_${System.currentTimeMillis()}_${System.nanoTime()}.${format.suffix}"
        val temp = if (format == ExportFormat.HEIF) File.createTempFile("Pianyu_", ".heic", context.cacheDir) else null
        fun encodeTo(output: java.io.OutputStream) {
            if (format == ExportFormat.HEIF) error("HEIF requires a file target")
            check(bitmap.compress(rasterFormat, 100, output)) { "照片写入失败" }
        }
        try { if (temp != null) HeifExport.encode(bitmap, temp) }
        catch (error: Throwable) { temp?.delete(); throw error }
        if (share) {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            // Only our own disposable exports are removed; imported originals are untouched.
            directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                ?.forEach { it.delete() }
            val file = File(directory, name)
            try {
                if (temp != null) temp.inputStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                else file.outputStream().use(::encodeTo)
                return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            } catch (error: Throwable) { file.delete(); throw error } finally { temp?.delete() }
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/片屿")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        try {
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { output ->
                    if (temp != null) temp.inputStream().use { it.copyTo(output) } else encodeTo(output)
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                return uri
            } catch (e: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } finally { temp?.delete() }
    }
}
