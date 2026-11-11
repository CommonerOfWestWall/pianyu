package studio.pianyu.app

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/** The displayed units are also the units accepted by the entry field. */
@Composable
fun ParameterValue(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                   exposure: Boolean = false, onCommit: (Float) -> Unit) {
    val scale = if (exposure) 1f else 100f
    fun display(v: Float) = if (exposure) String.format(Locale.ROOT, "%.2f", v)
        else (v * scale).roundToInt().toString()
    var entering by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    TextButton(onClick = { input = display(value); entering = true },
        modifier = Modifier.widthIn(min = 52.dp).semantics { contentDescription = "输入${label}数值" }) {
        Text(display(value))
    }
    if (entering) {
        val number = input.trim().replace('−', '-').toFloatOrNull()
        val valid = number != null && number.isFinite() && number / scale in range
        AlertDialog(onDismissRequest = { entering = false }, title = { Text("设置$label") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                    label = { Text(if (exposure) "$label EV" else label) },
                    supportingText = { Text("范围 ${display(range.start)} 至 ${display(range.endInclusive)}，按此单位输入") },
                    isError = !valid, modifier = Modifier.semantics { contentDescription = "${label}数值输入框" })
            },
            confirmButton = { TextButton(enabled = valid, onClick = {
                if (input != display(value)) onCommit(number!! / scale)
                entering = false
            }) { Text("应用") } },
            dismissButton = { TextButton(onClick = { entering = false }) { Text("取消") } })
    }
}
