package studio.pianyu.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeometryDeviceTest {
    @Test
    fun quarterTurnsAndMirrorsPreserveExpectedPixels() {
        val source = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            source.setPixel(x, y, Color.rgb(x * 50, y * 70, 20))
        }
        var rotated = source
        val once = PhotoGeometry.orient(source, EditSettings(quarterTurns = 1))
        assertEquals(3, once.width); assertEquals(4, once.height)
        assertEquals(source.getPixel(0, 0), once.getPixel(2, 0))
        assertEquals(source.getPixel(3, 2), once.getPixel(0, 3))
        repeat(4) { rotated = PhotoGeometry.orient(rotated, EditSettings(quarterTurns = 1)) }
        for (y in 0 until source.height) for (x in 0 until source.width) assertEquals(source.getPixel(x, y), rotated.getPixel(x, y))
        val mirrored = PhotoGeometry.orient(source, EditSettings(flipHorizontal = true))
        assertEquals(source.getPixel(0, 0), mirrored.getPixel(source.width - 1, 0))
        assertEquals(source.getPixel(0, source.height - 1), mirrored.getPixel(source.width - 1, source.height - 1))
    }

    @Test
    fun cropAndFitAspectUseNormalizedCoordinates() {
        val source = Bitmap.createBitmap(10, 8, Bitmap.Config.ARGB_8888)
        val cropped = PhotoGeometry.crop(source, CropRect(.2f, .25f, .8f, .75f))
        assertEquals(6, cropped.width); assertEquals(4, cropped.height)
        val fit = PhotoGeometry.fitAspect(10, 8, 1f, CropRect(.1f, .1f, .9f, .9f))
        assertEquals(1f, (fit.right - fit.left) * 10f / ((fit.bottom - fit.top) * 8f), .0001f)
    }

    @Test
    fun straighteningDifferentAspectHasNoTransparentCorners() {
        for ((width, height) in listOf(17 to 9, 9 to 17)) {
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(40, 80, 120)) }
        for (degrees in listOf(-20f, 20f)) {
            val result = PhotoGeometry.orient(source, EditSettings(straightenDegrees = degrees))
            assertEquals(source.width, result.width); assertEquals(source.height, result.height)
            for (y in 0 until result.height) for (x in 0 until result.width) assertEquals(255, result.getPixel(x, y) ushr 24)
        }
        }
    }

    @Test
    fun freeCropDragAndLockedAspectStayInsideImage() {
        val initial = CropRect(.2f, .2f, .8f, .8f)
        val moved = dragCrop(initial, 4, 4f, -4f, 0f)
        assertEquals(.6f, moved.right - moved.left, .0001f)
        assertEquals(1f, moved.right, .0001f); assertEquals(0f, moved.top, .0001f)
        val free = dragCrop(initial, 0, .1f, .05f, 0f)
        assertEquals(.3f, free.left, .0001f); assertEquals(.25f, free.top, .0001f)
        for (handle in 0..3) for (offset in listOf(-2f, -.15f, .15f, 2f)) {
            val r = dragCrop(initial, handle, offset, offset, .75f)
            assertEquals(.75f, (r.right-r.left)/(r.bottom-r.top), .0001f)
            assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f)
        }
        val settings = EditSettings(quarterTurns=1, straightenDegrees=12f,
            crop=CropRect(.1f,.2f,.9f,.8f))
        val preview = PhotoGeometry.apply(Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888),settings)
        val export = PhotoGeometry.apply(Bitmap.createBitmap(1600,1200,Bitmap.Config.ARGB_8888),settings)
        assertEquals(preview.width*4,export.width); assertEquals(preview.height*4,export.height)
    }

    @Test
    fun adjustmentsHaveExactDefaultAndVisibleEndpoints() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(80, 120, 160)) }
        val filtered = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val defaults = PhotoIO.finish(source, filtered, EditSettings(strength = 0f))
        val pixels = IntArray(source.width * source.height); val defaultPixels = IntArray(pixels.size)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        defaults.getPixels(defaultPixels, 0, source.width, 0, 0, source.width, source.height)
        assertTrue(pixels.contentEquals(defaultPixels))
        val adjusted = PhotoIO.finish(source, source, EditSettings(strength = 1f, exposure = -1f, saturation = .5f, vignette = 1f))
        assertTrue(adjusted.getPixel(4, 4) != source.getPixel(4, 4))
        assertTrue((adjusted.getPixel(0, 0) and 255) < (adjusted.getPixel(4, 4) and 255))
        assertEquals(8, adjusted.width); assertEquals(8, adjusted.height)

        val exposureAndSharpen = PhotoIO.finish(source, source, EditSettings(strength = 1f, exposure = 1f, sharpen = 1f))
        val exposedRed = (exposureAndSharpen.getPixel(4, 4) shr 16) and 255
        val sourceRed = (source.getPixel(4, 4) shr 16) and 255
        assertTrue("sharpen must retain exposure", exposedRed > sourceRed)
        val fadeSource = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        for (y in 0 until 8) for (x in 0 until 8) fadeSource.setPixel(x, y, Color.rgb(x * 30, x * 30, x * 30))
        val faded = PhotoIO.finish(fadeSource, fadeSource, EditSettings(strength = 1f, fade = 1f))
        val fadedDark = faded.getPixel(0, 0) and 255
        val fadedLight = faded.getPixel(7, 7) and 255
        assertTrue("fade must retain contrast", fadedDark < fadedLight && fadedLight < 255)
        val dark = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val light = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200,200,200)) }
        val half = PhotoIO.finish(dark, light, EditSettings(strength=.5f))
        assertTrue((half.getPixel(1,1) and 255) in 99..101)
        assertEquals(255, half.getPixel(1,1) ushr 24)
    }
}
