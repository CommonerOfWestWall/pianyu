package studio.pianyu.app

data class CreativeRecipe(val id: String, val name: String, val description: String, val settings: EditSettings)

val CreativeRecipes = listOf(
    CreativeRecipe("teal-orange-night", "青橙夜色", "冷暖灯光更分明，日光人像可能偏黄", EditSettings(
        lookIndex = 2, strength = .75f, grain = 0f, filmExposure = -.25f,
        autoFilmExposure = true, exposure = -.03f, warmth = .06f, tint = -.04f,
        contrast = .06f, saturation = .08f, highlights = -.08f, shadows = .08f,
        hsl = HslSettings(orange = HslChannel(.02f, .08f, .02f), cyan = HslChannel(-.02f, .08f, -.02f),
            blue = HslChannel(-.02f, .04f, -.02f)), lookVersion = 3)),
    CreativeRecipe("hard-silver", "硬调银盐", "更深黑位与更强明暗，适合街头和光影", EditSettings(
        lookIndex = 6, strength = .75f, grain = 0f, filmExposure = 0f,
        autoFilmExposure = true, exposure = .06f, contrast = .24f,
        highlights = -.04f, shadows = -.12f, fade = 0f, lookVersion = 3)),
    CreativeRecipe("autumn-cyan", "秋日青影", "青蓝天空与暖色主体分离，适合秋日街景与旅行", EditSettings(
        lookIndex = 1, strength = .75f, grain = 0f, filmExposure = .15f,
        autoFilmExposure = true, exposure = -.04f, warmth = .025f, tint = -.015f,
        contrast = -.03f, saturation = -.05f, highlights = -.14f, shadows = -.02f,
        fade = .025f, hsl = HslSettings(
            orange = HslChannel(-.02f, -.08f, .02f), yellow = HslChannel(-.06f, -.1f, 0f),
            green = HslChannel(.2f, -.2f, -.02f), cyan = HslChannel(-.3f, -.08f, .02f),
            blue = HslChannel(-.8f, -.12f, .02f)), lookVersion = 3))
)
