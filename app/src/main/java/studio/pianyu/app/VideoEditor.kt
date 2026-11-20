package studio.pianyu.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VideoEditor(source: VideoIO.Source, back: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val motion = source.motion != null
    var lookIndex by rememberSaveable { mutableIntStateOf(1) }
    var stock by rememberSaveable { mutableStateOf("") }
    var showLookInfo by rememberSaveable { mutableStateOf(false) }
    var lookSort by rememberSaveable { mutableStateOf<LookSort?>(null) }
    val lookChoicesOpen = remember { mutableStateOf(false) }
    val cards = remember(lookSort) { sortedLookCards(lookSort) }
    var exposure by rememberSaveable { mutableFloatStateOf(0f) }
    var printExposure by rememberSaveable { mutableFloatStateOf(0f) }
    var strength by rememberSaveable { mutableFloatStateOf(.75f) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var graded by remember { mutableStateOf<Bitmap?>(null) }
    var gradedSettings by remember { mutableStateOf<EditSettings?>(null) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val look = Looks[lookIndex]
    val primaryStyle = look.primaryStyle()
    val settings = EditSettings(lookIndex = lookIndex, strength = strength, grain = 0f,
        autoFilmExposure = false, filmExposure = exposure, printStock = stock, printExposure = printExposure)

    fun chooseStyle(index: Int) {
        if (lookIndex == index) return
        lookIndex = index
        stock = ""
        printExposure = 0f
        strength = .75f
    }

    fun prioritize(sort: LookSort?) {
        lookSort = sort
        lookChoicesOpen.value = true
    }

    fun save(asMotion: Boolean) {
        if (saving) return
        saving = true
        saved = false
        saveError = null
        progress = -1
        job = scope.launch {
            try {
                VideoIO.save(context.applicationContext, source, settings, asMotion) { progress = it }
                saved = true
            } catch (error: CancellationException) { throw error }
            catch (error: OutOfMemoryError) { saveError = "内存不足，无法导出此文件" }
            catch (error: Exception) { saveError = error.localizedMessage ?: "导出失败" }
            finally { saving = false }
        }
    }

    BackHandler { if (!saving) back() }
    DisposableEffect(activity, saving) {
        if (saving) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (saving) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(source) {
        try { original = withContext(Dispatchers.IO) { VideoIO.preview(context, source) } }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { previewError = error.localizedMessage ?: "无法读取画面" }
    }
    LaunchedEffect(original, settings) {
        val frame = original ?: return@LaunchedEffect
        saved = false
        previewError = null
        delay(100)
        try {
            graded = VideoIO.grade(context, frame, settings)
            gradedSettings = settings
        }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { previewError = error.localizedMessage ?: "无法处理预览" }
    }

    val enabled = original != null && !saving
    val ready = enabled && gradedSettings == settings
    Surface(Modifier.fillMaxSize(), color = Graphite) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val previewHeight = (maxHeight * .45f).coerceAtMost(340.dp)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
                    val compactPadding = PaddingValues(horizontal = 8.dp)
                    TextButton(onClick = back, enabled = !saving, contentPadding = compactPadding,
                        modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回") }
                    Text(if (motion) "实况照片" else "视频调色", Modifier.align(Alignment.Center)
                        .padding(horizontal = 76.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { save(motion) }, enabled = ready, contentPadding = compactPadding,
                        modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(if (saving) "导出中" else "导出")
                    }
                }

                val frame = if (showOriginal) original else graded ?: original
                Box(Modifier.fillMaxWidth().height(previewHeight).background(Color(0xFF151613)),
                    contentAlignment = Alignment.Center) {
                    if (frame != null) {
                        Image(frame.asImageBitmap(), if (motion) "封面预览" else "视频抽帧预览",
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else if (previewError == null) CircularProgressIndicator(Modifier.size(28.dp))
                    if (graded != null && !saving) {
                        TextButton(onClick = { showOriginal = !showOriginal },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                .background(Graphite.copy(alpha = .8f), RoundedCornerShape(24.dp))) {
                            Text(if (showOriginal) "看调色" else "看原片")
                        }
                    }
                }
                HorizontalDivider(color = WarmWhite.copy(alpha = .12f))
                if (saving) {
                    if (progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (progress < 0) "正在准备" else "正在导出 · $progress%", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { job?.cancel() }) { Text("取消导出") }
                    }
                }
                if (saved) Text("已保存到相册", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                (saveError ?: previewError)?.let {
                    Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error)
                }
                Column(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clipToBounds()
                    .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LookPriorityChips(lookSort, ::prioritize, enabled)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MediaChoice("胶片风格", primaryStyle.name, enabled, modifier = Modifier.weight(1f),
                            expanded = lookChoicesOpen) { close ->
                            cards.forEach { (index, item) ->
                                DropdownMenuItem(text = { Text(item.name) }, onClick = {
                                    chooseStyle(index)
                                    close()
                                })
                            }
                        }
                        TextButton(onClick = { showLookInfo = true }, enabled = enabled,
                            contentPadding = PaddingValues(horizontal = 8.dp)) { Text("风格说明") }
                    }
                    LookVariantChips(primaryStyle, lookIndex, enabled, ::chooseStyle, label = "同系变体")
                    MediaSlider("胶片曝光", exposure, -2f..2f, enabled, ev = true) { exposure = it }
                    val materials = look.printStocks()
                    if (materials.isNotEmpty()) {
                        val selected = look.effectivePrintStock(stock)
                        val label = if (look.paper == "kodak_2383") "电影拷贝片" else "相纸"
                        MediaChoice(label, materials.firstOrNull { it.id == selected }?.name ?: selected,
                            enabled && materials.size > 1) { close ->
                            materials.forEach { item ->
                                DropdownMenuItem(text = { Text(item.name) }, onClick = { stock = item.id; close() })
                            }
                        }
                    }
                    if (!look.positive) {
                        MediaSlider("印放曝光", printExposure, -2f..2f, enabled, ev = true) { printExposure = it }
                    }
                    MediaSlider("风格强度", strength, 0f..1f, enabled) { strength = it }
                    Text("以固定曝光调色，导出保留音轨。", style = MaterialTheme.typography.bodySmall)
                    if (motion && !saving) OutlinedButton(onClick = { save(false) }, enabled = ready,
                        modifier = Modifier.fillMaxWidth()) { Text("另存为 MP4") }
                }
            }
        }
    }
    if (showLookInfo) LookGuideDialog(
        look = look,
        sort = lookSort,
        onSort = { sort ->
            showLookInfo = false
            prioritize(sort)
        },
        onClose = { showLookInfo = false }
    )
}

@Composable
private fun MediaChoice(label: String, value: String, enabled: Boolean,
    modifier: Modifier = Modifier,
    expanded: MutableState<Boolean> = remember { mutableStateOf(false) },
    choices: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var open by expanded
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = label }) {
            Text("$label · $value", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("▾", Modifier.padding(start = 8.dp).clearAndSetSemantics { })
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            choices { open = false }
        }
    }
}

@Composable
private fun MediaSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    enabled: Boolean, ev: Boolean = false, update: (Float) -> Unit) {
    Column {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(value, update, valueRange = range, enabled = enabled, modifier = Modifier.weight(1f)
                .semantics { contentDescription = label })
            if (enabled) ParameterValue(label, value, range, exposure = ev, onCommit = update)
        }
    }
}

