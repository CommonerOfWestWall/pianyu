package studio.pianyu.app

data class EditSettings(
    val lookIndex: Int = 0,
    // Version 3 puts the calibrated recipe at 75%, leaving creative headroom.
    val strength: Float = .75f,
    val brightness: Float = 0f,
    val warmth: Float = 0f,
    val grain: Float = .125f,
    val cropAspect: Float = 0f,
    val exposure: Float = 0f,
    // Scene exposure before the film response; existing exposure stays post-render.
    val filmExposure: Float = 0f,
    val autoFilmExposure: Boolean = true,
    // Stored with the recipe so a later default change cannot reinterpret it.
    val autoFilmExposureMethod: String = "protect_highlights",
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val fade: Float = 0f,
    val tint: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    val hsl: HslSettings = HslSettings(),
    val crop: CropRect = CropRect(),
    val quarterTurns: Int = 0,
    val straightenDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    // 0 preserves recipes saved before the finishing layer; 1 is the legacy
    // calibrated-strength model; 2 is normalized; 3 has a 75% reference point.
    val lookVersion: Int = 3
) {
    // Display old v2 edits at the equivalent position without rewriting them.
    val displayedStrength: Float get() = if (lookVersion == 2) strength * .75f else strength

    fun withDisplayedStrength(value: Float) = copy(strength = value.coerceIn(0f, 1f),
        lookVersion = if (lookVersion == 2) 3 else lookVersion)

    fun colorRecipe() = copy(cropAspect = 0f, crop = CropRect(), quarterTurns = 0,
        straightenDegrees = 0f, flipHorizontal = false, flipVertical = false)

    fun withColorRecipe(recipe: EditSettings) = recipe.copy(cropAspect = cropAspect, crop = crop,
        quarterTurns = quarterTurns, straightenDegrees = straightenDegrees,
        flipHorizontal = flipHorizontal, flipVertical = flipVertical)
}
