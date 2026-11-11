package studio.pianyu.app

enum class ExportFormat(val mime: String, val suffix: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    HEIF("image/heic", "heic");

    companion object {
        fun fromStored(value: String?, legacyLossless: Boolean): ExportFormat =
            when (value?.uppercase()) {
                "JPEG" -> JPEG
                "PNG" -> PNG
                "HEIF" -> HEIF
                else -> if (legacyLossless) PNG else JPEG
            }
    }
}
