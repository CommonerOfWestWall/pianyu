package studio.pianyu.app

data class EditSettings(
    val lookIndex: Int = 0,
    val strength: Float = .65f,
    val brightness: Float = 0f,
    val warmth: Float = 0f,
    val grain: Float = 0f,
    val cropAspect: Float = 0f,
    val exposure: Float = 0f,
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
    val flipVertical: Boolean = false
) {
    fun colorRecipe() = copy(cropAspect = 0f, crop = CropRect(), quarterTurns = 0,
        straightenDegrees = 0f, flipHorizontal = false, flipVertical = false)

    fun withColorRecipe(recipe: EditSettings) = recipe.copy(cropAspect = cropAspect, crop = crop,
        quarterTurns = quarterTurns, straightenDegrees = straightenDegrees,
        flipHorizontal = flipHorizontal, flipVertical = flipVertical)
}
