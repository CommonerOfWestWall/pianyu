package studio.pianyu.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
fun LibraryScreen(model: EditorModel, pickPhoto: () -> Unit, exportBatch: () -> Unit, about: () -> Unit) {
    val library=model.library
    val busy=library.batch!=null
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.brand_mark),null,Modifier.size(46.dp).graphicsLayer(scaleX=1.4f,scaleY=1.4f))
            Column(Modifier.weight(1f).padding(start=8.dp)) {
                Text("片屿",fontSize=24.sp, color=WarmWhite, fontFamily=Serif)
                Text("照片与片刻，留成一座屿",fontSize=11.sp,color=Brass)
            }
            TextButton(onClick=about) {Text("关于")}
            Button(onClick=pickPhoto,enabled=!busy&&!library.importing&&!library.restoring) {Text("导入")}
        }
        if(library.restoring||library.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(library.entries.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Text("让日常，有一点胶片的味道。",fontSize=22.sp,color=WarmWhite,fontFamily=Serif)
                Spacer(Modifier.height(16.dp))
                Text("一次选多张，在照片库里慢慢挑。\n调好一张，复制配方，批量保存。",color=WarmWhite.copy(alpha=.65f),lineHeight=25.sp)
                Spacer(Modifier.height(28.dp))
                Button(onClick=pickPhoto,enabled=!library.restoring) {Text("从相册导入 · 支持多选")}
                TextButton(onClick=model::loadDemo,enabled=!library.restoring) {Text("试用示例")}
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(library.selected.isEmpty()) "照片库 · ${library.entries.size}" else "已选 ${library.selected.size} 张",Modifier.weight(1f),color=WarmWhite)
                if(library.selected.isNotEmpty()) TextButton(onClick=model::clearSelection,enabled=!busy) {Text("取消选择")}
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
                        else CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.Center),strokeWidth=2.dp)
                        if(photo.settings!=EditSettings()) Text("已调色",fontSize=10.sp,color=WarmWhite,modifier=Modifier.align(Alignment.BottomStart).background(Color(0xBB20211F)).padding(5.dp))
                        if(library.selected.isNotEmpty()) Surface(color=if(photo.id in library.selected)Brass else Color(0xAA20211F),shape=RoundedCornerShape(16.dp),modifier=Modifier.align(Alignment.TopEnd).padding(7.dp)) {Text(if(photo.id in library.selected)"✓" else "○",Modifier.padding(horizontal=7.dp,vertical=3.dp),color=if(photo.id in library.selected)Graphite else WarmWhite)}
                    }
                }
            }
            if(library.selected.isNotEmpty()&&!busy) Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
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
    }
}
