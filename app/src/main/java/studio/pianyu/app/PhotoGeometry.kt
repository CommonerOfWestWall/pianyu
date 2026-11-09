package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun normalized(): CropRect {
        val epsilon = 0.000001f
        val l = left.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val t = top.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val r = right.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        val b = bottom.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
        var nl = min(l, r); var nr = max(l, r)
        if (nr - nl < epsilon) {
            if (nl >= 1f - epsilon) nl = 1f - epsilon else nr = nl + epsilon
        }
        var nt = min(t, b); var nb = max(t, b)
        if (nb - nt < epsilon) {
            if (nt >= 1f - epsilon) nt = 1f - epsilon else nb = nt + epsilon
        }
        return CropRect(nl, nt, nr, nb)
    }
}

object PhotoGeometry {
    fun orient(source: Bitmap, settings: EditSettings): Bitmap {
        var result = source
        val turns = ((settings.quarterTurns % 4) + 4) % 4
        if (turns != 0) {
            val matrix = Matrix().apply { postRotate(turns * 90f) }
            val previous = result
            result = Bitmap.createBitmap(previous, 0, 0, previous.width, previous.height, matrix, true)
            if (previous !== source) previous.recycle()
        }
        if (settings.flipHorizontal || settings.flipVertical) {
            val matrix = Matrix().apply {
                postScale(if (settings.flipHorizontal) -1f else 1f,
                    if (settings.flipVertical) -1f else 1f)
            }
            val previous = result
            result = Bitmap.createBitmap(previous, 0, 0, previous.width, previous.height, matrix, true)
            if (previous !== source) previous.recycle()
        }
        val degrees = settings.straightenDegrees.coerceIn(-20f, 20f)
        if (abs(degrees) > 0.0001f) {
            val previous = result
            result = straighten(previous, degrees)
            if (previous !== source) previous.recycle()
        }
        return result
    }

    private fun straighten(source: Bitmap, degrees: Float): Bitmap {
        val radians = Math.toRadians(abs(degrees).toDouble())
        val sinA = sin(radians)
        val cosA = cos(radians)
        val scale = max(cosA + source.height * sinA / source.width,
            cosA + source.width * sinA / source.height).toFloat()
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(android.graphics.Color.TRANSPARENT)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val cx = source.width / 2f
            val cy = source.height / 2f
            translate(output.width / 2f, output.height / 2f)
            scale(scale, scale)
            rotate(degrees)
            drawBitmap(source, -cx, -cy, paint)
        }
        return output
    }

    fun crop(source: Bitmap, rect: CropRect): Bitmap {
        val r = rect.normalized()
        val left = (r.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val top = (r.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val right = (r.right * source.width).roundToInt().coerceIn(left + 1, source.width)
        val bottom = (r.bottom * source.height).roundToInt().coerceIn(top + 1, source.height)
        if (left == 0 && top == 0 && right == source.width && bottom == source.height) return source
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    fun apply(source: Bitmap, settings: EditSettings): Bitmap {
        val oriented = orient(source, settings)
        var rect = settings.crop.normalized()
        if (settings.cropAspect > 0f && rect == CropRect()) {
            rect = fitAspect(oriented.width, oriented.height, settings.cropAspect)
        }
        val result = crop(oriented, rect)
        if (oriented !== source && oriented !== result) oriented.recycle()
        return result
    }

    fun fitAspect(width: Int, height: Int, aspect: Float, around: CropRect = CropRect()): CropRect {
        val base = around.normalized()
        if (aspect <= 0f || width <= 0 || height <= 0) return base
        val target = aspect * height / width.toFloat()
        var w = base.right - base.left
        var h = base.bottom - base.top
        if (w / h > target) w = h * target else h = w / target
        w = w.coerceAtMost(1f)
        h = h.coerceAtMost(1f)
        val cx = (base.left + base.right) / 2f
        val cy = (base.top + base.bottom) / 2f
        val left = (cx - w / 2f).coerceIn(0f, 1f - w)
        val top = (cy - h / 2f).coerceIn(0f, 1f - h)
        return CropRect(left, top, left + w, top + h).normalized()
    }
}
