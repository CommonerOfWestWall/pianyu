package studio.pianyu.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class Adjustment(val name: String, val range: ClosedFloatingPointRange<Float> = -1f..1f,
    val get: (EditSettings) -> Float, val set: (EditSettings, Float) -> EditSettings)
private val filmExposure = Adjustment("胶片曝光", -2f..2f, { it.filmExposure }, { s,v -> s.copy(filmExposure=v) })
private val basicAdjustments = listOf(
    Adjustment("曝光", -2f..2f, { it.exposure }, { s,v -> s.copy(exposure=v) }),
    Adjustment("对比", get={it.contrast}, set={s,v -> s.copy(contrast=v)}),
    Adjustment("饱和", get={it.saturation}, set={s,v -> s.copy(saturation=v)}),
    Adjustment("色温", get={it.warmth}, set={s,v -> s.copy(warmth=v)}),
    Adjustment("亮度", get={it.brightness}, set={s,v -> s.copy(brightness=v)}),
    Adjustment("高光", get={it.highlights}, set={s,v -> s.copy(highlights=v)}),
    Adjustment("阴影", get={it.shadows}, set={s,v -> s.copy(shadows=v)}),
    Adjustment("色调", get={it.tint}, set={s,v -> s.copy(tint=v)}),
    Adjustment("褪色", 0f..1f, {it.fade}, {s,v -> s.copy(fade=v)}),
    Adjustment("颗粒", 0f..1f, {it.grain}, {s,v -> s.copy(grain=v)}),
    Adjustment("锐化", 0f..1f, {it.sharpen}, {s,v -> s.copy(sharpen=v)}),
    Adjustment("暗角", 0f..1f, {it.vignette}, {s,v -> s.copy(vignette=v)})
)
private enum class AdjustmentPage(val label: String, val weight: Float) {
    BASIC("基础", 1f),
    FILM("胶片曝光", 1.10f),
    PRINT("相纸与印放", 1.25f),
    HSL("HSL", .70f)
}

@Composable
fun AdjustmentPanel(model: EditorModel) {
    var selected by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf(AdjustmentPage.BASIC) }
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal=12.dp, vertical=4.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
            AdjustmentPage.entries.forEach { section ->
                val selected = page == section
                val filmControl = section == AdjustmentPage.FILM || section == AdjustmentPage.PRINT
                Surface(selected=selected, onClick={
                    model.finishAdjustment()
                    page=section
                }, modifier=Modifier.weight(section.weight).heightIn(min=48.dp).then(
                    if(section == AdjustmentPage.HSL) Modifier.semantics { contentDescription = "HSL分色调整" }
                    else Modifier
                ), shape=RoundedCornerShape(8.dp),
                    color=if(selected) Brass.copy(alpha=.22f) else Color.Transparent,
                    contentColor=if(selected || filmControl) Brass else WarmWhite,
                    border=BorderStroke(1.dp, if(filmControl || selected) Brass.copy(alpha=.55f)
                        else WarmWhite.copy(alpha=.24f))) {
                    Box(Modifier.padding(horizontal=5.dp, vertical=8.dp), contentAlignment=Alignment.Center) {
                        Text(section.label, textAlign=TextAlign.Center, style=MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        when (page) {
            AdjustmentPage.BASIC -> {
                val tool = basicAdjustments[selected]
                LazyRow(contentPadding=PaddingValues(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(basicAdjustments) { index, item ->
                        FilterChip(selected=selected==index, onClick={model.finishAdjustment();selected=index},
                            label={Text(item.name)})
                    }
                }
                AdjustmentControl(model, tool)
            }
            AdjustmentPage.FILM -> AdjustmentControl(model, filmExposure)
            AdjustmentPage.PRINT -> FilmPrintPanel(model)
            AdjustmentPage.HSL -> HslPanel(model)
        }
    }
}

@Composable
private fun AdjustmentControl(model: EditorModel, tool: Adjustment) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(tool.name)
            Slider(value=tool.get(model.state.settings), onValueChange={model.edit(tool.set(model.state.settings,it))},
                onValueChangeFinished=model::finishAdjustment, valueRange=tool.range,
                modifier=Modifier.weight(1f).padding(horizontal=12.dp).semantics { contentDescription=tool.name+"调整" })
            ParameterValue(tool.name, tool.get(model.state.settings), tool.range, exposure = tool.range == -2f..2f) {
                model.edit(tool.set(model.state.settings, it)); model.finishAdjustment()
            }
        }
        if (tool.name == "胶片曝光") MeteringSelector(model)
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={model.edit(tool.set(model.state.settings,0f));model.finishAdjustment()}) { Text("重置${tool.name}") }
            TextButton(onClick=model::reset) { Text("还原全部 · 可撤销") }
        }
        if (tool.name == "颗粒") Text("停顿即更新 · 放大观察细节；颗粒随风格强度变化", modifier=Modifier.padding(horizontal=24.dp), style=MaterialTheme.typography.labelSmall)
        if (tool.name == "胶片曝光") Text(if(model.state.settings.autoFilmExposure)
            "按所选方式先测光，再叠加上方EV补偿；0EV仅表示额外补偿为0。保护高光保留明亮处，保护阴影会抬亮暗处；关闭则保留原片曝光起点"
            else "关闭自动测光可保留原片曝光起点；上方EV在胶片响应前调整。夜景或舞台可保留现场明暗。EV为0表示额外补偿为0",
            modifier=Modifier.padding(horizontal=24.dp), style=MaterialTheme.typography.labelSmall)
        if (tool.name == "曝光") Text("整体提亮或压暗调色后的照片", modifier=Modifier.padding(horizontal=24.dp), style=MaterialTheme.typography.labelSmall)
    }
}
