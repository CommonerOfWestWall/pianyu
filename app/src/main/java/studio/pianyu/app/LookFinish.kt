package studio.pianyu.app

/** Audited creative finishes, separate from the upstream material profiles. */
private fun Look.finishBase(): EditSettings = when (id) {
    "daily" -> EditSettings(lookVersion = 0, contrast = -0.07f, warmth = 0.045f, saturation = -0.02f, highlights = -0.05f, shadows = 0.06f)
    "cinema" -> EditSettings(lookVersion = 0, contrast = 0.025f, warmth = 0.02f, tint = -0.015f, highlights = -0.08f, shadows = 0.03f, saturation = -0.06f, fade = 0.025f)
    "night" -> EditSettings(lookVersion = 0, contrast = -0.035f, highlights = -0.1f, shadows = 0.08f, saturation = -0.01f, warmth = -0.015f, tint = 0.01f)
    "natural" -> EditSettings(lookVersion = 0, exposure = 0.02f, contrast = -0.015f, saturation = 0.04f, highlights = -0.04f, shadows = 0.05f, hsl = HslSettings(cyan = HslChannel(0.18f, 0.02f, 0f), blue = HslChannel(0.12f, 0.06f, -0.04f)))
    "landscape" -> EditSettings(lookVersion = 0, saturation = 0.01f, highlights = -0.08f, shadows = 0.06f, hsl = HslSettings(red = HslChannel(0f, -0.08f, 0.03f), orange = HslChannel(0f, -0.09f, 0.02f), yellow = HslChannel(0.18f, -0.08f, -0.03f), green = HslChannel(0.55f, 0.04f, -0.08f), blue = HslChannel(0.03f, 0.04f, -0.08f), magenta = HslChannel(0f, 0f, 0.04f)))
    "retro" -> EditSettings(lookVersion = 0, exposure = 0.03f, contrast = 0.045f, saturation = 0.1f, highlights = -0.04f, shadows = 0.02f, hsl = HslSettings(red = HslChannel(0f, 0.03f, 0f), yellow = HslChannel(0f, -0.06f, 0f), green = HslChannel(0.18f, -0.02f, -0.02f), cyan = HslChannel(0.5f, 0.1f, -0.04f), blue = HslChannel(0.36f, 0.15f, -0.08f)))
    "softmono" -> EditSettings(lookVersion = 0, contrast = -0.12f, shadows = 0.12f, highlights = -0.06f, fade = 0.045f)
    else -> EditSettings(lookVersion = 0)
}

/** Material strength sent to the film engine for this stored settings version. */
fun Look.filmStrength(s: EditSettings): Float = when (s.lookVersion) {
    3 -> (s.strength.coerceIn(0f, 1f) / .75f * defaultStrength).coerceAtMost(1f)
    2 -> s.strength.coerceIn(0f, 1f) * defaultStrength
    else -> s.strength.coerceIn(0f, 1f)
}

/** One CPU adjustment pass for the finish plus user edits; stored sliders stay relative to the look. */
fun Look.finishSettings(s: EditSettings): EditSettings {
    if (s.lookVersion == 0 || s.strength <= 0f) return s
    val base = finishBase()
    val amount = when (s.lookVersion) {
        3 -> s.strength.coerceIn(0f, 1f) / .75f
        2 -> s.strength.coerceIn(0f, 1f)
        else -> s.strength.coerceIn(0f, 1f) / defaultStrength
    }
    fun mix(user: Float, preset: Float, low: Float = -1f, high: Float = 1f) =
        (user + preset * amount).coerceIn(low, high)
    var hsl = HslSettings()
    for (color in HslColor.entries) {
        val u = s.hsl.channel(color); val p = base.hsl.channel(color)
        hsl = hsl.withChannel(color, HslChannel(mix(u.hue, p.hue),
            mix(u.saturation, p.saturation), mix(u.lightness, p.lightness)))
    }
    return s.copy(exposure = mix(s.exposure, base.exposure, -2f, 2f),
        contrast = mix(s.contrast, base.contrast), saturation = mix(s.saturation, base.saturation),
        warmth = mix(s.warmth, base.warmth), tint = mix(s.tint, base.tint),
        highlights = mix(s.highlights, base.highlights), shadows = mix(s.shadows, base.shadows),
        fade = mix(s.fade, base.fade, 0f, 1f), hsl = hsl)
}
