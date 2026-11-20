package studio.pianyu.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(model: EditorModel, pickPhoto: () -> Unit, exportBatch: () -> Unit, about: () -> Unit, pickMediaFile: (() -> Unit)?, openDemo: () -> Unit) {
    val library=model.library
    val busy=library.batch!=null||library.importing||library.restoring
    var confirmRemove by remember { mutableStateOf(false) }
    var showImportChooser by remember { mutableStateOf(false) }
    var showRecipeChooser by remember { mutableStateOf(false) }
    val openImport = { if (pickMediaFile == null) pickPhoto() else showImportChooser = true }
    BackHandler(enabled = library.selectionMode && !busy) { model.clearSelection() }
    if(confirmRemove) AlertDialog(onDismissRequest={confirmRemove=false},title={Text("移出 ${library.selected.size} 张照片？")},
        text={Text("只清除片屿中的照片记录与编辑设置，手机相册里的原文件会保留。")},
        confirmButton={TextButton(onClick={model.removeSelectedFromLibrary();confirmRemove=false}){Text("移出照片库")}},
        dismissButton={TextButton(onClick={confirmRemove=false}){Text("取消")}})
    if(showImportChooser && pickMediaFile != null) AlertDialog(onDismissRequest={showImportChooser=false},title={Text("选择导入来源")},
        text={
            Column {
                TextButton(onClick={showImportChooser=false;pickPhoto()},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("从相册选")
                        Text("可一次选择多张照片",fontSize=12.sp,color=WarmWhite.copy(alpha=.65f))
                    }
                }
                TextButton(onClick={showImportChooser=false;pickMediaFile()},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("从文件选")
                        Text("照片、RAW（DNG）、视频和 JPEG 实况原件",fontSize=12.sp,color=WarmWhite.copy(alpha=.65f))
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={showImportChooser=false}){Text("取消")}})
    if (showRecipeChooser) ModalBottomSheet(onDismissRequest={showRecipeChooser=false},
        contentWindowInsets = { BottomSheetDefaults.windowInsets.exclude(WindowInsets.navigationBars) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp, vertical=8.dp)) {
            Text("选择套用配方", style=MaterialTheme.typography.titleMedium)
            Text("整套套用会覆盖全部调色，保留每张照片自己的裁切与旋转", fontSize=12.sp,
                color=WarmWhite.copy(alpha=.65f), modifier=Modifier.padding(top=6.dp, bottom=8.dp))
            fun apply(recipe: EditSettings) {
                model.applyRecipeToSelected(recipe)
                showRecipeChooser = false
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                var hasPreviousGroup = false
                library.recipe?.let { recent ->
                    item { Text("最近复制", style = MaterialTheme.typography.labelLarge, color = Brass,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    val recentName = model.savedRecipes.firstOrNull { it.settings == recent }?.name
                        ?: CreativeRecipes.firstOrNull { it.settings == recent }?.name
                        ?: "当前调色"
                    item { BatchRecipeChoice(recentName, recent) { apply(recent) } }
                    hasPreviousGroup = true
                }
                if (model.savedRecipes.isNotEmpty()) {
                    if (hasPreviousGroup) item { HorizontalDivider(color = WarmWhite.copy(alpha = .12f)) }
                    item { Text("我的配方", style = MaterialTheme.typography.labelLarge, color = Brass,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    items(model.savedRecipes, key = { "personal-${it.id}" }) { recipe ->
                        BatchRecipeChoice(recipe.name, recipe.settings, recipe.name) { apply(recipe.settings) }
                    }
                    hasPreviousGroup = true
                }
                if (CreativeRecipes.isNotEmpty()) {
                    if (hasPreviousGroup) item { HorizontalDivider(color = WarmWhite.copy(alpha = .12f)) }
                    item { Text("官方配方", style = MaterialTheme.typography.labelLarge, color = Brass,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                    items(CreativeRecipes, key = { "official-${it.id}" }) { recipe ->
                        BatchRecipeChoice(recipe.name, recipe.settings, recipe.name) { apply(recipe.settings) }
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.brand_mark),null,Modifier.size(46.dp).graphicsLayer(scaleX=1.4f,scaleY=1.4f))
            Column(Modifier.weight(1f).padding(start=8.dp)) {
                Text("片屿",fontSize=24.sp, color=WarmWhite, fontFamily=Serif)
                Text("照片与片刻，留成一座屿",fontSize=11.sp,color=Brass)
            }
            TextButton(onClick=about) {Text("关于")}
            if(library.entries.isNotEmpty()) Button(onClick=openImport,enabled=!busy) {Text("导入")}
        }
        if(library.restoring||library.importing) {
            library.importProgress?.let { progress ->
                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                    Text("已读取 ${progress.completed} / ${progress.total}",color=WarmWhite,fontSize=12.sp)
                    LinearProgressIndicator(progress={if(progress.total==0) 1f else progress.completed.toFloat()/progress.total},modifier=Modifier.fillMaxWidth())
                }
            } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if(library.entries.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text("让日常，有一点胶片的味道。",fontSize=22.sp,color=WarmWhite,fontFamily=Serif)
                Spacer(Modifier.height(16.dp))
                Text("支持照片、RAW（DNG）、视频和 JPEG 实况。",color=WarmWhite.copy(alpha=.65f),lineHeight=25.sp)
                Spacer(Modifier.height(28.dp))
                Button(onClick=openImport,enabled=!busy) {Text("导入")}
                TextButton(onClick=openDemo,enabled=!busy) {Text("试用示例")}
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(library.selected.isEmpty()) "照片库 · ${library.entries.size}" else "已选 ${library.selected.size} 张",Modifier.weight(1f),color=WarmWhite)
                if (library.selectionMode) {
                    TextButton(onClick = model::clearSelection, enabled = !busy) { Text("取消选择") }
                    TextButton(onClick = model::selectAll, enabled = !busy) { Text("全选") }
                } else {
                    TextButton(onClick = openDemo, enabled = !busy) { Text("打开示例") }
                    TextButton(onClick = model::enterSelectionMode, enabled = !busy) { Text("选择") }
                }
            }
            Text(if(!library.selectionMode) "点击编辑 · 长按多选" else if(library.selected.isEmpty()) "点击照片加入选择" else "套用仅同步调色，每张构图独立保留",fontSize=12.sp,color=WarmWhite.copy(alpha=.6f),modifier=Modifier.padding(horizontal=16.dp,vertical=6.dp))
            LazyVerticalGrid(columns=GridCells.Adaptive(108.dp),modifier=Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(library.entries,key={it.id}) { photo ->
                    val thumb=library.thumbnails[photo.id]
                    LaunchedEffect(photo.id,photo.settings,thumb==null) {model.requestLibraryThumbnail(photo.id)}
                    Box(Modifier.aspectRatio(.8f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF333530))
                        .combinedClickable(enabled=!busy,onClick={if(library.selectionMode)model.toggleSelected(photo.id) else model.openPhoto(photo.id)},onLongClick={model.toggleSelected(photo.id)})) {
                        if(thumb!=null) Image(thumb.asImageBitmap(),photo.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        else if(photo.id in library.thumbnailLoading) CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.Center),strokeWidth=2.dp)
                        else Text(if(photo.id in library.thumbnailErrors)"读取失败\n点开查看" else "点开编辑",fontSize=12.sp,color=WarmWhite,
                            modifier=Modifier.align(Alignment.Center).padding(8.dp))
                        if(thumb!=null) Text(if(photo.id in library.originalThumbnails)"原图预览" else "效果预览",fontSize=10.sp,color=WarmWhite,modifier=Modifier.align(Alignment.BottomStart).background(Color(0xBB20211F)).padding(5.dp))
                        if(library.selected.isNotEmpty()) Surface(color=if(photo.id in library.selected)Brass else Color(0xAA20211F),shape=RoundedCornerShape(16.dp),modifier=Modifier.align(Alignment.TopEnd).padding(7.dp)) {Text(if(photo.id in library.selected)"✓" else "○",Modifier.padding(horizontal=7.dp,vertical=3.dp),color=if(photo.id in library.selected)Graphite else WarmWhite)}
                    }
                }
            }
            if(library.selected.isNotEmpty()&&!busy) Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={confirmRemove=true},modifier=Modifier.weight(1f)) {Text("移出")}
                OutlinedButton(onClick={showRecipeChooser=true},modifier=Modifier.weight(1f)) {Text("套用配方")}
                Button(onClick=exportBatch,modifier=Modifier.weight(1f)) {Text("保存 ${library.selected.size} 张")}
            }
        }
        library.batch?.let { batch ->
            Column(Modifier.fillMaxWidth().background(Color(0xFF30322D)).padding(16.dp)) {
                Text("正在保存 ${batch.completed} / ${batch.total} · 失败 ${batch.failed}",color=WarmWhite)
                LinearProgressIndicator(progress={batch.completed.toFloat()/batch.total},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("导出时请保持应用打开",fontSize=12.sp,modifier=Modifier.weight(1f),color=WarmWhite.copy(alpha=.6f))
                    TextButton(onClick=model::stopBatch,enabled=!batch.stopping) {Text(if(batch.stopping)"本张完成后停止" else "停止")}
                }
            }
        }
        library.lastBatch?.let { result ->
            Column(Modifier.fillMaxWidth().background(Color(0xFF30322D)).padding(16.dp)) {
                Text("${if(result.canceled) "已停止" else "批量保存完成"}：已保存 ${result.saved.size} 张，失败 ${result.failed} 张",color=WarmWhite)
                if(result.failures.isNotEmpty()) Column(Modifier.fillMaxWidth().heightIn(max=120.dp).verticalScroll(rememberScrollState())) {
                    result.failures.forEach { failure ->
                        Text("${failure.name}：${failure.reason}",fontSize=12.sp,color=WarmWhite.copy(alpha=.8f),modifier=Modifier.padding(top=4.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    if(result.failures.isNotEmpty()) TextButton(onClick=model::retryFailedBatch,enabled=!busy) {Text("仅重试失败")}
                    TextButton(onClick=model::dismissBatchResult) {Text("关闭")}
                }
            }
        }
    }
}

@Composable
private fun BatchRecipeChoice(
    title: String, recipe: EditSettings, accessibilityName: String = title, apply: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title)
            val look = Looks.getOrNull(recipe.lookIndex)?.name.orEmpty()
            val strength = (recipe.displayedStrength * 100).roundToInt()
            Text("$look · 强度 $strength%", fontSize = 12.sp, color = WarmWhite.copy(alpha = .65f))
        }
        TextButton(onClick = apply, modifier = Modifier.semantics {
            contentDescription = "整套套用$accessibilityName"
        }) {
            Text("套用", color = Brass)
        }
    }
}
