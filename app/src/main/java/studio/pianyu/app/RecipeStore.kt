package studio.pianyu.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class NamedRecipe(val id: String, val name: String, val settings: EditSettings)
/** exportEdge=0 means the decoded source dimensions (the default). */
data class SavedRecipes(val recipes: List<NamedRecipe> = emptyList(), val exportEdge: Int = 0, val lossless: Boolean = false,
    val format: ExportFormat = if (lossless) ExportFormat.PNG else ExportFormat.JPEG)

class RecipeStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "recipes-v1.json"))

    @Synchronized fun read(): SavedRecipes {
        if (!file.baseFile.exists()) return SavedRecipes()
        val json = file.openRead().bufferedReader().use { JSONObject(it.readText()) }
        val rows = json.optJSONArray("recipes") ?: JSONArray()
        val recipes = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            NamedRecipe(row.getString("id"), row.getString("name"),
                settingsFromJson(row.optJSONObject("settings")).colorRecipe())
        }
        // Version 1 could not distinguish a deliberate resize from its forced default.
        // Upgrade once to original dimensions; version 2 retains explicit resize choices.
        val edge = if (json.optInt("version", 1) < 2) 0 else json.optInt("exportEdge", 0)
        val legacyLossless = json.optBoolean("lossless", false)
        val format = ExportFormat.fromStored(if (json.has("exportFormat")) json.optString("exportFormat") else null, legacyLossless)
        return SavedRecipes(recipes, edge.takeIf { it == 0 || it == 1080 || it == 2048 } ?: 0, format == ExportFormat.PNG, format)
    }

    @Synchronized fun write(value: SavedRecipes) {
        val json = JSONObject().put("version", 2).put("exportEdge", value.exportEdge)
            .put("lossless", value.format == ExportFormat.PNG).put("exportFormat", value.format.name)
            .put("recipes", JSONArray().apply {
                value.recipes.forEach { recipe -> put(JSONObject().put("id", recipe.id)
                    .put("name", recipe.name).put("settings", settingsToJson(recipe.settings.colorRecipe()))) }
            })
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
