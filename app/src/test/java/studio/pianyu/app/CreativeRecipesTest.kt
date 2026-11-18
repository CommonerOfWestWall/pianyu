package studio.pianyu.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeRecipesTest {
    @Test fun builtinsHaveDistinctIdsAndLegalBaseLooks() {
        assertTrue(CreativeRecipes.isNotEmpty())
        assertEquals(CreativeRecipes.size, CreativeRecipes.map { it.id }.toSet().size)
        assertTrue(CreativeRecipes.all { it.settings.lookIndex in Looks.indices })
        assertNotEquals(CreativeRecipes[0].settings.lookIndex, CreativeRecipes[1].settings.lookIndex)
    }

    @Test fun recipeReplacementKeepsGeometryAndReplacesColourState() {
        val geometry = EditSettings(cropAspect = 1.5f, crop = CropRect(0.1f, 0.2f, 0.8f, 0.9f),
            quarterTurns = 1, straightenDegrees = 2f, flipHorizontal = true)
        val result = geometry.withColorRecipe(CreativeRecipes.first().settings)
        assertEquals(geometry.crop, result.crop)
        assertEquals(geometry.cropAspect, result.cropAspect, 0f)
        assertEquals(geometry.quarterTurns, result.quarterTurns)
        assertEquals(geometry.straightenDegrees, result.straightenDegrees, 0f)
        assertEquals(geometry.flipHorizontal, result.flipHorizontal)
        assertEquals(CreativeRecipes.first().settings.lookIndex, result.lookIndex)
        assertEquals(CreativeRecipes.first().settings.hsl, result.hsl)
    }
}
