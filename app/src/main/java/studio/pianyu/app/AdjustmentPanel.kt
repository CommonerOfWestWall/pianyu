package studio.pianyu.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private data class Adjustment(val name: String, val range: ClosedFloatingPointRange<Float> = -1f..1f,
    val get: (EditSettings) -> Float, val set: (EditSettings, Float) -> EditSettings)
private val adjustments = listOf(
    Adjustment("曝光", -2f..2f, { it.exposure }, { s,v -> s.copy(exposure=v) }),
    Adjustment("亮度", get={it.brightness}, set={s,v -> s.copy(brightness=v)}),
    Adjustment("对比", get={it.contrast}, set={s,v -> s.copy(contrast=v)}),
    Adjustment("饱和", get={it.saturation}, set={s,v -> s.copy(saturation=v)}),
    Adjustment("高光", get={it.highlights}, set={s,v -> s.copy(highlights=v)}),
    Adjustment("阴影", get={it.shadows}, set={s,v -> s.copy(shadows=v)}),
    Adjustment("色温", get={it.warmth}, set={s,v -> s.copy(warmth=v)}),
    Adjustment("色调", get={it.tint}, set={s,v -> s.copy(tint=v)}),
    Adjustment("褪色", 0f..1f, {it.fade}, {s,v -> s.copy(fade=v)}),
    Adjustment("颗粒", 0f..1f, {it.grain}, {s,v -> s.copy(grain=v)}),
    Adjustment("锐化", 0f..1f, {it.sharpen}, {s,v -> s.copy(sharpen=v)}),
    Adjustment("暗角", 0f..1f, {it.vignette}, {s,v -> s.copy(vignette=v)})
)
@Composable
fun AdjustmentPanel(model: EditorModel) {
    var selected by remember { mutableIntStateOf(0) }
    var hslMode by remember { mutableStateOf(false) }
    if (hslMode) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { model.finishAdjustment(); hslMode = false }, label = { Text("基础") })
                FilterChip(selected = true, onClick = {}, label = { Text("HSL分色") }, modifier = Modifier.semantics { contentDescription = "HSL分色调整" })
            }
            HslPanel(model)
        }
        return
    }
    val tool = adjustments[selected]
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        LazyRow(contentPadding=PaddingValues(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = false, onClick = { model.finishAdjustment(); hslMode = true }, label = { Text("HSL分色") }, modifier = Modifier.semantics { contentDescription = "HSL分色调整" }) }
            items(adjustments) { item -> FilterChip(selected=tool==item, onClick={ model.finishAdjustment(); selected=adjustments.indexOf(item) }, label={ Text(item.name) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(tool.name)
            Slider(value=tool.get(model.state.settings), onValueChange={model.edit(tool.set(model.state.settings,it))},
                onValueChangeFinished=model::finishAdjustment, valueRange=tool.range,
                modifier=Modifier.weight(1f).padding(horizontal=12.dp).semantics { contentDescription=tool.name+"调整" })
            Text(if (selected==0) "%.1f".format(tool.get(model.state.settings)) else (tool.get(model.state.settings)*100).roundToInt().toString())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={model.edit(tool.set(model.state.settings,0f));model.finishAdjustment()}) { Text("重置${tool.name}") }
            TextButton(onClick=model::reset) { Text("还原全部 · 可撤销") }
        }
    }
}
