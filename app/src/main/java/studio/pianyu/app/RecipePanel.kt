package studio.pianyu.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class RecipeSection { OFFICIAL, PERSONAL }

@Composable
fun RecipePanel(model: EditorModel, modifier: Modifier = Modifier) {
    var naming by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<NamedRecipe?>(null) }
    var deleting by remember { mutableStateOf<NamedRecipe?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    var selectedSection by rememberSaveable { mutableStateOf<RecipeSection?>(null) }
    val section = selectedSection ?: if (model.savedRecipes.isNotEmpty()) RecipeSection.PERSONAL else RecipeSection.OFFICIAL
    val officialScroll = rememberLazyListState()
    val personalScroll = rememberLazyListState()
    val scrollState = when (section) {
        RecipeSection.OFFICIAL -> officialScroll
        RecipeSection.PERSONAL -> personalScroll
    }
    val scope = rememberCoroutineScope()
    val ready = model.state.source != null && !model.recipesLoading && !model.state.loading && !model.state.exporting
    Column(modifier.fillMaxWidth().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
                LookChip("我的配方", section == RecipeSection.PERSONAL, true) {
                    selectedSection = RecipeSection.PERSONAL
                }
                LookChip("官方配方", section == RecipeSection.OFFICIAL, true) {
                    selectedSection = RecipeSection.OFFICIAL
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(0.dp), verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=model::copyRecipe, enabled=ready&&!model.state.rendering,
                    modifier=Modifier.semantics { contentDescription = "复制当前配方" },
                    contentPadding=PaddingValues(horizontal=6.dp)) { Text("复制") }
                TextButton(onClick={naming=true}, enabled=ready,
                    modifier=Modifier.semantics { contentDescription = "保存当前调色为配方" },
                    contentPadding=PaddingValues(horizontal=6.dp)) { Text("保存") }
            }
        }
        if (model.recipesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("整套套用会替换当前调色，保留构图。", fontSize = 12.sp,
            color = WarmWhite.copy(alpha = .7f), modifier = Modifier.padding(bottom = 6.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = scrollState) {
        if (section == RecipeSection.OFFICIAL) {
        val recipes = CreativeRecipes
        items(recipes, key={"builtin-${it.id}"}) { recipe ->
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical=4.dp)) {
                    Text(recipe.name)
                    Text(recipe.description, fontSize=11.sp, color=WarmWhite.copy(alpha=.65f))
                }
                TextButton(onClick={model.applyCreativeRecipe(recipe.id)}, enabled=ready) {
                    Text("整套套用", Modifier.semantics { contentDescription="整套套用${recipe.name}" })
                }
            }
        }
        } else if (!model.recipesLoading && model.savedRecipes.isEmpty()) item {
            Text("把喜欢的调色留下来", color=WarmWhite, modifier=Modifier.padding(top=16.dp))
            Text("为当前风格与调整起个名字，下次一键使用。\n配方不包含裁切和旋转，也能用于批量套色。",
                fontSize=12.sp, color=WarmWhite.copy(alpha=.65f), modifier=Modifier.padding(top=8.dp))
        } else if (!model.recipesLoading) items(model.savedRecipes, key={it.id}) { recipe ->
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical=8.dp)) {
                        Text(recipe.name, maxLines=1, overflow=TextOverflow.Ellipsis)
                        Text(Looks[recipe.settings.lookIndex].name+" · 保留独立构图", fontSize=11.sp, color=WarmWhite.copy(alpha=.6f))
                    }
                    TextButton(onClick={model.applySavedRecipe(recipe.id)}, enabled=ready) {
                        Text("整套套用", Modifier.semantics { contentDescription="整套套用${recipe.name}" })
                    }
                    Box {
                        TextButton(onClick={menu=recipe.id}, enabled=ready, modifier=Modifier.width(48.dp)) {
                            Text("···", Modifier.semantics { contentDescription="管理配方${recipe.name}" })
                        }
                        DropdownMenu(expanded=menu==recipe.id,onDismissRequest={menu=null}) {
                            DropdownMenuItem(text={Text("改名")},onClick={menu=null;rename=recipe})
                            DropdownMenuItem(text={Text("删除")},onClick={menu=null;deleting=recipe})
                        }
                    }
                }
                HorizontalDivider(color=WarmWhite.copy(alpha=.1f))
        }
        }
    }
    if (naming) RecipeNameDialog("保存配方", "", {naming=false}) {
        if(model.saveRecipe(it)) {
            naming = false
            selectedSection = RecipeSection.PERSONAL
            scope.launch { personalScroll.scrollToItem(0) }
        }
    }
    rename?.let { recipe -> RecipeNameDialog("配方改名", recipe.name, {rename=null}) {model.renameRecipe(recipe.id,it);rename=null} }
    deleting?.let { recipe -> AlertDialog(onDismissRequest={deleting=null},title={Text("删除配方？")},
        text={Text("删除「${recipe.name}」不会改变已编辑的照片。")},
        confirmButton={TextButton(onClick={model.deleteRecipe(recipe.id);deleting=null}){Text("确认删除")}},
        dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}) }
}

@Composable
private fun RecipeNameDialog(title: String, initial: String, close: ()->Unit, save: (String)->Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=close,title={Text(title)},text={OutlinedTextField(value=name,
        onValueChange={name=it.take(40)},singleLine=true,label={Text("配方名称")},supportingText={Text("最多40字，仅保存调色，不包含构图")})},
        confirmButton={TextButton(onClick={save(name.trim())},enabled=name.isNotBlank()){Text("保存名称")}},
        dismissButton={TextButton(onClick=close){Text("取消")}})
}
