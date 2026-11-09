package studio.pianyu.app

/** One bounded HSL adjustment. Values are normalized to -1..1. */
data class HslChannel(val hue: Float = 0f, val saturation: Float = 0f, val lightness: Float = 0f) {
    fun bounded() = HslChannel(
        if (hue.isFinite()) hue.coerceIn(-1f, 1f) else 0f,
        if (saturation.isFinite()) saturation.coerceIn(-1f, 1f) else 0f,
        if (lightness.isFinite()) lightness.coerceIn(-1f, 1f) else 0f
    )
    fun isZero() = hue == 0f && saturation == 0f && lightness == 0f
}

enum class HslColor { RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PURPLE, MAGENTA }

/** Immutable eight-color HSL recipe. Channel order is red, orange, yellow, green, cyan, blue, purple, magenta. */
data class HslSettings(
    val red: HslChannel = HslChannel(), val orange: HslChannel = HslChannel(),
    val yellow: HslChannel = HslChannel(), val green: HslChannel = HslChannel(),
    val cyan: HslChannel = HslChannel(), val blue: HslChannel = HslChannel(),
    val purple: HslChannel = HslChannel(), val magenta: HslChannel = HslChannel()
) {
    fun channel(color: HslColor): HslChannel = when (color) {
        HslColor.RED -> red; HslColor.ORANGE -> orange; HslColor.YELLOW -> yellow; HslColor.GREEN -> green
        HslColor.CYAN -> cyan; HslColor.BLUE -> blue; HslColor.PURPLE -> purple; HslColor.MAGENTA -> magenta
    }
    fun withChannel(color: HslColor, value: HslChannel): HslSettings = when (color) {
        HslColor.RED -> copy(red = value.bounded()); HslColor.ORANGE -> copy(orange = value.bounded())
        HslColor.YELLOW -> copy(yellow = value.bounded()); HslColor.GREEN -> copy(green = value.bounded())
        HslColor.CYAN -> copy(cyan = value.bounded()); HslColor.BLUE -> copy(blue = value.bounded())
        HslColor.PURPLE -> copy(purple = value.bounded()); HslColor.MAGENTA -> copy(magenta = value.bounded())
    }
    fun bounded() = HslSettings(red.bounded(), orange.bounded(), yellow.bounded(), green.bounded(),
        cyan.bounded(), blue.bounded(), purple.bounded(), magenta.bounded())
    fun isZero() = red.isZero() && orange.isZero() && yellow.isZero() && green.isZero() &&
        cyan.isZero() && blue.isZero() && purple.isZero() && magenta.isZero()
}
