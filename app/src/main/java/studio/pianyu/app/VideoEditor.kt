package studio.pianyu.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val settings = EditSettings(lookIndex = lookIndex, strength = strength, grain = 0f,
        autoFilmExposure = false, filmExposure = exposure, printStock = stock, printExposure = printExposure)

    fun save(asMotion: Boolean) {
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

    Surface(Modifier.fillMaxSize(), color = Graphite) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (motion) "实况照片" else "视频调色", Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall, fontFamily = Serif)
                TextButton(onClick = back, enabled = !saving) { Text("返回") }
            }
            val frame = if (showOriginal) original else graded ?: original
            Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                if (frame != null) {
                    Image(frame.asImageBitmap(), if (motion) "封面预览" else "视频抽帧预览",
                        Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else if (previewError == null) CircularProgressIndicator()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(when {
                    showOriginal -> "原始画面"
                    graded == null -> "调色预览"
                    motion -> "调色后封面"
                    else -> "调色后抽帧"
                }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showOriginal = !showOriginal }, enabled = graded != null && !saving) {
                    Text(if (showOriginal) "看调色" else "看原片")
                }
            }
            val enabled = original != null && !saving
            MediaChoice("胶片风格", look.name, enabled) { close ->
                LookCards.forEach { (index, item) ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = {
                        lookIndex = index
                        stock = ""
                        close()
                    })
                }
            }
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
            MediaSlider("胶片曝光", exposure, -2f..2f, enabled, ev = true) { exposure = it }
            if (!look.positive) {
                MediaSlider("印放曝光", printExposure, -2f..2f, enabled, ev = true) { printExposure = it }
            }
            MediaSlider("风格强度", strength, 0f..1f, enabled) { strength = it }
            Text("以固定曝光调色，导出保留音轨。", style = MaterialTheme.typography.bodySmall)
            if (saving) {
                if (progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(if (progress < 0) "正在准备" else "正在导出 · $progress%", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { job?.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("取消导出") }
            } else {
                val ready = enabled && gradedSettings == settings
                Button(onClick = { save(motion) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text(if (motion) "保存实况照片" else "保存视频")
                }
                if (motion) OutlinedButton(onClick = { save(false) }, enabled = ready,
                    modifier = Modifier.fillMaxWidth()) { Text("另存为 MP4") }
            }
            if (saved) Text("已保存到相册")
            (saveError ?: previewError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MediaChoice(label: String, value: String, enabled: Boolean,
    choices: @Composable ColumnScope.(() -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()
            .semantics { contentDescription = label }) { Text("$label · $value") }
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

