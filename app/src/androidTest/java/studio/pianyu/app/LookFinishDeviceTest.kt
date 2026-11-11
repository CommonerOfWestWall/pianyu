package studio.pianyu.app

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LookFinishDeviceTest {
    @Test fun savedRecipesRetainTheirFinishingVersion() {
        val current = EditSettings(lookIndex = 5, exposure = .23f,
            crop = CropRect(.1f, .1f, .8f, .8f), straightenDegrees = 3f)
        assertEquals(current, settingsFromJson(settingsToJson(current)))
        val oldJson = settingsToJson(current).apply { remove("lookVersion") }
        val old = settingsFromJson(oldJson)
        assertEquals(0, old.lookVersion)
        assertEquals(old, Looks[5].finishSettings(old))
        assertEquals(0, old.colorRecipe().lookVersion)
        assertEquals(current.crop, current.withColorRecipe(old).crop)
        assertEquals(0, current.withColorRecipe(old).lookVersion)
        assertEquals(3, settingsFromJson(null).lookVersion)
        assertEquals(0, settingsFromJson(JSONObject()).lookVersion)
        assertEquals(.125f, settingsFromJson(null).grain, .000001f)
        assertEquals(0f, settingsFromJson(JSONObject()).grain, .000001f)
        val legacy = current.copy(lookVersion = 1, strength = .72f, grain = 0f)
        assertEquals(legacy, settingsFromJson(settingsToJson(legacy)))
    }

    @Test fun zeroStrengthKeepsTheOriginalForEveryLook() {
        val original = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff6688aa.toInt()) }
        val filtered = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xffaa4411.toInt()) }
        try {
            for (i in Looks.indices) {
                val output = PhotoIO.finish(original, filtered, EditSettings(lookIndex = i, strength = 0f))
                try { assertTrue("strength zero changed look $i", original.sameAs(output)) }
                finally { output.recycle() }
            }
        } finally { original.recycle(); filtered.recycle() }
    }

    @Test fun finishingIsSharedWithExportAndKeepsSlidersAndGeometryRelative() {
        val original = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val filtered = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(48 * 48) { i ->
            val v = i % 240 + 8
            (0xff000000.toInt() or (v shl 16) or ((256 - v) shl 8) or (v / 2))
        }
        original.setPixels(pixels, 0, 48, 0, 0, 48, 48)
        filtered.setPixels(pixels.reversedArray(), 0, 48, 0, 0, 48, 48)
        try {
            for ((i, look) in Looks.withIndex()) {
                val s = EditSettings(lookIndex = i, strength = look.defaultStrength, lookVersion = 1,
                    exposure = .1f, quarterTurns = 1, flipHorizontal = true)
                val effective = look.finishSettings(s)
                assertEquals(s.quarterTurns, effective.quarterTurns)
                assertEquals(s.flipHorizontal, effective.flipHorizontal)
                assertEquals(.1f, s.exposure, .00001f)
                val actual = PhotoIO.finish(original, filtered, s)
                val explicit = PhotoIO.finish(original, filtered, effective.copy(lookVersion = 0))
                try { assertTrue("finish not applied in shared output path: $i", actual.sameAs(explicit)) }
                finally { actual.recycle(); explicit.recycle() }
                val normalized = PhotoIO.finish(original, filtered,
                    s.copy(strength = 1f, lookVersion = 2))
                val legacyOutput = PhotoIO.finish(original, filtered, s)
                try { assertTrue("normalized default changed calibrated pixels: $i", normalized.sameAs(legacyOutput)) }
                finally { normalized.recycle(); legacyOutput.recycle() }
            }
        } finally { original.recycle(); filtered.recycle() }
    }
}
