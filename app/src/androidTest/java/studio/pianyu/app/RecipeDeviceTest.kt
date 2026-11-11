package studio.pianyu.app

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(action: ()->Unit) = instrumentation.runOnMainSync(action)
    // Poll only the committed base file. AtomicFile.openRead can recover/delete a
    // pending transaction, so another store instance must not poll it mid-write.
    private fun committed(app:IsolatedTestApplication):JSONObject? = try {
        JSONObject(File(app.filesDir,"recipes-v1.json").readText())
    } catch (_:Exception) {null}
    private fun await(condition: ()->Boolean) {
        val end = SystemClock.uptimeMillis()+30_000
        while(SystemClock.uptimeMillis()<end) {
            var passed=false; onMain { passed=condition() }
            if(passed)return
            SystemClock.sleep(40)
        }
        throw AssertionError("recipe condition timeout")
    }

    @Test fun missingExportPreferenceUsesOriginalSizeDefault() {
        val app=IsolatedTestApplication.create(instrumentation.targetContext)
        try {
            File(app.filesDir,"recipes-v1.json").writeText(JSONObject().put("version",1)
                .put("recipes",org.json.JSONArray()).toString())
            assertEquals(0, RecipeStore(app).read().exportEdge)
            File(app.filesDir,"recipes-v1.json").writeText(JSONObject().put("version",1)
                .put("exportEdge",2048).put("recipes",org.json.JSONArray()).toString())
            assertEquals("old forced default must migrate",0, RecipeStore(app).read().exportEdge)
            RecipeStore(app).write(SavedRecipes(exportEdge=1080,lossless=true))
            assertEquals(1080, RecipeStore(app).read().exportEdge)
            assertTrue(RecipeStore(app).read().lossless)
            assertEquals(ExportFormat.PNG, RecipeStore(app).read().format)
            RecipeStore(app).write(SavedRecipes(exportEdge=0, format=ExportFormat.HEIF))
            val heif = RecipeStore(app).read()
            assertEquals(ExportFormat.HEIF, heif.format)
            assertFalse(heif.lossless)
            assertEquals("HEIF", JSONObject(File(app.filesDir,"recipes-v1.json").readText()).getString("exportFormat"))
            File(app.filesDir,"recipes-v1.json").writeText(JSONObject().put("version",2)
                .put("lossless",true).put("recipes",org.json.JSONArray()).toString())
            assertEquals(ExportFormat.PNG, RecipeStore(app).read().format)
            File(app.filesDir,"recipes-v1.json").writeText(JSONObject().put("version",1)
                .put("exportEdge",999).put("recipes",org.json.JSONArray()).toString())
            assertEquals(0, RecipeStore(app).read().exportEdge)
        } finally { app.cleanup() }
    }

    @Test fun namedRecipesPersistApplyWithoutGeometryAndUndoThenRenameDelete() {
        val app=IsolatedTestApplication.create(instrumentation.targetContext)
        lateinit var model:EditorModel
        lateinit var restored:EditorModel
        val models=mutableListOf<EditorModel>()
        try {
            onMain {model=EditorModel(app);models.add(model);model.loadDemo()}
            await {!model.recipesLoading&&!model.library.restoring&&!model.state.loading&&model.state.displayedSettings==model.state.settings&&model.state.source!=null}
            val color=EditSettings(lookIndex=3,strength=.48f,exposure=.35f,
                hsl=HslSettings(blue=HslChannel(saturation=-.25f)),crop=CropRect(.1f,.2f,.8f,.9f),quarterTurns=1)
            onMain {
                model.edit(color);model.finishAdjustment()
                assertFalse(model.saveRecipe("   "))
                assertTrue(model.saveRecipe("  海边日记  "))
                model.chooseExportEdge(1080)
            }
            await {committed(app)?.let{it.getJSONArray("recipes").length()==1&&it.getInt("exportEdge")==1080}==true}
            val saved=RecipeStore(app).read().recipes.single()
            assertEquals("海边日记",saved.name)
            assertEquals(color.colorRecipe(),saved.settings)
            val own=EditSettings(lookIndex=1,crop=CropRect(.22f,.1f,.9f,.77f),quarterTurns=3,straightenDegrees=7f,flipHorizontal=true)
            onMain {
                model.edit(own);model.finishAdjustment();model.applySavedRecipe(saved.id)
                assertEquals(own.withColorRecipe(color),model.state.settings)
                assertEquals(color.colorRecipe(),model.library.recipe)
            }
            await {!model.state.rendering&&model.state.displayedSettings==model.state.settings}
            lateinit var source:android.graphics.Bitmap
            lateinit var filtered:android.graphics.Bitmap
            onMain {source=model.state.source!!;filtered=model.state.filtered!!}
            val expected=runBlocking {withContext(NativeCore.dispatcher){NativeCore.render(app,source,Looks[color.lookIndex], color.grain, color.filmExposure, autoFilmExposure = color.autoFilmExposure, autoFilmExposureMethod = color.autoFilmExposureMethod)}}
            assertTrue("applying saved recipe must also rerender its film",expected.sameAs(filtered))
            expected.recycle()
            onMain {
                model.undo();assertEquals(own,model.state.settings)
                model.redo();assertEquals(own.withColorRecipe(color),model.state.settings)
                model.renameRecipe(saved.id,"清透海边")
            }
            await {committed(app)?.getJSONArray("recipes")?.optJSONObject(0)?.optString("name")=="清透海边"}
            onMain {restored=EditorModel(app);models.add(restored)}
            await {!restored.recipesLoading}
            onMain {
                assertEquals("清透海边",restored.savedRecipes.single().name)
                assertEquals(1080,restored.exportEdge)
                restored.deleteRecipe(saved.id)
            }
            await {committed(app)?.getJSONArray("recipes")?.length()==0}
            assertEquals(1080,RecipeStore(app).read().exportEdge)
        } finally {
            onMain {models.forEach{it.viewModelScope.cancel()}}
            app.cleanup()
        }
    }
}
