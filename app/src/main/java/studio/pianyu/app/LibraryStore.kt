package studio.pianyu.app

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PhotoEntry(val id: String, val uri: String?, val name: String,
    val settings: EditSettings = EditSettings(), val addedAt: Long = System.currentTimeMillis())

data class StoredLibrary(val entries: List<PhotoEntry> = emptyList(), val recipe: EditSettings? = null)

class LibraryStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "library-v2.json"))
    val thumbnails = File(context.filesDir, "library-thumbnails").apply { mkdirs() }

    @Synchronized fun read(): StoredLibrary {
        if (!file.baseFile.exists()) return StoredLibrary()
        val json = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
        val array = json.optJSONArray("photos") ?: JSONArray()
        val photos = (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            PhotoEntry(row.getString("id"), row.optString("uri").takeIf { it.isNotEmpty() },
                row.optString("name", "照片"), settingsFromJson(row.optJSONObject("settings")),
                row.optLong("addedAt", 0))
        }
        return StoredLibrary(photos, json.optJSONObject("recipe")?.let(::settingsFromJson))
    }

    @Synchronized fun write(value: StoredLibrary) {
        val json = JSONObject().put("version", 2).put("photos", JSONArray().apply {
            value.entries.forEach { photo -> put(JSONObject().put("id", photo.id)
                .put("uri", photo.uri ?: "").put("name", photo.name)
                .put("addedAt", photo.addedAt).put("settings", settingsToJson(photo.settings))) }
        })
        value.recipe?.let { json.put("recipe", settingsToJson(it)) }
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    fun thumbnail(id: String, settings: EditSettings): File {
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        return File(thumbnails, "$id-${settings.hashCode()}.jpg")
    }
}

fun settingsToJson(s: EditSettings): JSONObject = JSONObject().apply {
    put("lookIndex", s.lookIndex); put("strength", s.strength); put("brightness", s.brightness)
    put("warmth", s.warmth); put("grain", s.grain); put("cropAspect", s.cropAspect)
    put("exposure", s.exposure); put("contrast", s.contrast); put("saturation", s.saturation)
    put("highlights", s.highlights); put("shadows", s.shadows); put("fade", s.fade)
    put("tint", s.tint); put("sharpen", s.sharpen); put("vignette", s.vignette)
    put("crop", JSONArray(listOf(s.crop.left, s.crop.top, s.crop.right, s.crop.bottom)))
    put("quarterTurns", s.quarterTurns); put("straightenDegrees", s.straightenDegrees)
    put("flipHorizontal", s.flipHorizontal); put("flipVertical", s.flipVertical)
    put("hsl", JSONObject().apply {
        val hsl = s.hsl.bounded()
        fun channel(name: String, c: HslChannel) { put(name, JSONObject().put("hue", c.hue).put("saturation", c.saturation).put("lightness", c.lightness)) }
        channel("red", hsl.red); channel("orange", hsl.orange); channel("yellow", hsl.yellow); channel("green", hsl.green)
        channel("cyan", hsl.cyan); channel("blue", hsl.blue); channel("purple", hsl.purple); channel("magenta", hsl.magenta)
    })
}

fun settingsFromJson(j: JSONObject?): EditSettings {
    if (j == null) return EditSettings()
    fun number(key: String, default: Float = 0f): Float = runCatching { j.optDouble(key, default.toDouble()).toFloat() }.getOrDefault(default).let {
        if (it.isFinite()) it else default
    }
    fun hslChannel(name: String): HslChannel {
        val c = j.optJSONObject("hsl")?.optJSONObject(name)
        fun value(key: String) = runCatching { c?.optDouble(key, 0.0)?.toFloat() ?: 0f }.getOrDefault(0f)
            .let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
        return HslChannel(value("hue"), value("saturation"), value("lightness"))
    }
    val rect = j.optJSONArray("crop")
    fun coordinate(index: Int, default: Float): Float = rect?.optDouble(index, default.toDouble())
        ?.toFloat()?.takeIf { it.isFinite() } ?: default
    return EditSettings(lookIndex = j.optInt("lookIndex").coerceIn(Looks.indices),
        strength = number("strength", .65f).coerceIn(0f, 1f), brightness = number("brightness").coerceIn(-1f, 1f),
        warmth = number("warmth").coerceIn(-1f, 1f), grain = number("grain").coerceIn(0f, 1f),
        cropAspect = number("cropAspect").coerceAtLeast(0f), exposure = number("exposure").coerceIn(-2f, 2f),
        contrast = number("contrast").coerceIn(-1f, 1f), saturation = number("saturation").coerceIn(-1f, 1f),
        highlights = number("highlights").coerceIn(-1f, 1f), shadows = number("shadows").coerceIn(-1f, 1f),
        fade = number("fade").coerceIn(0f, 1f), tint = number("tint").coerceIn(-1f, 1f),
        sharpen = number("sharpen").coerceIn(0f, 1f), vignette = number("vignette").coerceIn(0f, 1f),
        crop = CropRect(coordinate(0, 0f), coordinate(1, 0f), coordinate(2, 1f), coordinate(3, 1f)).normalized(),
        quarterTurns = ((j.optInt("quarterTurns") % 4) + 4) % 4,
        straightenDegrees = number("straightenDegrees").coerceIn(-20f, 20f),
        flipHorizontal = j.optBoolean("flipHorizontal"), flipVertical = j.optBoolean("flipVertical"),
        hsl = HslSettings(hslChannel("red"), hslChannel("orange"), hslChannel("yellow"), hslChannel("green"),
            hslChannel("cyan"), hslChannel("blue"), hslChannel("purple"), hslChannel("magenta")))
}

fun PhotoEntry.contentUri(): Uri? = uri?.let(Uri::parse)
