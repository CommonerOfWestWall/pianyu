package studio.pianyu.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryScreen(model: EditorModel, pickPhoto: () -> Unit, exportBatch: () -> Unit, about: () -> Unit, pickMediaFile: () -> Unit) {
    val library=model.library
    val busy=library.batch!=null||library.importing||library.restoring
    var confirmRemove by remember { mutableStateOf(false) }
    if(confirmRemove) AlertDialog(onDismissRequest={confirmRemove=false},title={Text("移出 ${library.selected.size} 张照片？")},
        text={Text("只清除片屿中的照片记录与编辑设置，手机相册里的原文件会保留。")},
        confirmButton={TextButton(onClick={model.removeSelectedFromLibrary();confirmRemove=false}){Text("移出照片库")}},
        dismissButton={TextButton(onClick={confirmRemove=false}){Text("取消")}})
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.brand_mark),null,Modifier.size(46.dp).graphicsLayer(scaleX=1.4f,scaleY=1.4f))
            Column(Modifier.weight(1f).padding(start=8.dp)) {
                Text("片屿",fontSize=24.sp, color=WarmWhite, fontFamily=Serif)
                Text("照片与片刻，留成一座屿",fontSize=11.sp,color=Brass)
            }
            TextButton(onClick=about) {Text("关于")}
            if(library.entries.isNotEmpty()) Button(onClick=pickPhoto,enabled=!busy) {Text("导入")}
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
                Text("支持从相册一次导入多张照片。",color=WarmWhite.copy(alpha=.65f),lineHeight=25.sp)
                Spacer(Modifier.height(28.dp))
                Button(onClick=pickPhoto,enabled=!busy) {Text("从相册导入")}
                TextButton(onClick=pickMediaFile,enabled=!busy) {Text("导入 RAW、视频或实况")}
                TextButton(onClick=model::loadDemo,enabled=!busy) {Text("试用示例")}
            }
        } else {
            if (library.selected.isEmpty()) TextButton(onClick = pickMediaFile, enabled = !busy,
                modifier = Modifier.padding(horizontal = 12.dp)) { Text("导入 RAW、视频或实况") }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(library.selected.isEmpty()) "照片库 · ${library.entries.size}" else "已选 ${library.selected.size} 张",Modifier.weight(1f),color=WarmWhite)
                if(library.selected.isNotEmpty()) TextButton(onClick=model::clearSelection,enabled=!busy) {Text("取消选择")}
                if(library.selected.isEmpty()) TextButton(onClick=model::loadDemo,enabled=!busy&&!library.importing) {Text("打开示例")}
                TextButton(onClick=model::selectAll,enabled=!busy) {Text("全选")}
            }
            Text(if(library.selected.isEmpty()) "点击编辑 · 长按多选" else "套用仅同步调色，每张构图独立保留",fontSize=12.sp,color=WarmWhite.copy(alpha=.6f),modifier=Modifier.padding(horizontal=16.dp,vertical=6.dp))
            LazyVerticalGrid(columns=GridCells.Adaptive(108.dp),modifier=Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(library.entries,key={it.id}) { photo ->
                    val thumb=library.thumbnails[photo.id]
                    LaunchedEffect(photo.id,photo.settings,thumb==null) {model.requestLibraryThumbnail(photo.id)}
                    Box(Modifier.aspectRatio(.8f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF333530))
                        .combinedClickable(enabled=!busy,onClick={if(library.selected.isNotEmpty())model.toggleSelected(photo.id) else model.openPhoto(photo.id)},onLongClick={model.toggleSelected(photo.id)})) {
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
                OutlinedButton(onClick=model::applyRecipeToSelected,enabled=library.recipe!=null,modifier=Modifier.weight(1f)) {Text("套用配方")}
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
