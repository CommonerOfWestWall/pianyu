package studio.pianyu.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class NamedRecipe(val id: String, val name: String, val settings: EditSettings)
data class SavedRecipes(val recipes: List<NamedRecipe> = emptyList(), val exportEdge: Int = 2048)

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
        return SavedRecipes(recipes, json.optInt("exportEdge", 2048).takeIf { it == 1080 || it == 2048 } ?: 2048)
    }

    @Synchronized fun write(value: SavedRecipes) {
        val json = JSONObject().put("version", 1).put("exportEdge", value.exportEdge)
            .put("recipes", JSONArray().apply {
                value.recipes.forEach { recipe -> put(JSONObject().put("id", recipe.id)
                    .put("name", recipe.name).put("settings", settingsToJson(recipe.settings.colorRecipe()))) }
            })
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
