package studio.pianyu.app

import org.junit.Assert.*
import org.junit.Test

class ReferenceStylesTest {
    @Test fun existingIndicesMaterialsAndStrengthsRemainCompatible() {
        val original = listOf(
            Look("daily", "日常暖色", "kodak_portra_400", "kodak_portra_endura", defaultStrength=.72f),
            Look("cinema", "电影日光", "kodak_vision3_250d", "kodak_2383", defaultStrength=.82f),
            Look("night", "夜色光晕", "cinestill_800t", "kodak_2383", halo=true, defaultStrength=.8f),
            Look("natural", "自然正片", "fujifilm_provia_100f", "kodak_portra_endura", positive=true, defaultStrength=.9f),
            Look("landscape", "浓彩风光", "fujifilm_velvia_100", "kodak_portra_endura", positive=true, defaultStrength=.9f),
            Look("retro", "红蓝复古", "kodak_kodachrome_64", "kodak_portra_endura", positive=true, defaultStrength=1f),
            Look("softmono", "柔和黑白", "fujifilm_neopan_acros_100_ii", "ilford_multigrade_iv_rc", defaultStrength=1f))
        assertEquals(original, Looks.take(7))
        assertEquals(
            listOf("mist-coast", "soft-daylight", "olive-story", "cyan-travel", "golden-landscape",
                "blue-hour", "forest-deep", "muted-teal-coast"),
            Looks.subList(7, 15).map { it.id })
        assertTrue(Looks.subList(7, 15).all { it.creative })
        assertEquals(Looks.size, Looks.map { it.id }.toSet().size)
        assertEquals(Looks.size, Looks.map { it.name }.toSet().size)
    }

    @Test fun addedPalettesHaveDistinctFinishesAndReuseExistingMaterials() {
        val finishes = Looks.subList(7, 15).map { look ->
            val base = Looks.take(7).firstOrNull {
                it.film == look.film && it.paper == look.paper && it.positive == look.positive && it.halo == look.halo
            }
            assertNotNull("${look.id} must use an available material", base)
            val finish = look.finishSettings(EditSettings())
            assertNotEquals("${look.id} must add a colour treatment", EditSettings(), finish)
            assertNotEquals("${look.id} duplicates its material default", base!!.finishSettings(EditSettings()), finish)
            assertEquals(base.defaultStrength, look.defaultStrength, 0f)
            assertTrue(finish.exposure.isFinite() && finish.contrast in -1f..1f && finish.saturation in -1f..1f)
            assertEquals(finish.hsl, finish.hsl.bounded())
            finish
        }
        assertEquals(finishes.size, finishes.toSet().size)
    }

    @Test fun trialCFinishesAreExactAtDefaultStrength() {
        val expected = listOf(
            12 to EditSettings(lookIndex = 12, exposure = .08f, contrast = .045f, saturation = .055f,
                warmth = -.015f, tint = .012f, highlights = -.02f, shadows = .03f, fade = .01f,
                hsl = HslSettings(yellow = HslChannel(0f, -.06f, 0f), green = HslChannel(-.1f, -.18f, -.05f),
                    cyan = HslChannel(.15f, .15f, -.025f), blue = HslChannel(.1f, .28f, -.1f),
                    purple = HslChannel(0f, .02f, -.015f))),
            13 to EditSettings(lookIndex = 13, exposure = .01f, contrast = .015f, saturation = -.06f,
                highlights = -.03f, shadows = .09f, fade = .005f,
                hsl = HslSettings(red = HslChannel(0f, .03f, -.03f), orange = HslChannel(0f, .03f, -.03f),
                    yellow = HslChannel(.25f, -.15f, -.05f), green = HslChannel(.1f, -.07f, -.06f),
                    cyan = HslChannel(0f, -.2f, -.05f), blue = HslChannel(0f, -.1f, -.05f))))
        for ((index, expectedFinish) in expected) {
            val look = Looks[index]
            assertEquals(expectedFinish, look.finishSettings(EditSettings(lookIndex = index)))
        }
    }

    @Test fun newReferenceFinishesScaleDisableAndKeepUserEdits() {
        for (index in 12..14) {
            val look = Looks[index]
            val atDefault = look.finishSettings(EditSettings(lookIndex = index, strength = .75f))
            val half = look.finishSettings(EditSettings(lookIndex = index, strength = .375f))
            assertEquals(atDefault.exposure / 2f, half.exposure, 1e-6f)
            assertEquals(atDefault.hsl.blue.hue / 2f, half.hsl.blue.hue, 1e-6f)

            val zero = EditSettings(lookIndex = index, strength = 0f)
            assertEquals(zero, look.finishSettings(zero))

            val user = EditSettings(lookIndex = index, strength = .375f, exposure = .17f,
                contrast = -.12f, hsl = HslSettings(blue = HslChannel(.03f, -.02f, .01f)))
            val applied = look.finishSettings(user)
            assertEquals(user.strength, applied.strength, 0f)
            assertEquals(user.exposure + atDefault.exposure / 2f, applied.exposure, 1e-6f)
            assertEquals(user.contrast + atDefault.contrast / 2f, applied.contrast, 1e-6f)
            assertEquals(user.hsl.blue.hue + atDefault.hsl.blue.hue / 2f, applied.hsl.blue.hue, 1e-6f)
            val disabledUser = EditSettings(lookIndex = index, strength = 0f, exposure = .17f,
                hsl = HslSettings(blue = HslChannel(.03f, -.02f, .01f)))
            assertEquals(disabledUser, look.finishSettings(disabledUser))
            assertEquals((look.defaultStrength / .75f).coerceAtMost(1f), look.filmStrength(EditSettings(lookIndex = index, strength = 1f)), 1e-6f)
            if (index == 14) {
                for (strength in listOf(0f, .375f, .75f, 1f)) {
                    val full = look.finishSettings(EditSettings(lookIndex = index, strength = strength))
                    val channels = listOf(full.hsl.red, full.hsl.orange, full.hsl.yellow, full.hsl.green,
                        full.hsl.cyan, full.hsl.blue, full.hsl.purple, full.hsl.magenta)
                    val anchors = floatArrayOf(0f, 1f / 12f, 1f / 6f, 1f / 3f, .5f, 2f / 3f, .75f, 5f / 6f, 1f)
                    val hues = channels.map { it.hue } + channels.first().hue
                    for (i in 0 until 8) {
                        val derivative = 1f + 1.5f * .125f * (hues[i + 1] - hues[i]) / (anchors[i + 1] - anchors[i])
                        assertTrue("$index strength $strength HSL segment $i reversed: $derivative", derivative >= .1f - 1e-6f)
                    }
                }
            }
        }
    }

    @Test fun mutedTealCoastUsesExactAcceptedFinishAndDailyMaterial() {
        val look = Looks[14]
        assertEquals("muted-teal-coast", look.id)
        assertEquals("灰青低彩", look.name)
        assertEquals(Look("muted-teal-coast", "灰青低彩", "kodak_portra_400", "kodak_portra_endura",
            positive = false, defaultStrength = .72f, creative = true), look)
        val finish = look.finishSettings(EditSettings(lookIndex = 14))
        assertEquals(-0.06225873841435463f, finish.exposure, 0f)
        assertEquals(-0.15497991089409832f, finish.contrast, 0f)
        assertEquals(-0.10471789032605339f, finish.saturation, 0f)
        assertEquals(-0.02543916418167603f, finish.warmth, 0f)
        assertEquals(-0.018953381319075202f, finish.tint, 0f)
        assertEquals(-0.017474818365434457f, finish.highlights, 0f)
        assertEquals(0.09086344088176529f, finish.shadows, 0f)
        assertEquals(0.00029414861979658096f, finish.fade, 0f)
        assertEquals(HslChannel(-0.28453889193055176f, -0.2825971585240644f, 0.11997563468630096f), finish.hsl.cyan)
        assertEquals(HslChannel(-0.3155989179608355f, -0.24513076186008043f, 0.11988013933202213f), finish.hsl.blue)
    }

    @Test fun paletteLooksAppendWithoutChangingPersistedIndices() {
        assertEquals(listOf("daily", "cinema", "night", "natural", "landscape", "retro", "softmono",
            "mist-coast", "soft-daylight", "olive-story", "cyan-travel", "golden-landscape", "blue-hour",
            "forest-deep", "muted-teal-coast"), Looks.take(15).map { it.id })
        assertEquals(9, Looks.drop(15).size)
        assertEquals(listOf("浅绿柔光", "暖粉淡彩", "金色日照", "冷灰薄雾", "深秋暖雾"),
            SeasonalPalettes.map { it.look.name })
        assertEquals(listOf("青蓝亮调", "电影暖亮", "电影冷影", "电影暖影"),
            CinemaPalettes.map { it.look.name })
        assertEquals(listOf("spring-cool", "spring-warm", "summer-warm", "autumn-cool", "autumn-warm",
            "cinema-tungsten-cool-bright", "cinema-daylight-warm-bright", "cinema-daylight-cool-shadow",
            "cinema-tungsten-warm-shadow"), Looks.drop(15).map { it.id })
        assertEquals(
            listOf("cinema", "daily", "soft-daylight", "night", "retro", "golden-landscape",
                "blue-hour", "muted-teal-coast", "mist-coast", "cyan-travel", "natural",
                "olive-story", "forest-deep", "landscape", "softmono", "spring-cool", "spring-warm",
                "summer-warm", "autumn-cool", "autumn-warm", "cinema-tungsten-cool-bright",
                "cinema-daylight-warm-bright", "cinema-daylight-cool-shadow", "cinema-tungsten-warm-shadow")
                .filterNot { it in LookVariants },
            LookCards.map { it.value.id })
    }

    @Test fun newPalettesHaveDistinctBoundedFinishesAndCinemaPairAxes() {
        val finishes = Looks.drop(15).map { look ->
            val finish = look.finishSettings(EditSettings())
            assertTrue(finish.exposure.isFinite())
            assertTrue(finish.contrast in -1f..1f && finish.saturation in -1f..1f)
            assertEquals(finish.hsl, finish.hsl.bounded())
            finish
        }
        assertEquals(finishes.size, finishes.toSet().size)
        assertTrue(CinemaPalettes.all { !it.look.halo && it.look.paper == "kodak_2383" })
        assertTrue(CinemaPalettes[0].look.film == "kodak_vision3_500t")
        assertTrue(CinemaPalettes[1].look.film == "kodak_vision3_250d")
        assertTrue(CinemaPalettes[2].look.film == "kodak_vision3_250d")
        assertTrue(CinemaPalettes[3].look.film == "kodak_vision3_500t")
        assertNotEquals(CinemaPalettes[1].finish.warmth, CinemaPalettes[2].finish.warmth)
        assertNotEquals(CinemaPalettes[1].finish.exposure, CinemaPalettes[2].finish.exposure)
        assertNotEquals(CinemaPalettes[2].finish.shadows, CinemaPalettes[3].finish.shadows)
    }

    @Test fun everyLookHasCompleteGuideAndExistingIndex() {
        val covered = LookCards.map { it.index } + VariantRecipes.map { it.settings.lookIndex }
        assertEquals(Looks.indices.toList(), covered.sorted())
        LookCards.forEach { card -> assertSame(Looks[card.index], card.value) }
        Looks.forEach { look ->
            val guide = look.guidance
            assertTrue(guide.source.isNotBlank())
            assertTrue(guide.character.isNotBlank())
            assertTrue(guide.scenes.isNotBlank())
            assertTrue(guide.tags.isNotEmpty())
        }
    }

    @Test fun variantsKeepTheirOriginalGradeAndHaveAnIndependentParent() {
        assertTrue(VariantRecipes.isNotEmpty())
        val distinctRecipes = CreativeRecipes + VariantRecipes
        assertEquals(distinctRecipes.size, distinctRecipes.map { it.id }.toSet().size)
        for (recipe in VariantRecipes) {
            val index = recipe.settings.lookIndex
            val variant = Looks[index]
            val parent = variant.primaryStyle()
            assertEquals(EditSettings(lookIndex = index), recipe.settings)
            assertEquals(variant.name, recipe.name)
            assertTrue(parent.id in LookCards.map { it.value.id })
            assertTrue(parent.browseTags().containsAll(variant.guidance.tags))
        }
    }

    @Test fun lookSortsUseEarliestMatchingTagAndStableTies() {
        val original = LookCards
        for (sort in LookSort.entries) {
            val sorted = sortedLookCards(sort)
            assertEquals(original.size, sorted.size)
            assertEquals(original.map { it.index }.toSet(), sorted.map { it.index }.toSet())
            val priority = if (sort in setOf(LookSort.SPRING, LookSort.SUMMER, LookSort.AUTUMN, LookSort.WINTER)) {
                val seasons = listOf(LookSort.SPRING, LookSort.SUMMER, LookSort.AUTUMN, LookSort.WINTER)
                val start = seasons.indexOf(sort)
                seasons.drop(start) + seasons.take(start)
            } else listOf(sort)
            assertTrue(sorted.first().value.browseTags().contains(sort))
            val ranked = sorted.groupBy { card ->
                val firstMatch = priority.indexOfFirst { it in card.value.browseTags() }
                if (firstMatch < 0) priority.size else firstMatch
            }
            assertEquals(ranked.keys.sorted(), ranked.keys.toList())
            for (tied in ranked.values) {
                assertEquals(original.filter { it in tied }, tied)
            }
            sorted.forEach { card -> assertSame(Looks[card.index], card.value) }
        }
    }

    @Test fun defaultLookSortPreservesCardIdentityAndOrder() {
        assertEquals(LookCards.map { it.index }, sortedLookCards(null).map { it.index })
        LookCards.indices.forEach { assertSame(LookCards[it], sortedLookCards(null)[it]) }
    }
}
