package studio.pianyu.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import java.security.MessageDigest

/** Debug-only pipeline studies. Imported-study is explicit and reads, never edits, the library. */
class PipelineAuditActivity : ComponentActivity() {
    private var status by mutableStateOf("等待审计…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val action = intent.getStringExtra("action") ?: "render"
        if (action == "editor") openEditor() else {
            setContent { Text(status) }
            lifecycleScope.launch {
                when (action) {
                    "recipes" -> runRecipes(intent.getStringExtra("study") ?: "")
                    "imported-study" -> runImportedStudy()
                    "imported-heif" -> runImportedHeif()
                    else -> runAudit()
                }
            }
        }
    }

    /** User-authorized private study; none of these images enter release/public sample assets. */
    private suspend fun runImportedHeif() {
        val root = File(filesDir, "private-imported-heif-v12").apply { mkdirs() }
        File(root, "done.txt").delete(); File(root, "error.txt").delete()
        try {
            withContext(Dispatchers.Default) {
                val photos = JSONObject(File(filesDir, "library-v2.json").readText()).getJSONArray("photos")
                val photo = photos.getJSONObject(4) // Only an already imported, authorized landscape.
                val source = PhotoIO.decode(this@PipelineAuditActivity, Uri.parse(photo.getString("uri")), 0)
                try {
                    val settings = EditSettings()
                    val film = withContext(NativeCore.dispatcher) {
                        NativeCore.render(this@PipelineAuditActivity, source, Looks[settings.lookIndex],
                            settings.grain, settings.filmExposure, ProcessingStage.EXPORT,
                            settings.autoFilmExposure, settings.autoFilmExposureMethod)
                    }
                    val output = try { PhotoIO.finish(source, film, settings) } finally { film.recycle() }
                    try {
                        File(root, "rendered.png").outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        val uri = PhotoIO.save(this@PipelineAuditActivity, output, share = true, format = ExportFormat.HEIF)
                        try {
                            contentResolver.openInputStream(uri)!!.use { input ->
                                File(root, "exported.heic").outputStream().use { input.copyTo(it) }
                            }
                            val decoded = contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it)) }
                            try {
                                check(decoded.width == output.width && decoded.height == output.height)
                                File(root, "decoded.png").outputStream().use { check(decoded.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                                File(root, "result.json").writeText(JSONObject().put("width", output.width).put("height", output.height)
                                    .put("mime", contentResolver.getType(uri)).put("format", "HEIF")
                                    .put("jpeg_intermediate", false).put("private_user_input", true)
                                    .put("settings", settingsToJson(settings)).toString(2))
                            } finally { decoded.recycle() }
                        } finally { File(cacheDir, "exports/${uri.lastPathSegment}").delete() }
                    } finally { output.recycle() }
                } finally { source.recycle() }
                File(root, "done.txt").writeText("PASS: original-size imported HEIF -> pixels -> film -> HEIF -> decoded")
            }
            status = "HEIF 原尺寸检查完成（仅本地）"
        } catch (failure: Exception) { File(root, "error.txt").writeText(failure.stackTraceToString()); status = "HEIF 检查失败" }
    }

    private suspend fun runImportedStudy() {
        val root = File(filesDir, "private-imported-study-v12").apply { mkdirs() }
        val done = File(root, "done.txt"); val error = File(root, "error.txt")
        done.delete(); error.delete()
        try {
            withContext(Dispatchers.Default) {
                val photos = JSONObject(File(filesDir, "library-v2.json").readText()).getJSONArray("photos")
                val rows = JSONArray()
                for (index in 0 until photos.length()) {
                    val photo = photos.getJSONObject(index)
                    val uri = photo.optString("uri").takeIf { it.isNotEmpty() } ?: continue
                    val number = index + 1
                    withContext(Dispatchers.Main) { status = "正在检查已导入照片 $number…" }
                    val source = PhotoIO.decode(this@PipelineAuditActivity, Uri.parse(uri), 960)
                    try {
                        val current = settingsFromJson(photo.optJSONObject("settings"))
                        val base = EditSettings(lookIndex = current.lookIndex)
                        val prefix = "imported-%03d".format(java.util.Locale.ROOT, number)
                        save(root, "$prefix-source", source, JSONObject().put("number", number)
                            .put("private_user_input", true).put("input_decoder", "PhotoIO/ImageDecoder/sRGB/edge960"), rows)
                        val recipes = mutableListOf("current" to current, "default" to base,
                            "meter-off" to base.copy(autoFilmExposure = false),
                            "ev-minus-05" to base.copy(filmExposure = -.5f),
                            "ev-plus-05" to base.copy(filmExposure = .5f),
                            "ev-plus-10" to base.copy(filmExposure = 1f))
                        if (number in listOf(2, 5, 11, 12)) Looks.indices.filter { it != current.lookIndex }.forEach {
                            recipes += "look-${Looks[it].id}" to EditSettings(lookIndex = it)
                        }
                        for ((name, settings) in recipes) {
                            val look = Looks[settings.lookIndex]
                            val film = withContext(NativeCore.dispatcher) {
                                NativeCore.render(this@PipelineAuditActivity, source, look, settings.grain,
                                    settings.filmExposure, autoFilmExposure = settings.autoFilmExposure,
                                    autoFilmExposureMethod = settings.autoFilmExposureMethod)
                            }
                            val output = try { PhotoIO.finish(source, film, settings) } finally { film.recycle() }
                            try {
                                save(root, "$prefix-$name", output, JSONObject().put("number", number)
                                    .put("private_user_input", true).put("settings", settingsToJson(settings))
                                    .put("params", JSONObject(look.params(settings.grain, settings.filmExposure,
                                        settings.autoFilmExposure, settings.autoFilmExposureMethod))), rows)
                            } finally { output.recycle() }
                        }
                        File(root, "manifest.json").writeText(rows.toString(2))
                    } finally { source.recycle() }
                }
                done.writeText("PASS: ${rows.length()} private inputs/outputs; library unchanged")
            }
            status = "导入照片对照已完成（仅本地）"
        } catch (failure: Exception) { error.writeText(failure.stackTraceToString()); status = "私人照片检查失败" }
    }

    private suspend fun runRecipes(name: String) {
        require(name.matches(Regex("[A-Za-z0-9_-]+")))
        val directory = File(filesDir, "recipe-study/$name")
        val done = File(directory, "activity-done.txt")
        val error = File(directory, "activity-error.txt")
        done.delete(); error.delete()
        try {
            withContext(Dispatchers.Default) {
                val input = File(directory, "input.png").readBytes()
                // Exercise the same orientation/color-space decoder as user photos.
                // 1536 bounds this diagnostic study only; app export still uses edge=0.
                val original = PhotoIO.decode(this@PipelineAuditActivity,
                    Uri.fromFile(File(directory, "input.png")), 1536)
                try {
                    val inputSha = MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
                    val recipes = JSONArray(File(directory, "recipes.json").readText())
                    val rows = JSONArray()
                    val outputs = File(directory, "outputs").apply { mkdirs() }
                    for (i in 0 until recipes.length()) {
                        val recipe = recipes.getJSONObject(i)
                        val id = recipe.getString("id")
                        require(id.matches(Regex("[A-Za-z0-9_-]+")))
                        val settings = settingsFromJson(recipe.getJSONObject("settings"))
                        require(settings.crop == CropRect() && settings.cropAspect == 0f && settings.quarterTurns == 0 &&
                            settings.straightenDegrees == 0f && !settings.flipHorizontal && !settings.flipVertical)
                        val look = Looks[settings.lookIndex]
                        val film = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, original, look, settings.grain, settings.filmExposure, autoFilmExposure = settings.autoFilmExposure, autoFilmExposureMethod = settings.autoFilmExposureMethod) }
                        val output = try { PhotoIO.finish(original, film, settings) } finally { film.recycle() }
                        try {
                            require(output.width == original.width && output.height == original.height)
                            save(outputs, id, output, JSONObject().put("input_sha256", inputSha)
                                .put("input_decoder", "PhotoIO.decode/ImageDecoder/sRGB/edge1536")
                                .put("settings", settingsToJson(settings)).put("effective_finish", settingsToJson(look.finishSettings(settings)))
                                .put("params", JSONObject(look.params(settings.grain, settings.filmExposure, settings.autoFilmExposure, settings.autoFilmExposureMethod))), rows)
                            File(directory, "manifest.json").writeText(rows.toString(2))
                        } finally { output.recycle() }
                    }
                } finally { original.recycle() }
            }
            done.writeText("PASS"); status = "配方渲染完成"
        } catch (failure: Exception) {
            error.writeText(failure.stackTraceToString()); status = "配方渲染失败"
        }
    }

    private fun openEditor() {
        val model = EditorModel(application)
        setContent {
            PianyuTheme { EditorScreen(model) {} }
        }
        lifecycleScope.launch {
            model.loadDemo()
            val look = intent.getIntExtra("look", -1)
            val strength = intent.getFloatExtra("strength", -1f)
            val exposure = intent.getFloatExtra("exposure", 0f)
            while (model.state.loading || model.state.source == null) kotlinx.coroutines.delay(50)
            if (intent.getBooleanExtra("resetDemo", false)) model.reset()
            if (look in Looks.indices) model.chooseLook(look)
            if (strength >= 0f || exposure != 0f) {
                val settings = if (strength >= 0f) model.state.settings.withDisplayedStrength(strength) else model.state.settings
                model.edit(settings.copy(exposure = exposure))
            }
        }
    }

    private suspend fun runAudit() {
        val root = File(filesDir, "showcase-v032").apply { mkdirs() }
        File(filesDir, "audit-done.txt").delete()
        File(filesDir, "audit-error.txt").delete()
        val audit = JSONArray()
        val started = System.nanoTime()
        try {
            val inputRoot = File(filesDir, "audit-inputs")
            val names = listOf("portrait", "landscape", "night", "humanity", "phone-landscape", "concert", "candid")
            val inputFiles = names.map { scene -> scene to File(inputRoot, "$scene.png") }
            val sources = inputFiles.map { (scene, file) -> scene to BitmapFactory.decodeFile(file.absolutePath) } +
                listOf("stilllife" to BitmapFactory.decodeStream(assets.open("demo.jpg")))
            withContext(Dispatchers.Default) {
                for ((scene, original) in sources) {
                    requireNotNull(original) { "无法解码 $scene" }
                    withContext(Dispatchers.Main) { status = "渲染 $scene…" }
                    save(root, "$scene-original", original, JSONObject().put("scene", scene).put("type", "input").put("ai_generated_input", true), audit)
                    for ((index, look) in Looks.withIndex()) {
                        val renderStart = System.nanoTime()
                        val film = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, original, look) }
                        val settings = EditSettings(lookIndex = index, strength = look.defaultStrength, lookVersion = 1, grain = 0f)
                        val finished = PhotoIO.finish(original, film, settings)
                        save(root, "$scene-${look.id}", finished, JSONObject().put("scene", scene).put("look", look.id)
                            .put("strength", settings.strength).put("params", JSONObject(look.params()))
                            .put("settings", settingsToJson(settings))
                            .put("elapsed_ms", (System.nanoTime() - renderStart) / 1_000_000.0)
                            .put("pss_kib", android.os.Debug.getPss()), audit)
                        if (scene == "humanity" && index == 1) {
                            val tuned = settings.copy(strength=.55f, exposure=.15f, warmth=.08f, contrast=.05f, highlights=-.15f)
                            val output = PhotoIO.finish(original, film, tuned)
                            save(root, "humanity-tuned", output, JSONObject().put("settings", settingsToJson(tuned)), audit)
                            output.recycle()
                        }
                        if (scene == "phone-landscape" && index == 3) {
                            val tuned = settings.copy(exposure=.2f, shadows=.12f, warmth=.06f, saturation=.08f, highlights=-.15f)
                            val output = PhotoIO.finish(original, film, tuned)
                            save(root, "phone-landscape-tuned", output, JSONObject().put("settings", settingsToJson(tuned)), audit)
                            output.recycle()
                        }
                        if (scene == "concert" && index == 2) {
                            val tuned = settings.copy(strength=.45f, exposure=.15f, highlights=-.2f, warmth=-.05f, shadows=.08f)
                            val output = PhotoIO.finish(original, film, tuned)
                            save(root, "concert-tuned", output, JSONObject().put("settings", settingsToJson(tuned)), audit)
                            output.recycle()
                        }
                        if (scene == "candid" && index == 0) {
                            val tuned = settings.copy(strength=.8f, exposure=.2f, warmth=.08f, contrast=.08f,
                                highlights=-.12f, fade=.12f, grain=.25f)
                            val grained = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, original, look, tuned.grain) }
                            val output = PhotoIO.finish(original, grained, tuned)
                            save(root, "candid-tuned", output, JSONObject().put("settings", settingsToJson(tuned)), audit)
                            grained.recycle(); output.recycle()
                            for (strength in listOf(.35f, 1f)) {
                                val strengthSettings = tuned.copy(strength = strength)
                                val strengthFilm = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, original, look, strengthSettings.grain) }
                                val strengthOutput = PhotoIO.finish(original, strengthFilm, strengthSettings)
                                save(root, "candid-strength-${(strength * 100).toInt()}", strengthOutput,
                                    JSONObject().put("settings", settingsToJson(strengthSettings)), audit)
                                strengthFilm.recycle(); strengthOutput.recycle()
                            }
                        }
                        if (scene == "candid" && index == 6) {
                            val tuned = settings.copy(strength=1f, exposure=.2f, contrast=.15f,
                                highlights=-.12f, fade=.08f, grain=.3f, vignette=.1f)
                            val grained = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, original, look, tuned.grain) }
                            val output = PhotoIO.finish(original, grained, tuned)
                            save(root, "candid-monochrome-tuned", output, JSONObject().put("settings",settingsToJson(tuned)), audit)
                            grained.recycle(); output.recycle()
                        }
                        if (scene == "portrait" && index == 0) {
                            for (strength in listOf(.0f, .35f, 1f)) {
                                val output = PhotoIO.finish(original, film, settings.copy(strength = strength))
                                save(root, "portrait-strength-${(strength * 100).toInt()}", output, JSONObject().put("strength", strength), audit)
                                output.recycle()
                            }
                            for (exposure in listOf(-.5f, .5f)) {
                                val output = PhotoIO.finish(original, film, settings.copy(exposure = exposure))
                                save(root, "portrait-exposure-$exposure", output, JSONObject().put("exposure_ev", exposure), audit)
                                output.recycle()
                            }
                            val output = PhotoIO.finish(original, film, settings.copy(hsl=HslSettings(red=HslChannel(saturation=-.6f))))
                            save(root, "portrait-hsl-red-minus60", output, JSONObject().put("red_saturation", -.6), audit)
                            output.recycle()
                        }
                        if (scene == "landscape" && index == 3) {
                            val output = PhotoIO.finish(original, film, settings.copy(hsl=HslSettings(green=HslChannel(saturation=-.6f))))
                            save(root, "landscape-hsl-green-minus60", output, JSONObject().put("green_saturation", -.6), audit)
                            output.recycle()
                        }
                        if (scene == "stilllife" && index == 0) {
                            for (grain in listOf(.5f, 1f)) {
                                val grained = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, original, look, grain) }
                                val output = PhotoIO.finish(original, grained, settings.copy(grain = grain))
                                save(root, "stilllife-grain-${(grain * 100).toInt()}", output, JSONObject().put("grain", grain).put("strength", settings.strength), audit)
                                grained.recycle(); output.recycle()
                            }
                        }
                        finished.recycle(); film.recycle()
                    }
                    original.recycle()
                }
            }
            audit.put(fullResolutionAudit(inputRoot))
            File(root, "manifest.json").writeText(audit.toString(2))
            File(filesDir, "audit-validation.json").writeText(JSONObject().put("elapsed_ms", (System.nanoTime() - started) / 1_000_000.0)
                .put("pss_kib", android.os.Debug.getPss()).put("results", audit).toString(2))
            File(filesDir, "audit-done.txt").writeText("ok\n")
            status = "审计完成：${audit.length()} 条记录"
        } catch (error: Throwable) {
            File(filesDir, "audit-error.txt").writeText(error.stackTraceToString())
            status = "审计失败：${error.message}"
        }
    }

    private fun save(root: File, name: String, bitmap: Bitmap, values: JSONObject, rows: JSONArray) {
        val file = File(root, "$name.png")
        file.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val readback = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        require(readback.width == bitmap.width && readback.height == bitmap.height)
        val expected = IntArray(bitmap.width); val actual = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(expected, 0, bitmap.width, 0, y, bitmap.width, 1)
            readback.getPixels(actual, 0, bitmap.width, 0, y, bitmap.width, 1)
            require(expected.contentEquals(actual)) { "PNG pixel mismatch: ${file.name}, row=$y" }
        }
        readback.recycle()
        values.put("file", file.name).put("width", bitmap.width).put("height", bitmap.height)
            .put("sha256", MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
        rows.put(values)
    }

    private suspend fun grainDelta(source: Bitmap): JSONObject {
        val base = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, source, Looks.first(), 0f) }
        val grain = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, source, Looks.first(), 1f) }
        val width = minOf(base.width, 256); val height = minOf(base.height, 256)
        var delta = 0L
        for (y in 0 until height) for (x in 0 until width) {
            val a = base.getPixel(x, y); val b = grain.getPixel(x, y)
            delta += abs(((a shr 16) and 255) - ((b shr 16) and 255)) +
                abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))
        }
        base.recycle(); grain.recycle()
        return JSONObject().put("grain_delta_mean_rgb", delta.toDouble() / (width * height * 3.0))
    }

    private suspend fun fullResolutionAudit(inputRoot: File): JSONObject {
        val rows = JSONArray()
        for (name in listOf("sample_orientation6.jpg", "sample_orientation6.heic")) {
            val sourceFile = File(inputRoot, name)
            val cached = File(cacheDir, "exports/audit-$name").apply {
                parentFile?.mkdirs(); sourceFile.inputStream().use { input -> outputStream().use { output -> input.copyTo(output) } }
            }
            val uri = FileProvider.getUriForFile(this, "${packageName}.files", cached)
            val source = PhotoIO.decode(this, uri, 0)
            require(source.width == 2304 && source.height == 3456) { "$name decoded ${source.width}x${source.height}" }
            val film = withContext(NativeCore.dispatcher) { NativeCore.render(this@PipelineAuditActivity, source, Looks.first()) }
            val finished = PhotoIO.finish(source, film, EditSettings(strength = .8f, exposure = .25f, sharpen = .35f,
                hsl = HslSettings(red = HslChannel(saturation = -.2f))))
            val out = PhotoIO.save(this, finished, share = true, lossless = true)
            val check = contentResolver.openInputStream(out).use { BitmapFactory.decodeStream(it) }
            require(check != null && check.width == 2304 && check.height == 3456)
            val expected = IntArray(finished.width); val actual = IntArray(finished.width)
            for (y in 0 until finished.height) {
                finished.getPixels(expected, 0, finished.width, 0, y, finished.width, 1)
                check.getPixels(actual, 0, check.width, 0, y, check.width, 1)
                require(expected.contentEquals(actual)) { "$name PNG pixel mismatch at row $y" }
            }
            rows.put(JSONObject().put("fixture", name).put("width", check.width).put("height", check.height)
                .put("png_readback", true))
            check.recycle(); finished.recycle(); film.recycle(); source.recycle()
            val copied = PhotoIO.shareSource(this, uri)
            val a = sourceFile.readBytes(); val b = contentResolver.openInputStream(copied)!!.use { it.readBytes() }
            require(a.contentEquals(b)) { "$name source copy mismatch" }
        }
        return JSONObject().put("full_resolution", rows)
    }
}
