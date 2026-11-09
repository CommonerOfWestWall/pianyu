package studio.pianyu.app

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val WarmWhite=Color(0xFFF4EBDD)
val Brass=Color(0xFFC7A269)
val Graphite=Color(0xFF20211F)
val Serif=FontFamily(Font(R.font.editor_serif))

@Composable
fun EditorScreen(model: EditorModel, pickPhoto: () -> Unit) {
    var export by remember {mutableStateOf(false)}
    var crop by remember {mutableStateOf(false)}
    var about by remember {mutableStateOf(false)}
    var licenses by remember {mutableStateOf(false)}
    var info by remember {mutableStateOf<Look?>(null)}
    val snackbar=remember {SnackbarHostState()}
    val state=model.state
    val activity=LocalActivity.current
    DisposableEffect(model.library.batch!=null,state.exporting) {
        if(model.library.batch!=null||state.exporting) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    LaunchedEffect(state.notice,state.error) {
        (state.error?:state.notice)?.let {snackbar.showSnackbar(it);model.clearNotice();model.clearError()}
    }
    BackHandler(enabled=model.library.editorId!=null&&!crop&&!export) {model.backToLibrary()}
    Surface(Modifier.fillMaxSize(),color=Graphite) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if(model.library.editorId==null) LibraryScreen(model,pickPhoto,{export=true},{about=true})
            else LoadedEditor(model,{export=true},{crop=true},{info=it})
            SnackbarHost(snackbar,modifier=Modifier.align(Alignment.BottomCenter).padding(
                start=12.dp,end=12.dp,bottom=when {
                    model.library.editorId!=null->68.dp
                    model.library.selected.isNotEmpty()->80.dp
                    else->12.dp
                }))
        }
    }
    if(crop) CropEditor(model.state,onCancel={crop=false},onDone={model.setGeometry(it);crop=false})
    if(export) ExportDialog(model,onClose={export=false})
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("片屿 · 把片刻留成岛")},text={Text("片，是照片，也是生活里的片刻。屿，是值得停留的一小块日常。岛岸、日光与一片倒影，是片屿的标记。\n\n先选好看的味道，再微调自己的照片。\n\n0.3.1 · 本地处理，不上传照片。代码 GPL-3.0-or-later。\n\n胶片核心基于 SpektraLab；材料 profiles / LUT：spektrafilm by Andrea Volpato，CC BY-SA 4.0。\ngithub.com/andreavolpato/spektrafilm\n\n示例为 AI 生成的原创静物，不是真实胶片定标照。",modifier=Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()))},dismissButton={TextButton(onClick={about=false;licenses=true}){Text("开源许可")}},confirmButton={TextButton(onClick={about=false}){Text("知道了")}})
    if(licenses) LicenseDialog {licenses=false}
    info?.let {look-> AlertDialog(onDismissRequest={info=null},title={Text(look.name)},text={Column {Text("参考 · ${look.reference}",color=Brass);Spacer(Modifier.height(14.dp));Text(look.description);Spacer(Modifier.height(14.dp));Text("这是材料模型的风格化配方，视觉还会受到原图、显影、相纸和扫描影响。参考名称说明灵感与来源，不代表真实胶片的唯一颜色。",fontSize=12.sp)}},confirmButton={TextButton(onClick={info=null}){Text("明白了")}}) }
}

@Composable
private fun LoadedEditor(model: EditorModel, export: ()->Unit, crop: ()->Unit, info:(Look)->Unit) {
    val state=model.state
    var tab by remember {mutableIntStateOf(0)}
    var zoom by remember(model.library.editorId,state.settings.crop,state.settings.quarterTurns) {mutableFloatStateOf(1f)}
    var panX by remember(model.library.editorId) {mutableFloatStateOf(0f)}
    var panY by remember(model.library.editorId) {mutableFloatStateOf(0f)}
    var original by remember {mutableStateOf(false)}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var compare by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(state.source,state.settings.crop,state.settings.quarterTurns,state.settings.straightenDegrees,state.settings.flipHorizontal,state.settings.flipVertical) {
        val source=state.source
        compare=if(source!=null)withContext(Dispatchers.Default){PhotoGeometry.apply(source,state.settings)}else null
        zoom=1f;panX=0f;panY=0f
    }
    val ready=state.source!=null&&!state.loading
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=model::backToLibrary){Text("‹ 照片库")}
            Text("片屿",Modifier.weight(1f),fontFamily=Serif,fontSize=21.sp,color=WarmWhite)
            Button(onClick=export,enabled=ready&&!state.exporting){Text(if(state.exporting)"保存中" else "导出")}
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF151613)).clipToBounds().onSizeChanged{viewport=it}
            .pointerInput(Unit) {detectTransformGestures {_,pan,scale,_->
                zoom=(zoom*scale).coerceIn(1f,5f)
                panX=(panX+pan.x).coerceIn(-viewport.width*(zoom-1f)/2,viewport.width*(zoom-1f)/2)
                panY=(panY+pan.y).coerceIn(-viewport.height*(zoom-1f)/2,viewport.height*(zoom-1f)/2)
            }}.pointerInput(Unit){detectTapGestures(onLongPress={original=true},onPress={tryAwaitRelease();original=false})},contentAlignment=Alignment.Center) {
            val bitmap=if(original)compare?:state.source else state.displayed?:state.source
            bitmap?.let {Image(it.asImageBitmap(),"照片预览",Modifier.fillMaxSize().graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=panX,translationY=panY),contentScale=ContentScale.Fit)}
            if(state.loading) CircularProgressIndicator(Modifier.size(28.dp))
            if(state.rendering) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
            Text(if(original)"原图 · 未经调色" else if(state.rendering)"正在冲洗风格…" else "长按对比 · 双指放大",fontSize=11.sp,color=WarmWhite.copy(alpha=.75f),modifier=Modifier.align(Alignment.BottomStart).background(Color(0x6620211F)).padding(10.dp))
            if(zoom>1.05f) TextButton(onClick={zoom=1f;panX=0f;panY=0f},modifier=Modifier.align(Alignment.BottomEnd).background(Color(0xCC20211F),RoundedCornerShape(20.dp))) {Text("恢复全图")}
        }
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=model::undo,enabled=state.canUndo){Text("撤销")}
            TextButton(onClick=model::redo,enabled=state.canRedo){Text("重做")}
            Spacer(Modifier.weight(1f))
            TextButton(onClick={original=!original},enabled=ready){Text(if(original)"返回效果" else "对比原图")}
            TextButton(onClick=model::copyRecipe,enabled=ready&&!state.rendering){Text("复制配方")}
        }
        if(tab==0) {
            val look=Looks[state.settings.lookIndex]
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text(look.name,fontFamily=Serif,fontSize=24.sp);Text("灵感 · ${look.reference}",fontSize=11.sp,color=Brass)}
                TextButton(onClick={info(look)}){Text("风格说明")}
            }
            LazyRow(contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                itemsIndexed(Looks,key={_,look->look.id}) {index,look->
                    LaunchedEffect(model.library.editorId,state.rendering,index) {if(!state.rendering)model.requestLookThumbnail(index)}
                    Column(Modifier.width(70.dp).clickable(enabled=ready){model.chooseLook(index)},horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(68.dp).border(if(index==state.settings.lookIndex)2.dp else 0.dp,if(index==state.settings.lookIndex)Brass else Color.Transparent,RoundedCornerShape(8.dp)).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF353730)),contentAlignment=Alignment.Center) {
                            state.thumbnails[look.id]?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}?:Text("···",color=Brass)
                        }
                        Text(look.name,fontSize=11.sp,color=if(index==state.settings.lookIndex)Brass else WarmWhite,maxLines=1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("强度",fontSize=13.sp)
                Slider(state.settings.strength,model::updateStrength,onValueChangeFinished=model::finishAdjustment,enabled=ready,modifier=Modifier.weight(1f).padding(horizontal=12.dp).semantics{contentDescription="风格强度"})
                Text((state.settings.strength*100).toInt().toString(),fontSize=13.sp)
            }
        } else if(tab==1) AdjustmentPanel(model)
        else RecipePanel(model)
        HorizontalDivider(color=WarmWhite.copy(alpha=.12f))
        Row(Modifier.fillMaxWidth().height(56.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={model.finishAdjustment();tab=0}){Text("风格",color=if(tab==0)Brass else WarmWhite)}
            TextButton(onClick={model.finishAdjustment();tab=1},enabled=ready){Text("调色",color=if(tab==1)Brass else WarmWhite)}
            TextButton(onClick={model.finishAdjustment();tab=2},enabled=ready){Text("配方",color=if(tab==2)Brass else WarmWhite)}
            TextButton(onClick={model.finishAdjustment();crop()},enabled=ready&&!state.rendering){Text("裁切 / 旋转",color=WarmWhite)}
        }
    }
}

@Composable
private fun ExportDialog(model: EditorModel,onClose:()->Unit) {
    val context=LocalContext.current
    val batch=model.library.editorId==null
    AlertDialog(onDismissRequest=onClose,title={Text(if(batch)"批量保存 ${model.library.selected.size} 张" else "保存这一帧")},text={Column {
        Text("JPEG · sRGB · 不覆盖原片",color=Brass)
        Spacer(Modifier.height(12.dp))
        listOf(1080 to "轻巧 · 长边 1080",2048 to "清晰 · 长边 2048").forEach{(edge,label)->
            Row(Modifier.fillMaxWidth().clickable{model.chooseExportEdge(edge)},verticalAlignment=Alignment.CenterVertically){RadioButton(selected=model.exportEdge==edge,onClick={model.chooseExportEdge(edge)});Text(label)}
        }
        Text("裁切后尺寸会缩小，低分辨率照片不会放大。",fontSize=12.sp)
    }},confirmButton={TextButton(onClick={if(batch)model.exportSelected() else model.export(false){};onClose()}){Text("保存到相册")}},dismissButton={Row {
        if(!batch)TextButton(onClick={model.export(true){uri->context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="image/jpeg";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"分享片屿照片"))};onClose()}){Text("分享")}
        TextButton(onClick=onClose){Text("取消")}
    }})
}

