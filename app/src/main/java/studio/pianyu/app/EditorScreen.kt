package studio.pianyu.app

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val WarmWhite=Color(0xFFF4EBDD)
val Brass=Color(0xFFC7A269)
val Graphite=Color(0xFF20211F)
val Serif=FontFamily(Font(R.font.editor_serif))

private val CinematicLookOrder = listOf(
    "cinema", "daily", "soft-daylight", "night", "retro", "golden-landscape",
    "blue-hour", "muted-teal-coast", "mist-coast", "cyan-travel", "natural",
    "olive-story", "forest-deep", "landscape", "softmono"
)

// Display order is independent of the indices persisted by LibraryStore.
internal val LookCards = Looks.withIndex().sortedBy { (_, look) ->
    CinematicLookOrder.indexOf(look.id).takeIf { it >= 0 } ?: CinematicLookOrder.size
}

@Composable
fun EditorScreen(model: EditorModel, pickMediaFile: (() -> Unit)? = null, pickPhoto: () -> Unit) {
    var export by remember {mutableStateOf(false)}
    var crop by remember {mutableStateOf(false)}
    var about by remember {mutableStateOf(false)}
    var licenses by remember {mutableStateOf(false)}
    var diagnostic by remember {mutableStateOf(false)}
    var info by remember {mutableStateOf<Look?>(null)}
    val snackbar=remember {SnackbarHostState()}
    val state=model.state
    val activity=LocalActivity.current
    val context=LocalContext.current
    val version=remember(context) {context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()}
    DisposableEffect(model.library.batch!=null,state.exporting) {
        if(model.library.batch!=null||state.exporting) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    LaunchedEffect(state.notice,state.error) {
        val error=state.error; val notice=state.notice
        (error?:notice)?.let {
            val result=snackbar.showSnackbar(it,actionLabel=if(error!=null&&model.diagnosticReport!=null)"查看" else null,
                withDismissAction=error!=null,duration=if(error!=null)SnackbarDuration.Long else SnackbarDuration.Short)
            if(result==SnackbarResult.ActionPerformed) diagnostic=true
            if(model.state.notice==notice) model.clearNotice()
            if(model.state.error==error) model.clearError()
        }
    }
    BackHandler(enabled=model.library.editorId!=null&&!crop&&!export) {model.backToLibrary()}
    Surface(Modifier.fillMaxSize(),color=Graphite) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if(model.library.editorId==null) LibraryScreen(model,pickPhoto,{export=true},{about=true},pickMediaFile ?: pickPhoto)
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
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("片屿 · 把片刻留成岛")},text={Text("15 款风格可用于 JPG、HEIF、手机 DNG、视频与 JPEG 实况照片。用胶片曝光、相纸和印放曝光继续调整风格。编辑和导出均在本机完成。\n\n$version · Android 10+ · arm64\n\n代码采用 GPL-3.0-or-later。胶片核心基于 SpektraLab。材料与 LUT 来自 Andrea Volpato 的 spektrafilm，采用 CC BY-SA 4.0。\ngithub.com/andreavolpato/spektrafilm\n\n内置静物示例由 AI 生成。",modifier=Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()))},dismissButton={Column {TextButton(onClick={about=false;licenses=true}){Text("开源许可")};if(model.diagnosticReport!=null) TextButton(onClick={about=false;diagnostic=true}){Text("处理诊断")}}},confirmButton={TextButton(onClick={about=false}){Text("知道了")}})
    if(diagnostic) AlertDialog(onDismissRequest={diagnostic=false},title={Text("最近一次处理诊断")},
        text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            Text("仅保存在本机，包含设备、处理阶段与错误。不会上传照片。系统退出原因用于排查，不能单凭一项判定故障。",fontSize=12.sp)
            Spacer(Modifier.height(12.dp));Text(model.diagnosticReport?:"暂无诊断",fontSize=12.sp)
        }},dismissButton={TextButton(onClick={diagnostic=false}){Text("关闭")}},
        confirmButton={TextButton(onClick={
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                ClipData.newPlainText("片屿处理诊断",model.diagnosticReport.orEmpty()))
            diagnostic=false
        }){Text("复制诊断")}})
    if(licenses) LicenseDialog {licenses=false}
    info?.let { look ->
        AlertDialog(onDismissRequest={info=null}, title={Text(look.name)}, text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("参考 · ${look.reference}", color=Brass)
                Text(look.description)
                Text("75 是推荐强度，100 的效果更鲜明。缩略图显示默认效果，当前调整以大图为准。", fontSize=12.sp)
                Text("默认先自动测光，可在「调色 → 胶片曝光」关闭。片屿预设在胶片模型上额外调色。关闭预设并将强度设为 100，可查看完整模型效果。", fontSize=12.sp)
                TextButton(onClick={model.setLookFinish(state.settings.lookVersion==0);info=null},
                    enabled=!state.loading) {
                    Text(if(state.settings.lookVersion==0) "启用片屿预设" else "关闭片屿预设")
                }
            }
        }, confirmButton={TextButton(onClick={info=null}){Text("明白了")}})
    }
}

@Composable
private fun LoadedEditor(model: EditorModel, export: ()->Unit, crop: ()->Unit, info:(Look)->Unit) {
    val state=model.state
    var tab by remember {mutableIntStateOf(0)}
    var zoom by remember(model.library.editorId,state.settings.crop,state.settings.quarterTurns) {mutableFloatStateOf(1f)}
    var panX by remember(model.library.editorId) {mutableFloatStateOf(0f)}
    var panY by remember(model.library.editorId) {mutableFloatStateOf(0f)}
    var original by remember(model.library.editorId) {mutableStateOf(false)}
    var heldOriginal by remember {mutableStateOf(false)}
    var viewport by remember {mutableStateOf(IntSize.Zero)}
    var compare by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(state.source,state.settings.crop,state.settings.quarterTurns,state.settings.straightenDegrees,state.settings.flipHorizontal,state.settings.flipVertical,state.settings.cropAspect) {
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
            }}.pointerInput(Unit){detectTapGestures(onLongPress={heldOriginal=true},onPress={try {tryAwaitRelease()} finally {heldOriginal=false}})},contentAlignment=Alignment.Center) {
            val bitmap=if(original||heldOriginal)compare?:state.source else state.displayed?:state.source
            bitmap?.let {Image(it.asImageBitmap(),"照片预览",Modifier.fillMaxSize().graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=panX,translationY=panY),contentScale=ContentScale.Fit)}
            if(state.loading) CircularProgressIndicator(Modifier.size(28.dp))
            if(state.rendering) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
            Text(if(state.loading)"正在读取照片…" else if(state.source==null)"照片尚未打开" else if(original||heldOriginal)"原图缩略预览 · 未调色 · 长边≤960" else if(state.rendering)"正在冲洗风格…" else if(state.displayedSettings==null)"原图预览 · 风格尚未完成" else "缩略预览 · 长按对比 · 双指放大",fontSize=11.sp,color=WarmWhite.copy(alpha=.75f),modifier=Modifier.align(Alignment.BottomStart).background(Color(0x6620211F)).padding(10.dp))
            if(zoom>1.05f) TextButton(onClick={zoom=1f;panX=0f;panY=0f},modifier=Modifier.align(Alignment.BottomEnd).background(Color(0xCC20211F),RoundedCornerShape(20.dp))) {Text("恢复全图")}
        }
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=model::undo,enabled=state.canUndo){Text("撤销")}
            TextButton(onClick=model::redo,enabled=state.canRedo){Text("重做")}
            Spacer(Modifier.weight(1f))
            TextButton(onClick={original=!original},enabled=ready){Text(if(original)"返回效果" else "对比原图")}
        }
        if(tab==0) {
            val look=Looks[state.settings.lookIndex]
            val activeCard=LookCards.indexOfFirst {it.index==state.settings.lookIndex}
            val firstCard=(activeCard-1).coerceAtLeast(0)
            val lookListState=rememberLazyListState(initialFirstVisibleItemIndex=firstCard)
            LaunchedEffect(model.library.editorId,activeCard) {
                val layout=lookListState.layoutInfo
                val visible=layout.visibleItemsInfo.any {
                    it.index==activeCard && it.offset>=layout.viewportStartOffset &&
                        it.offset+it.size<=layout.viewportEndOffset
                }
                if(!visible) lookListState.scrollToItem(firstCard)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text(look.name,fontFamily=Serif,fontSize=24.sp);Text(if(state.settings.lookVersion==0)"胶片模型 · 未加片屿预设" else "材料 · ${look.reference}",fontSize=11.sp,color=Brass)}
                TextButton(onClick={info(look)}){Text("风格说明")}
            }
            LazyRow(modifier=Modifier.selectableGroup(),state=lookListState,contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                items(LookCards,key={it.value.id}) { (index, look) ->
                    LaunchedEffect(model.library.editorId,state.rendering,index) {if(!state.rendering)model.requestLookThumbnail(index)}
                    val active=index==state.settings.lookIndex
                    Column(Modifier.width(70.dp)
                        .semantics {contentDescription="选择风格${look.name}"}
                        .selectable(selected=active,enabled=ready,role=Role.RadioButton){model.chooseLook(index)},
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(68.dp).border(if(active)2.dp else 0.dp,if(active)Brass else Color.Transparent,RoundedCornerShape(8.dp)).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF353730)),contentAlignment=Alignment.Center) {
                            state.thumbnails[look.id]?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}?:Text("···",color=Brass)
                        }
                        Text(look.name,fontSize=11.sp,color=if(active)Brass else WarmWhite,maxLines=1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("强度",fontSize=13.sp)
                Slider(state.settings.displayedStrength,model::updateStrength,onValueChangeFinished=model::finishAdjustment,enabled=ready,modifier=Modifier.weight(1f).padding(horizontal=12.dp).semantics{contentDescription="风格强度"})
                ParameterValue("风格强度", state.settings.displayedStrength, 0f..1f) {
                    model.updateStrength(it); model.finishAdjustment()
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if(state.settings.lookVersion >= 2) Text("75 推荐 · 100 加强", fontSize=11.sp, color=Brass)
                }
                TextButton(onClick=model::applyRecommendedStart, enabled=ready&&!state.rendering&&!state.exporting) {
                    Text("重置此风格调色")
                }
            }
        } else if(tab==1) AdjustmentPanel(model)
        else RecipePanel(model)
        HorizontalDivider(color=WarmWhite.copy(alpha=.12f))
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).selectableGroup(), verticalAlignment=Alignment.CenterVertically) {
                listOf("风格", "调色", "配方").forEachIndexed { index, label ->
                    val selected=tab==index
                    val enabled=index==0||ready
                    Box(Modifier.weight(1f).heightIn(min=48.dp)
                        .selectable(selected=selected, enabled=enabled, role=Role.Tab) {
                            model.finishAdjustment()
                            tab=index
                        }, contentAlignment=Alignment.Center) {
                        Text(label, textAlign=TextAlign.Center,
                            color=if(!enabled) WarmWhite.copy(alpha=.38f) else if(selected) Brass else WarmWhite,
                            modifier=Modifier.background(
                                if(selected) Brass.copy(alpha=.20f) else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            ).padding(horizontal=12.dp, vertical=6.dp))
                    }
                }
            }
            TextButton(onClick={model.finishAdjustment();crop()}, enabled=ready&&!state.rendering,
                contentPadding=PaddingValues(horizontal=12.dp)) { Text("裁切 / 旋转") }
        }
    }
}

@Composable
private fun ExportDialog(model: EditorModel,onClose:()->Unit) {
    val context=LocalContext.current
    val batch=model.library.editorId==null
    fun share(uri: android.net.Uri) {
        val mime=context.contentResolver.getType(uri) ?: "image/*"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type=mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },"分享片屿照片"))
    }
    var moreSettings by remember { mutableStateOf(false) }
    val size = if(model.exportEdge==0) "原尺寸" else "长边 ${model.exportEdge}"
    val settingsReady = !model.recipesLoading
    AlertDialog(onDismissRequest=onClose, title={Text(if(batch) "批量保存 ${model.library.selected.size} 张" else "导出照片")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                    Text(if(settingsReady) "$size · ${model.exportFormat.name}" else "正在读取导出设置…",
                        modifier=Modifier.weight(1f), style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={moreSettings=!moreSettings}, enabled=settingsReady) {
                        Text(if(moreSettings) "收起设置" else "更多设置")
                    }
                }
                if(moreSettings) {
                    Text("尺寸", color=Brass)
                    listOf(0 to "原尺寸 · 保留输入像素", 2048 to "缩小 · 长边 2048", 1080 to "缩小 · 长边 1080").forEach { (edge, label) ->
                        ExportChoice(label, model.exportEdge==edge) { model.chooseExportEdge(edge) }
                    }
                    Text("格式", color=Brass)
                    listOf(ExportFormat.JPEG to "JPEG · 最高质量（仍有损）",
                        ExportFormat.PNG to "PNG · 无损像素（文件较大）",
                        ExportFormat.HEIF to "HEIF · 高效存储（设备支持时）").forEach { (format, label) ->
                        ExportChoice(label, model.exportFormat==format) { model.chooseExportFormat(format) }
                    }
                    Text("编辑后输出为 8-bit sRGB，不覆盖原片、不保留 EXIF。HEIF 需要设备编码器，失败时请改选 JPEG/PNG；裁切会改变尺寸，小图不放大。", fontSize=12.sp)
                }
                if(!batch) {
                    TextButton(onClick={model.export(true,::share);onClose()}, enabled=settingsReady,
                        modifier=Modifier.fillMaxWidth()) { Text("分享编辑后照片") }
                    if(model.library.entries.firstOrNull{it.id==model.library.editorId}?.uri!=null) {
                        TextButton(onClick={model.shareOriginal(::share);onClose()},
                            modifier=Modifier.fillMaxWidth()) { Text("直接分享原片") }
                        Text("原片保留文件与元数据，不含编辑效果。", fontSize=12.sp)
                    }
                }
            }
        },
        confirmButton={
            Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally) {
                Button(onClick={if(batch) model.exportSelected() else model.export(false){};onClose()},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp), enabled=settingsReady) {
                    Text("保存到相册")
                }
                TextButton(onClick=onClose) { Text("取消") }
            }
        }
    )
}

@Composable
private fun ExportChoice(label: String, selected: Boolean, onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
        .selectable(selected=selected, role=Role.RadioButton, onClick=onClick)
        .padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
        RadioButton(selected=selected, onClick=null)
        Text(label, modifier=Modifier.padding(start=12.dp))
    }
}
