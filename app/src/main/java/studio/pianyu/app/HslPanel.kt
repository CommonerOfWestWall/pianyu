package studio.pianyu.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val hslNames = mapOf(
    HslColor.RED to "红", HslColor.ORANGE to "橙", HslColor.YELLOW to "黄", HslColor.GREEN to "绿",
    HslColor.CYAN to "青", HslColor.BLUE to "蓝", HslColor.PURPLE to "紫", HslColor.MAGENTA to "洋红"
)
private val hslSwatches = listOf(0xFFE96561,0xFFEAA25E,0xFFD5BC65,0xFF74AD82,
    0xFF65B9BA,0xFF729CDA,0xFFA18CD3,0xFFC87FA8)

@Composable
fun HslPanel(model: EditorModel) {
    var selected by remember { mutableStateOf(HslColor.RED) }
    val current = model.state.settings.hsl.channel(selected)
    val name = hslNames.getValue(selected)
    fun update(value: HslChannel) { model.edit(model.state.settings.copy(hsl = model.state.settings.hsl.withChannel(selected, value))) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(HslColor.values().toList()) { color ->
                FilterChip(selected = selected == color, onClick = { model.finishAdjustment(); selected = color },
                    leadingIcon = { Box(Modifier.size(12.dp).background(Color(hslSwatches[color.ordinal]),CircleShape)) },
                    label = { Text(hslNames.getValue(color)) }, modifier = Modifier.semantics { contentDescription = "选择${hslNames.getValue(color)}色" })
            }
        }
        HslSlider("${name} · 色相", current.hue, { update(current.copy(hue = it)) }, model)
        HslSlider("${name} · 饱和", current.saturation, { update(current.copy(saturation = it)) }, model)
        HslSlider("${name} · 明度", current.lightness, { update(current.copy(lightness = it)) }, model)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { update(HslChannel()); model.finishAdjustment() }) { Text("重置${name}色") }
            TextButton(onClick = { model.edit(model.state.settings.copy(hsl = HslSettings())); model.finishAdjustment() }) { Text("重置全部HSL") }
        }
    }
}

@Composable
private fun HslSlider(label: String, value: Float, onChange: (Float) -> Unit, model: EditorModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label)
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = model::finishAdjustment,
            valueRange = -1f..1f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                .semantics { contentDescription = label })
        Text((value * 100).roundToInt().toString())
    }
}
