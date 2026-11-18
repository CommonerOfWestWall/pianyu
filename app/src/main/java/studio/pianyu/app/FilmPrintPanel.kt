package studio.pianyu.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun MeteringSelector(model: EditorModel) {
    val settings = model.state.settings
    var choosing by remember { mutableStateOf(false) }
    val intent = MeteringIntents.firstOrNull { it.id == settings.autoFilmExposureMethod }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("自动测光", modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { choosing = true }, enabled = settings.autoFilmExposure && !model.state.loading,
                modifier = Modifier.semantics { contentDescription = "选择测光方式" }) {
                Text(if (settings.autoFilmExposure) intent?.label ?: "旧版中央加权" else "原片曝光")
            }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                MeteringIntents.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = {
                    choosing = false
                    model.edit(settings.copy(autoFilmExposureMethod = item.id)); model.finishAdjustment()
                }) }
            }
        }
        Switch(checked = settings.autoFilmExposure, onCheckedChange = { enabled ->
            model.edit(settings.copy(autoFilmExposure = enabled)); model.finishAdjustment()
        }, enabled = !model.state.loading, modifier = Modifier.semantics { contentDescription = "自动测光" })
    }
}

@Composable
fun FilmPrintPanel(model: EditorModel) {
    val settings = model.state.settings
    val look = Looks[settings.lookIndex]
    val stocks = look.printStocks()
    val active = look.effectivePrintStock(settings.printStock)
    val name = PrintStocks.firstOrNull { it.id == active }?.name ?: active
    var choosing by remember { mutableStateOf(false) }
    var more by remember(look.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (look.positive) {
            Text("正片直接扫描 · 不经过相纸", style = MaterialTheme.typography.bodyMedium)
            Text("此风格保留正片扫描路径，相纸和印放曝光不参与处理。需要改变底片响应，请回到「胶片曝光」。",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text(if (look.paper == "kodak_2383") "电影拷贝片" else "相纸", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { choosing = true }, enabled = stocks.size > 1 && !model.state.loading,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "选择冲印材料" }) {
                    Text(if (active == look.paper && settings.printStock.isEmpty()) "推荐 · $name" else name)
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    DropdownMenuItem(text = { Text("跟随风格推荐") }, onClick = {
                        choosing = false; model.edit(settings.copy(printStock = "")); model.finishAdjustment()
                    })
                    val common = setOf("kodak_ultra_endura", "fujifilm_crystal_archive_typeii", "kodak_2393")
                    stocks.filter { it.id != look.paper && (more || it.id in common || it.id == active) }.forEach { stock ->
                        DropdownMenuItem(text = { Text(stock.name) }, onClick = {
                            choosing = false; model.edit(settings.copy(printStock = stock.id)); model.finishAdjustment()
                        })
                    }
                    if (!more && stocks.any { it.id != look.paper && it.id !in common && it.id != active }) {
                        DropdownMenuItem(text = { Text("更多相纸…") }, onClick = { more = true })
                    }
                }
            }
            if (stocks.size == 1) Text("当前使用已验证的黑白相纸搭配", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("印放曝光")
                Slider(value = settings.printExposure, onValueChange = { model.edit(settings.copy(printExposure = it)) },
                    onValueChangeFinished = model::finishAdjustment, valueRange = -2f..2f,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "印放曝光调整" })
                ParameterValue("印放曝光", settings.printExposure, -2f..2f, exposure = true) {
                    model.edit(settings.copy(printExposure = it)); model.finishAdjustment()
                }
            }
            Text("EV · 改变相纸或拷贝片受到的曝光，正值不一定让成片变亮。胶片曝光与成片「曝光」仍可分别调节。",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                model.edit(settings.copy(printStock = "", printExposure = 0f)); model.finishAdjustment()
            }, enabled = !model.state.loading) { Text("恢复推荐冲印 · 可撤销") }
        }
    }
}
