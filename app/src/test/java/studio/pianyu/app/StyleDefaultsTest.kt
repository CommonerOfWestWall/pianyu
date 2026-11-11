package studio.pianyu.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleDefaultsTest {
    @Test fun seventyFiveMatchesOldRecommendationAndFullStrengthHasRealHeadroom() {
        assertEquals(.75f, EditSettings().strength, 0f)
        assertTrue(EditSettings().autoFilmExposure)
        for ((index, look) in Looks.withIndex()) {
            val old = EditSettings(lookIndex = index, strength = 1f, lookVersion = 2, autoFilmExposure = false)
            val fresh = old.copy(strength = .75f, lookVersion = 3)
            assertEquals(look.filmStrength(old), look.filmStrength(fresh), 1e-6f)
            assertFinishEquivalent(old, fresh)
            val full = fresh.copy(strength = 1f)
            val a = look.finishSettings(fresh); val b = look.finishSettings(full)
            assertTrue("${look.id} has no creative headroom", a.contrast != b.contrast || a.saturation != b.saturation ||
                a.warmth != b.warmth || a.highlights != b.highlights || a.hsl != b.hsl)
            assertTrue(look.filmStrength(full) in look.filmStrength(fresh)..1f)
            assertEquals(.75f, old.displayedStrength, 0f)
            assertEquals(fresh, old.withDisplayedStrength(.75f))
            val zero = fresh.copy(strength = 0f)
            assertEquals(0f, look.filmStrength(zero), 0f)
            assertEquals(zero, look.finishSettings(zero))
        }
    }

    private fun assertFinishEquivalent(old: EditSettings, fresh: EditSettings) {
        val a = Looks[old.lookIndex].finishSettings(old)
        val b = Looks[fresh.lookIndex].finishSettings(fresh)
        assertEquals(a.exposure, b.exposure, 1e-6f)
        assertEquals(a.contrast, b.contrast, 1e-6f)
        assertEquals(a.saturation, b.saturation, 1e-6f)
        assertEquals(a.warmth, b.warmth, 1e-6f)
        assertEquals(a.tint, b.tint, 1e-6f)
        assertEquals(a.highlights, b.highlights, 1e-6f)
        assertEquals(a.shadows, b.shadows, 1e-6f)
        assertEquals(a.fade, b.fade, 1e-6f)
        assertEquals(a.hsl, b.hsl)
    }

    @Test fun normalizedDefaultsKeepAllSevenCalibratedLooksEquivalent() {
        for ((index, look) in Looks.withIndex()) {
            val old = EditSettings(lookIndex = index, strength = look.defaultStrength, lookVersion = 1)
            val fresh = EditSettings(lookIndex = index, strength = 1f, lookVersion = 2)
            assertEquals(look.defaultStrength, look.filmStrength(fresh), 1e-6f)
            assertFinishEquivalent(old, fresh)
        }
    }

    @Test fun normalizedPartialStrengthMatchesLegacyRelativeStrength() {
        for ((index, look) in Looks.withIndex()) {
            val fraction = .37f
            val old = EditSettings(lookIndex = index, strength = look.defaultStrength * fraction, lookVersion = 1)
            val fresh = EditSettings(lookIndex = index, strength = fraction, lookVersion = 2)
            assertEquals(look.filmStrength(old), look.filmStrength(fresh), 1e-6f)
            assertFinishEquivalent(old, fresh)
        }
    }

    @Test fun zeroStrengthAndDisabledFinishDoNotAddPreset() {
        for ((index, look) in Looks.withIndex()) {
            val zero = EditSettings(lookIndex = index, strength = 0f, lookVersion = 2)
            assertEquals(zero, look.finishSettings(zero))
            val disabled = EditSettings(lookIndex = index, strength = 1f, lookVersion = 0)
            assertEquals(disabled, look.finishSettings(disabled))
        }
    }

    @Test fun userStrengthIsPreservedAndLegacyFullStrengthStaysLegacy() {
        for ((index, look) in Looks.withIndex()) {
            val user = EditSettings(lookIndex = index, strength = .41f, exposure = .17f, lookVersion = 2)
            assertEquals(.41f, look.finishSettings(user).strength, 1e-6f)
            if (look.defaultStrength < 1f) {
                val legacy = EditSettings(lookIndex = index, strength = 1f, lookVersion = 1)
                val calibrated = EditSettings(lookIndex = index, strength = look.defaultStrength, lookVersion = 1)
                val a = look.finishSettings(legacy)
                val b = look.finishSettings(calibrated)
                assertTrue(a.exposure != b.exposure || a.contrast != b.contrast || a.saturation != b.saturation ||
                    a.warmth != b.warmth || a.tint != b.tint || a.highlights != b.highlights ||
                    a.shadows != b.shadows || a.fade != b.fade || a.hsl != b.hsl)
            }
        }
    }
}
