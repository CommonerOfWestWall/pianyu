package studio.pianyu.app

import org.json.JSONObject

data class Look(val id: String, val name: String, val film: String, val paper: String,
                val positive: Boolean = false, val halo: Boolean = false,
                val defaultStrength: Float = .65f) {
    fun params(grain: Float = 0f, filmExposure: Float = 0f, autoFilmExposure: Boolean = false,
        autoFilmExposureMethod: String = "protect_highlights"): String = JSONObject().apply {
        put("input_color_space", "sRGB"); put("input_cctf_decoding", true)
        put("output_color_space", "sRGB"); put("output_cctf_encoding", true)
        put("auto_exposure", autoFilmExposure)
        put("auto_exposure_method", autoFilmExposureMethod.takeIf {
            it in setOf("center_weighted", "balanced", "protect_highlights") } ?: "protect_highlights")
        put("exposure_compensation_ev", (filmExposure.takeIf { it.isFinite() } ?: 0f).coerceIn(-2f, 2f).toDouble())
        put("print_exposure", 1.0); put("film_format_mm", 35.0)
        put("film_stock", film); put("print_stock", paper); put("scan_film", positive)
        // The engine supports 0..2. Keep a subtle lower half, with a visibly
        // stronger upper half for phone-sized images; no replacement noise layer.
        put("grain_active", grain > 0f); put("grain_amount", (grain.coerceIn(0f, 1f) * 2f).toDouble())
        put("glare_active", false); put("halation_active", halo)
        put("halation_amount", .15)
        put("scene_latitude_active", false)
    }.toString()
}

val Look.reference: String get() = when (id) {
    "daily" -> "Kodak Portra 400"
    "cinema" -> "Kodak Vision3 250D"
    "night" -> "CineStill 800T"
    "natural" -> "Fujifilm Provia 100F"
    "landscape" -> "Fujifilm Velvia 100"
    "retro" -> "Kodak Kodachrome 64"
    else -> "Fujifilm Neopan Acros 100 II"
}
val Look.description: String get() = when (id) {
    "daily" -> "Portra 400 负片与 Endura 相纸模型，加片屿柔化反差、微暖和抬升暗部的预设。适合日常与人物；暖光下可稍降色温。"
    "cinema" -> "Vision3 250D 与 2383 印片模型，加片屿略收饱和、高光和微暖暗部的预设。比日常暖色保留更多明暗反差，适合街景、建筑与有方向的光。"
    "night" -> "以 Vision3 500T 为基础的 CineStill 800T 衍生模型，覆盖防光晕属性后搭配 2383。片屿预设抬起暗部、收住高光；光晕随原片高光变化，不给任何灯都套红边。并非独立测量的 800T 数据集。"
    "natural" -> "Provia 100F 正片扫描模型，加片屿清爽预设：略柔反差、提起暗部，微调青蓝，适合自然光与旅行。不会把阴天自动改成晴天。"
    "landscape" -> "Velvia 100 正片扫描模型，加片屿浓彩预设：加强绿蓝层次、收住高光，并约束红橙色的增加。适合植物和风光；拍人物仍建议降低强度。"
    "retro" -> "Kodachrome 64 正片扫描模型，加片屿复古预设：保留红色、深蓝与收敛的绿色，不靠泛黄或褪色制造年代感。适合街头与日常物件；不是某个年代扫描的唯一颜色。"
    else -> "Acros 100 II 黑白负片与 Ilford Multigrade IV RC 相纸模型，加片屿柔化反差、轻抬黑位的预设。推荐强度75已是完整黑白；提高强度会加强柔化预设，降低强度会混回原图颜色。"
}

val Looks = listOf(
    Look("daily", "日常暖色", "kodak_portra_400", "kodak_portra_endura", defaultStrength = .72f),
    Look("cinema", "电影日光", "kodak_vision3_250d", "kodak_2383", defaultStrength = .82f),
    Look("night", "夜色光晕", "cinestill_800t", "kodak_2383", halo = true, defaultStrength = .8f),
    Look("natural", "自然正片", "fujifilm_provia_100f", "kodak_portra_endura", positive = true, defaultStrength = .9f),
    Look("landscape", "浓彩风光", "fujifilm_velvia_100", "kodak_portra_endura", positive = true, defaultStrength = .9f),
    Look("retro", "克制复古", "kodak_kodachrome_64", "kodak_portra_endura", positive = true, defaultStrength = 1f),
    Look("softmono", "柔和黑白", "fujifilm_neopan_acros_100_ii", "ilford_multigrade_iv_rc", defaultStrength = 1f)
)
