package studio.pianyu.app

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

@Composable
fun CropEditor(state: EditorState,onCancel:()->Unit,onDone:(EditSettings)->Unit) {
    var draft by remember {mutableStateOf(state.settings)}
    var base by remember {mutableStateOf<Bitmap?>(null)}
    var oriented by remember {mutableStateOf<Bitmap?>(null)}
    var busy by remember {mutableStateOf(true)}
    LaunchedEffect(Unit) {
        base=withContext(PhotoIO.previewDispatcher) {PhotoIO.finish(state.source!!,state.filtered?:state.source,state.settings.colorRecipe())}
    }
    LaunchedEffect(base) {
        val photo=base?:return@LaunchedEffect
        snapshotFlow { draft.copy(crop=CropRect(),cropAspect=0f) }.conflate().collect { geometry ->
            busy=true
            oriented=withContext(PhotoIO.previewDispatcher) {PhotoGeometry.orient(photo,geometry)}
            busy=geometry!=draft.copy(crop=CropRect(),cropAspect=0f)
        }
    }
    Dialog(onDismissRequest=onCancel,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler {onCancel()}
        Surface(Modifier.fillMaxSize(),color=Graphite) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().height(60.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onCancel){Text("取消")}
                    Text("构图",Modifier.weight(1f),fontFamily=Serif,fontSize=24.sp)
                    TextButton(onClick={draft=draft.copy(crop=CropRect(),cropAspect=0f,quarterTurns=0,straightenDegrees=0f,flipHorizontal=false,flipVertical=false)}){Text("重置")}
                    TextButton(onClick={onDone(draft)},enabled=!busy){Text("完成")}
                }
                val image=oriented
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF12130F)),contentAlignment=Alignment.Center) {
                    if(image!=null) CropCanvas(image,draft.crop,draft.cropAspect){draft=draft.copy(crop=it)}
                    if(busy) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
                }
                Text("拖动四角裁切 · 拖动框内移动",fontSize=12.sp,color=WarmWhite.copy(alpha=.7f),modifier=Modifier.padding(horizontal=16.dp,vertical=10.dp))
                LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    items(listOf("自由" to 0f,"原图" to -1f,"1:1" to 1f,"4:5" to .8f,"3:4" to .75f,"2:3" to 2f/3,"3:2" to 1.5f,"9:16" to 9f/16,"16:9" to 16f/9)) {(name,ratio)->
                        FilterChip(enabled=oriented!=null&&!busy,selected=if(ratio==-1f)false else abs(draft.cropAspect-ratio)<.001f,onClick={
                            val bmp=oriented?:return@FilterChip
                            val aspect=if(ratio==-1f)bmp.width.toFloat()/bmp.height else ratio
                            draft=draft.copy(cropAspect=aspect,crop=if(aspect<=0f)draft.crop else PhotoGeometry.fitAspect(bmp.width,bmp.height,aspect))
                        },label={Text(name)})
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton(onClick={draft=draft.copy(quarterTurns=(draft.quarterTurns+3)%4,crop=CropRect(),cropAspect=0f)}){Text("左转 90°")}
                    TextButton(onClick={draft=draft.copy(quarterTurns=(draft.quarterTurns+1)%4,crop=CropRect(),cropAspect=0f)}){Text("右转 90°")}
                    TextButton(onClick={draft=draft.copy(flipHorizontal=!draft.flipHorizontal)},colors=ButtonDefaults.textButtonColors(contentColor=if(draft.flipHorizontal)Brass else WarmWhite)){Text("水平镜像")}
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={draft=draft.copy(flipVertical=!draft.flipVertical)},colors=ButtonDefaults.textButtonColors(contentColor=if(draft.flipVertical)Brass else WarmWhite)){Text("垂直镜像")}
                    TextButton(onClick={draft=draft.copy(straightenDegrees=0f)}){Text("校平")}
                    Slider(value=draft.straightenDegrees,onValueChange={draft=draft.copy(straightenDegrees=it)},valueRange=-20f..20f,modifier=Modifier.weight(1f).semantics{contentDescription="校平角度"})
                    Text("%.1f°".format(draft.straightenDegrees),fontSize=12.sp,modifier=Modifier.width(46.dp))
                }
                Text("校平自动放大避开黑边；旋转90°会重置裁切框。",fontSize=11.sp,color=WarmWhite.copy(alpha=.5f),modifier=Modifier.padding(horizontal=16.dp,vertical=10.dp))
            }
        }
    }
}

@Composable
private fun CropCanvas(bitmap: Bitmap,crop:CropRect,aspect:Float,onCrop:(CropRect)->Unit) {
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    val latest by rememberUpdatedState(crop)
    val latestCallback by rememberUpdatedState(onCrop)
    val imageRect=remember(viewport,bitmap) {
        val scale=min(viewport.width.toFloat()/bitmap.width,viewport.height.toFloat()/bitmap.height)
        val w=bitmap.width*scale;val h=bitmap.height*scale
        Rect((viewport.width-w)/2,(viewport.height-h)/2,(viewport.width+w)/2,(viewport.height+h)/2)
    }
    Box(Modifier.fillMaxSize().padding(16.dp).onSizeChanged{viewport=it}) {
        Image(bitmap.asImageBitmap(),"裁切工作区",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        Canvas(Modifier.fillMaxSize().semantics{contentDescription="自由裁切框，拖动四角或框内移动"}.pointerInput(imageRect,aspect) {
            var handle=-1
            var start=CropRect()
            var initial=Offset.Zero
            detectDragGestures(onDragStart={point->
                start=latest;initial=point
                val corners=listOf(Offset(start.left,start.top),Offset(start.right,start.top),Offset(start.right,start.bottom),Offset(start.left,start.bottom))
                val distances=corners.map {c->(point-Offset(imageRect.left+c.x*imageRect.width,imageRect.top+c.y*imageRect.height)).getDistance()}
                val closest=distances.indices.minByOrNull{distances[it]}?:0
                handle=if(distances[closest]<42.dp.toPx())closest else if(point.x>=imageRect.left+start.left*imageRect.width&&point.x<=imageRect.left+start.right*imageRect.width&&point.y>=imageRect.top+start.top*imageRect.height&&point.y<=imageRect.top+start.bottom*imageRect.height)4 else -1
            },onDrag={change,_->
                if(handle>=0&&imageRect.width>0&&imageRect.height>0) {
                    change.consume()
                    val dx=(change.position.x-initial.x)/imageRect.width
                    val dy=(change.position.y-initial.y)/imageRect.height
                    latestCallback(dragCrop(start,handle,dx,dy,if(aspect>0)aspect*bitmap.height/bitmap.width else 0f))
                }
            })
        }) {
            val r=Rect(imageRect.left+crop.left*imageRect.width,imageRect.top+crop.top*imageRect.height,imageRect.left+crop.right*imageRect.width,imageRect.top+crop.bottom*imageRect.height)
            val shade=Color.Black.copy(alpha=.58f)
            drawRect(shade,Offset(imageRect.left,imageRect.top),Size(imageRect.width,(r.top-imageRect.top).coerceAtLeast(0f)))
            drawRect(shade,Offset(imageRect.left,r.bottom),Size(imageRect.width,(imageRect.bottom-r.bottom).coerceAtLeast(0f)))
            drawRect(shade,Offset(imageRect.left,r.top),Size((r.left-imageRect.left).coerceAtLeast(0f),r.height))
            drawRect(shade,Offset(r.right,r.top),Size((imageRect.right-r.right).coerceAtLeast(0f),r.height))
            drawRect(WarmWhite,r.topLeft,r.size,style=Stroke(1.dp.toPx()))
            for(i in 1..2) {
                drawLine(WarmWhite.copy(alpha=.4f),Offset(r.left+r.width*i/3,r.top),Offset(r.left+r.width*i/3,r.bottom),1.dp.toPx())
                drawLine(WarmWhite.copy(alpha=.4f),Offset(r.left,r.top+r.height*i/3),Offset(r.right,r.top+r.height*i/3),1.dp.toPx())
            }
            listOf(r.topLeft,r.topRight,r.bottomRight,r.bottomLeft).forEach{drawCircle(Brass,5.dp.toPx(),it)}
        }
    }
}

internal fun dragCrop(start:CropRect,handle:Int,dx:Float,dy:Float,ratio:Float):CropRect {
    val w=start.right-start.left;val h=start.bottom-start.top
    if(handle==4) {
        val x=(start.left+dx).coerceIn(0f,1f-w);val y=(start.top+dy).coerceIn(0f,1f-h)
        return CropRect(x,y,x+w,y+h)
    }
    val left=handle==0||handle==3;val top=handle==0||handle==1
    val anchorX=if(left)start.right else start.left;val anchorY=if(top)start.bottom else start.top
    var newW=(w+if(left)-dx else dx).coerceAtLeast(.06f)
    var newH=(h+if(top)-dy else dy).coerceAtLeast(.06f)
    if(ratio>0) {
        if(abs(dx)>abs(dy*ratio))newH=newW/ratio else newW=newH*ratio
        val maxW=if(left)anchorX else 1f-anchorX;val maxH=if(top)anchorY else 1f-anchorY
        val scale=min(1f,min(maxW/newW,maxH/newH));newW*=scale;newH*=scale
    } else {
        newW=newW.coerceAtMost(if(left)anchorX else 1f-anchorX)
        newH=newH.coerceAtMost(if(top)anchorY else 1f-anchorY)
    }
    return CropRect(if(left)anchorX-newW else anchorX,if(top)anchorY-newH else anchorY,
        if(left)anchorX else anchorX+newW,if(top)anchorY else anchorY+newH).normalized()
}

