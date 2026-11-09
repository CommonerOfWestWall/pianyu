package studio.pianyu.app

import org.json.JSONObject

data class Look(val id: String, val name: String, val film: String, val paper: String,
                val positive: Boolean = false, val halo: Boolean = false,
                val defaultStrength: Float = .65f) {
    fun params(grain: Float = 0f): String = JSONObject().apply {
        put("input_color_space", "sRGB"); put("input_cctf_decoding", true)
        put("output_color_space", "sRGB"); put("output_cctf_encoding", true)
        put("auto_exposure", false); put("exposure_compensation_ev", 0.0)
        put("print_exposure", 1.0); put("film_format_mm", 35.0)
        put("film_stock", film); put("print_stock", paper); put("scan_film", positive)
        put("grain_active", grain > 0f); put("grain_amount", grain.toDouble())
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
    "daily" -> "以 Portra 400 负片与 Kodak Endura 相纸模型为底，默认保留一部分原图。偏暖、肤色柔和，适合日常与人物；暖光下可稍降色温。"
    "cinema" -> "以日光型 Vision3 250D 负片与 Kodak 2383 电影正片模型组合。偏向电影印片的层次与色彩关系，适合街景、建筑与有方向的光。与日常暖色在部分照片上仍会接近。"
    "night" -> "使用上游 CineStill 800T 材料模型搭配 2383，并加入少量光晕。参考钨丝灯电影负片去除防光晕背层的氛围；不是给任何高光都套红边。适合霓虹、灯光与夜景。"
    "natural" -> "参考 Provia 100F 反转片，直接扫描正片模型。色彩相对清爽、平衡，适合自然光与旅行。"
    "landscape" -> "参考 Velvia 100 反转片，直接扫描正片模型。更浓的色彩与较强对比适合植物和风光；拍人物时建议降低强度。"
    "retro" -> "参考 Kodachrome 64 正片材料模型，保留克制的复古色彩。偏向有年代感的街景与日常物件，具体偏色随原图光线而变。"
    else -> "参考 Acros 100 II 黑白负片与 Ilford Multigrade IV RC 相纸模型。以灰阶、纹理和明暗关系为主，默认全强度；降低强度会混回原图颜色。"
}

val Looks = listOf(
    Look("daily", "日常暖色", "kodak_portra_400", "kodak_portra_endura"),
    Look("cinema", "电影日光", "kodak_vision3_250d", "kodak_2383"),
    Look("night", "夜色光晕", "cinestill_800t", "kodak_2383", halo = true),
    Look("natural", "自然正片", "fujifilm_provia_100f", "kodak_portra_endura", positive = true),
    Look("landscape", "浓彩风光", "fujifilm_velvia_100", "kodak_portra_endura", positive = true),
    Look("retro", "克制复古", "kodak_kodachrome_64", "kodak_portra_endura", positive = true),
    Look("softmono", "柔和黑白", "fujifilm_neopan_acros_100_ii", "ilford_multigrade_iv_rc", defaultStrength = 1f)
)
