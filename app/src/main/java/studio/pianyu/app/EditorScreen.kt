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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val WarmWhite=Color(0xFFF4EBDD)
val Brass=Color(0xFFC7A269)
val Graphite=Color(0xFF20211F)
val Serif=FontFamily(Font(R.font.editor_serif))

private val editorTopBarHeight = 52.dp
private val editorBottomBarHeight = 56.dp

private val CinematicLookOrder = listOf(
    "cinema", "daily", "soft-daylight", "night", "retro", "golden-landscape",
    "blue-hour", "muted-teal-coast", "mist-coast", "cyan-travel", "natural",
    "olive-story", "forest-deep", "landscape", "softmono"
)

// Display order is independent of the indices persisted by LibraryStore.
internal val LookCards = Looks.withIndex().filter { it.value.id !in LookVariants }.sortedBy { (_, look) ->
    CinematicLookOrder.indexOf(look.id).takeIf { it >= 0 } ?: CinematicLookOrder.size
}

@Composable
fun EditorScreen(model: EditorModel, pickMediaFile: (() -> Unit)? = null, pickPhoto: () -> Unit) {
    var export by remember {mutableStateOf(false)}
    var crop by remember {mutableStateOf(false)}
    var about by remember {mutableStateOf(false)}
    var licenses by remember {mutableStateOf(false)}
    var diagnostic by remember {mutableStateOf(false)}
    var demoGuideRequested by rememberSaveable { mutableStateOf(false) }
    val snackbar=remember {SnackbarHostState()}
    var previewActionsHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val state=model.state
    val activity=LocalActivity.current
    val context=LocalContext.current
    val version=remember(context) {context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()}
    DisposableEffect(model.library.batch!=null,state.exporting) {
        if(model.library.batch!=null||state.exporting) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    LaunchedEffect(state.notice, state.error, state.recipeUndoId) {
        val error = state.error
        val notice = state.notice
        val undoId = state.recipeUndoId.takeIf { error == null }
        val message = error ?: notice ?: return@LaunchedEffect
        val action = when {
            error != null && model.diagnosticReport != null -> "查看"
            undoId != null -> "撤销"
            else -> null
        }
        val result = snackbar.showSnackbar(message, actionLabel = action, withDismissAction = error != null,
            duration = if (error != null) SnackbarDuration.Long else SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) {
            if (undoId != null) model.undoRecipeApplication(undoId) else diagnostic = true
        }
        if (model.state.notice == notice) model.clearNotice()
        if (model.state.error == error) model.clearError()
    }
    BackHandler(enabled=model.library.editorId!=null&&!crop&&!export) {
        demoGuideRequested = false
        model.backToLibrary()
    }
    Surface(Modifier.fillMaxSize(),color=Graphite) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
            val windowHeight = maxHeight + safeInsets.calculateTopPadding() + safeInsets.calculateBottomPadding()
            val panelHeight = (maxHeight - editorTopBarHeight - editorBottomBarHeight - 1.dp - windowHeight * .45f)
                .coerceIn(0.dp, 264.dp)
            if (model.library.editorId == null) {
                LibraryScreen(model, pickPhoto, { export = true }, { about = true }, pickMediaFile,
                    openDemo = {
                        demoGuideRequested = true
                        model.loadDemo()
                    })
            } else {
                LoadedEditor(model, { export = true }, { crop = true }, cropOpen = crop,
                    panelHeight = panelHeight, demoGuide = demoGuideRequested && model.library.editorId == DEMO_PHOTO_ID,
                    dismissDemoGuide = { demoGuideRequested = false },
                    onPreviewActionsHeightChanged = { previewActionsHeight = with(density) { it.toDp() } })
            }
            SnackbarHost(snackbar,modifier=Modifier.align(Alignment.BottomCenter).padding(
                start=12.dp,end=12.dp,bottom=when {
                    model.library.editorId != null -> editorBottomBarHeight + 1.dp + panelHeight +
                        previewActionsHeight + 8.dp
                    model.library.selected.isNotEmpty()->80.dp
                    else->12.dp
                })) { data ->
                if (data.visuals.actionLabel == "撤销") {
                    Snackbar(modifier = Modifier.padding(horizontal = 12.dp), action = {
                        TextButton(onClick = data::performAction,
                            modifier = Modifier.semantics { contentDescription = "撤销套用配方" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) {
                            Text("撤销")
                        }
                    }) { Text(data.visuals.message) }
                } else Snackbar(data)
            }
        }
    }
    if(crop) CropEditor(model.state,onCancel={crop=false},onDone={model.setGeometry(it);crop=false})
    if(export) ExportDialog(model,onClose={export=false})
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("片屿 · 把片刻留成岛")},text={Text("${LookCards.size} 款主风格和 ${LookVariants.size} 款同系变体可用于 JPG、HEIF、手机 DNG、视频与 JPEG 实况照片。用胶片曝光、相纸和印放曝光继续调整风格。编辑和导出均在本机完成。\n\n$version · Android 10+ · arm64\n\n代码采用 GPL-3.0-or-later。胶片核心基于 SpektraLab。材料与 LUT 来自 Andrea Volpato 的 spektrafilm，采用 CC BY-SA 4.0。\ngithub.com/andreavolpato/spektrafilm\n\n内置静物示例由 AI 生成。",modifier=Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()))},dismissButton={Column {TextButton(onClick={about=false;licenses=true}){Text("开源许可")};if(model.diagnosticReport!=null) TextButton(onClick={about=false;diagnostic=true}){Text("处理诊断")}}},confirmButton={TextButton(onClick={about=false}){Text("知道了")}})
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
}

private enum class EditorPage(val label: String) {
    LOOK("风格"), ADJUST("调色"), CROP("构图"), RECIPE("配方")
}

@Composable
private fun LoadedEditor(model: EditorModel, export: ()->Unit, crop: ()->Unit, cropOpen: Boolean,
    panelHeight: Dp, demoGuide: Boolean, dismissDemoGuide: () -> Unit,
    onPreviewActionsHeightChanged: (Int) -> Unit) {
    val state=model.state
    var page by rememberSaveable(model.library.editorId) { mutableStateOf(EditorPage.LOOK) }
    val panelState = rememberSaveableStateHolder()
    val look = Looks[state.settings.lookIndex]
    var showLookInfo by remember { mutableStateOf(false) }
    var lookSort by rememberSaveable { mutableStateOf<LookSort?>(null) }
    val cards = remember(lookSort) { sortedLookCards(lookSort) }
    val primaryStyle = look.primaryStyle()
    val activeCard = cards.indexOfFirst { it.value.id == primaryStyle.id }
    val firstCard = (activeCard - 1).coerceAtLeast(0)
    val hasManualColorAdjustments = state.settings.hasManualAdjustments() ||
        state.settings.displayedStrength != .75f || state.settings.lookVersion == 0
    val lookListState = rememberLazyListState(initialFirstVisibleItemIndex = firstCard)
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
    val ready = state.source != null && !state.loading && !state.exporting
    fun prioritize(sort: LookSort?) {
        lookSort = sort
        lookListState.requestScrollToItem(0)
    }
    if (showLookInfo) {
        LookGuideDialog(
            look = look,
            sort = lookSort,
            onSort = { sort ->
                prioritize(sort)
                showLookInfo = false
            },
            onClose = { showLookInfo = false },
            finishDescription = "关闭片屿预设后，将强度调到 100 可查看完整胶片模型。自动测光可在「调色 → 胶片」关闭。",
            finishButton = {
                val finishEnabled = state.settings.lookVersion != 0
                TextButton(
                    onClick = { model.setLookFinish(!finishEnabled); showLookInfo = false },
                    enabled = ready
                ) {
                    Text(if (finishEnabled) "关闭片屿预设" else "启用片屿预设")
                }
            }
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(editorTopBarHeight).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val compactPadding = PaddingValues(horizontal = 8.dp)
            TextButton(onClick = { dismissDemoGuide(); model.backToLibrary() }, contentPadding = compactPadding) {
                Text("‹ 照片库")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = model::undo, enabled = state.canUndo && !state.exporting,
                contentPadding = compactPadding) { Text("撤销") }
            TextButton(onClick = model::redo, enabled = state.canRedo && !state.exporting,
                contentPadding = compactPadding) { Text("重做") }
            Button(onClick = export, enabled = ready, contentPadding = compactPadding) {
                Text(if (state.exporting) "保存中" else "导出")
            }
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
            if (demoGuide && ready) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Graphite.copy(alpha = .92f))
                    .padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("先按场景选风格，拖动强度或长按对比原图。", Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = dismissDemoGuide) { Text("知道了") }
                }
            }
            val status = when {
                state.loading -> "正在读取照片…"
                state.source == null -> "照片尚未打开"
                original || heldOriginal -> "原图预览"
                state.rendering -> "正在冲洗风格…"
                state.displayedSettings == null -> "正在准备风格…"
                else -> null
            }
            status?.let {
                Text(it, fontSize = 11.sp, color = WarmWhite,
                    modifier = Modifier.align(Alignment.BottomStart).background(Graphite.copy(alpha = .8f)).padding(8.dp))
            }
            Row(Modifier.align(Alignment.BottomEnd).onSizeChanged { onPreviewActionsHeightChanged(it.height) }
                .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (zoom > 1.05f) TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f },
                    modifier = Modifier.background(Graphite.copy(alpha = .8f), RoundedCornerShape(24.dp))) {
                    Text("恢复全图")
                }
                if (ready) TextButton(onClick = { original = !original },
                    modifier = Modifier.background(Graphite.copy(alpha = .8f), RoundedCornerShape(24.dp))) {
                    Text(if (original) "返回效果" else "对比原图")
                }
            }
        }
        if (page == EditorPage.LOOK) Column(Modifier.height(panelHeight)
            .verticalScroll(rememberScrollState()).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            LaunchedEffect(model.library.editorId, state.settings.lookIndex) {
                val layout=lookListState.layoutInfo
                val visible=layout.visibleItemsInfo.any {
                    it.index==activeCard && it.offset>=layout.viewportStartOffset &&
                        it.offset+it.size<=layout.viewportEndOffset
                }
                if(!visible) lookListState.scrollToItem(firstCard)
            }
            LookPriorityChips(lookSort, ::prioritize, ready,
                contentPadding = PaddingValues(horizontal = 16.dp))
            LazyRow(modifier = Modifier.selectableGroup(), state = lookListState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards,key={it.value.id}) { (index, look) ->
                    LaunchedEffect(model.library.editorId,state.rendering,index) {if(!state.rendering)model.requestLookThumbnail(index)}
                    val active = look.id == primaryStyle.id
                    Column(Modifier.width(62.dp)
                        .semantics {contentDescription="选择风格${look.name}"}
                        .selectable(selected=active,enabled=ready,role=Role.RadioButton){model.chooseLook(index)},
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(62.dp).border(if(active)2.dp else 0.dp,if(active)Brass else Color.Transparent,RoundedCornerShape(8.dp)).padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF353730)),contentAlignment=Alignment.Center) {
                            state.thumbnails[look.id]?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}?:Text("···",color=Brass)
                            if (look.variants().isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(3.dp)
                                .size(6.dp).background(Brass, CircleShape).border(.75.dp, Graphite, CircleShape))
                        }
                        Text(look.name,fontSize=11.sp,color=if(active)Brass else WarmWhite,maxLines=1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(look.name, Modifier.weight(1f), fontFamily = Serif, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val actionPadding = PaddingValues(horizontal = 7.dp)
                TextButton(onClick = { showLookInfo = true }, contentPadding = actionPadding) {
                    Text("风格说明", fontSize = 12.sp)
                }
                TextButton(onClick = model::applyRecommendedStart,
                    enabled = hasManualColorAdjustments && ready && !state.rendering,
                    contentPadding = actionPadding) {
                    Text("重置手动调色", fontSize = 12.sp)
                }
            }
            LookVariantChips(primaryStyle, state.settings.lookIndex, ready, model::chooseLook,
                Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("强度",fontSize=13.sp)
                Slider(state.settings.displayedStrength,model::updateStrength,onValueChangeFinished=model::finishAdjustment,enabled=ready,modifier=Modifier.weight(1f).padding(horizontal=12.dp).semantics{contentDescription="风格强度"})
                ParameterValue("风格强度", state.settings.displayedStrength, 0f..1f,
                    hint = if (state.settings.lookVersion >= 2) "75 为推荐强度，100 为加强效果" else null) {
                    model.updateStrength(it); model.finishAdjustment()
                }
            }
        } else if (page == EditorPage.ADJUST) {
            panelState.SaveableStateProvider(EditorPage.ADJUST.name) {
                Column(Modifier.height(panelHeight).verticalScroll(rememberScrollState())) {
                    AdjustmentPanel(model)
                }
            }
        } else panelState.SaveableStateProvider(EditorPage.RECIPE.name) {
            RecipePanel(model, Modifier.height(panelHeight))
        }
        HorizontalDivider(color = WarmWhite.copy(alpha = .12f))
        Row(Modifier.fillMaxWidth().height(editorBottomBarHeight).padding(horizontal = 8.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically) {
            EditorPage.entries.forEach { destination ->
                val selected = destination == if (cropOpen) EditorPage.CROP else page
                val enabled = destination == EditorPage.LOOK || ready && (destination != EditorPage.CROP || !state.rendering)
                val changed = when (destination) {
                    EditorPage.ADJUST -> state.settings.hasManualAdjustments()
                    EditorPage.CROP -> state.settings != state.settings.colorRecipe()
                    else -> false
                }
                Box(Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(selected = selected, enabled = enabled, role = Role.Tab) {
                        model.finishAdjustment()
                        if (destination == EditorPage.CROP) crop() else page = destination
                    }, contentAlignment = Alignment.Center) {
                    Box(Modifier.background(if (selected) Brass.copy(alpha = .20f) else Color.Transparent,
                        RoundedCornerShape(16.dp))) {
                        Text(destination.label, textAlign = TextAlign.Center,
                            color = if (!enabled) WarmWhite.copy(alpha = .38f) else if (selected) Brass else WarmWhite,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        if (changed) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(5.dp)
                            .background(Brass, RoundedCornerShape(50)))
                    }
                }
            }
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
