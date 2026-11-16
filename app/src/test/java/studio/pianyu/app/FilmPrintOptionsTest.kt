package studio.pianyu.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class FilmPrintOptionsTest {
    @Test fun defaultPrintAndExposurePreserveExistingSevenLooks() {
        val settings = EditSettings()
        assertEquals("", settings.printStock)
        assertEquals(0f, settings.printExposure, 0f)
        for (look in Looks) {
            assertEquals(look.paper, look.effectivePrintStock(settings.printStock))
            assertEquals(1.0, look.printExposureGain(settings.printExposure), 0.0)
        }
    }

    @Test fun compatibleMaterialsCannotLeakBetweenColorCinemaAndMonochrome() {
        val color = Looks[0]; val cinema = Looks[1]; val mono = Looks[6]
        assertEquals("kodak_2393", cinema.effectivePrintStock("kodak_2393"))
        assertEquals("fujifilm_crystal_archive_typeii", color.effectivePrintStock("fujifilm_crystal_archive_typeii"))
        assertEquals(color.paper, color.effectivePrintStock("kodak_2393"))
        assertEquals(cinema.paper, cinema.effectivePrintStock("ilford_multigrade_iv_rc"))
        assertEquals(mono.paper, mono.effectivePrintStock("kodak_supra_endura"))
    }

    @Test fun directSlideScanIgnoresPrintControlsAndNonFiniteExposureIsNeutral() {
        for (look in Looks.filter { it.positive }) {
            assertTrue(look.printStocks().isEmpty())
            assertEquals(look.paper, look.effectivePrintStock("kodak_supra_endura"))
            assertEquals(1.0, look.printExposureGain(2f), 0.0)
        }
        assertEquals(sqrt(2.0), Looks[0].printExposureGain(.5f), 1e-9)
        assertEquals(1.0, Looks[0].printExposureGain(Float.NaN), 0.0)
        assertEquals(4.0, Looks[0].printExposureGain(9f), 0.0)
    }

    @Test fun meteringIncludesDistinctModernCenterAndPreservesLegacyMethod() {
        assertTrue("center" in MeteringMethods)
        assertTrue("center_weighted" in MeteringMethods)
        assertTrue("protect_shadows" in MeteringMethods)
        assertEquals(4, MeteringIntents.size)
    }

    @Test fun copiedColorRecipeIncludesPrintButPreservesDestinationGeometry() {
        val recipe = EditSettings(printStock = "kodak_2393", printExposure = .6f, autoFilmExposureMethod = "center")
        val target = EditSettings(crop = CropRect(.1f, .2f, .8f, .9f), quarterTurns = 1)
        val result = target.withColorRecipe(recipe.colorRecipe())
        assertEquals(recipe.printStock, result.printStock)
        assertEquals(recipe.printExposure, result.printExposure, 0f)
        assertEquals(target.crop, result.crop)
        assertEquals(target.quarterTurns, result.quarterTurns)
    }
}
