package studio.pianyu.app

import org.json.JSONObject

data class Look(val id: String, val name: String, val film: String, val paper: String,
                val positive: Boolean = false, val halo: Boolean = false,
                val defaultStrength: Float = .65f, val creative: Boolean = false) {
    fun params(grain: Float = 0f, filmExposure: Float = 0f, autoFilmExposure: Boolean = false,
        autoFilmExposureMethod: String = "protect_highlights", printStock: String = "",
        printExposure: Float = 0f): String = JSONObject().apply {
        put("input_color_space", "sRGB"); put("input_cctf_decoding", true)
        put("output_color_space", "sRGB"); put("output_cctf_encoding", true)
        put("auto_exposure", autoFilmExposure)
        put("auto_exposure_method", autoFilmExposureMethod.takeIf {
            it in MeteringMethods } ?: "protect_highlights")
        put("exposure_compensation_ev", (filmExposure.takeIf { it.isFinite() } ?: 0f).coerceIn(-2f, 2f).toDouble())
        put("print_exposure", printExposureGain(printExposure)); put("film_format_mm", 35.0)
        put("film_stock", film); put("print_stock", effectivePrintStock(printStock)); put("scan_film", positive)
        // The engine supports 0..2. Keep a subtle lower half, with a visibly
        // stronger upper half for phone-sized images; no replacement noise layer.
        put("grain_active", grain > 0f); put("grain_amount", (grain.coerceIn(0f, 1f) * 2f).toDouble())
        put("glare_active", false); put("halation_active", halo)
        put("halation_amount", .15)
        put("scene_latitude_active", false)
    }.toString()
}

val Look.reference: String get() = when (film) {
    "kodak_portra_400" -> "Kodak Portra 400"
    "kodak_vision3_250d" -> "Kodak Vision3 250D"
    "kodak_vision3_500t" -> "Kodak Vision3 500T"
    "cinestill_800t" -> "CineStill 800T"
    "fujifilm_provia_100f" -> "Fujifilm Provia 100F"
    "fujifilm_velvia_100" -> "Fujifilm Velvia 100"
    "kodak_kodachrome_64" -> "Kodak Kodachrome 64"
    else -> "Fujifilm Neopan Acros 100 II"
}
val Looks = listOf(
    Look("daily", "日常暖色", "kodak_portra_400", "kodak_portra_endura", defaultStrength = .72f),
    Look("cinema", "电影日光", "kodak_vision3_250d", "kodak_2383", defaultStrength = .82f),
    Look("night", "夜色光晕", "cinestill_800t", "kodak_2383", halo = true, defaultStrength = .8f),
    Look("natural", "自然正片", "fujifilm_provia_100f", "kodak_portra_endura", positive = true, defaultStrength = .9f),
    Look("landscape", "浓彩风光", "fujifilm_velvia_100", "kodak_portra_endura", positive = true, defaultStrength = .9f),
    Look("retro", "红蓝复古", "kodak_kodachrome_64", "kodak_portra_endura", positive = true, defaultStrength = 1f),
    Look("softmono", "柔和黑白", "fujifilm_neopan_acros_100_ii", "ilford_multigrade_iv_rc", defaultStrength = 1f)
) + ReferencePalettes.map { it.look } + SeasonalPalettes.map { it.look } + CinemaPalettes.map { it.look }
