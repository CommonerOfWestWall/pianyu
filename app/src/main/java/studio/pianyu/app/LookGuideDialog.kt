package studio.pianyu.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LookGuideDialog(
    look: Look,
    sort: LookSort?,
    onSort: (LookSort) -> Unit,
    onClose: () -> Unit,
    finishDescription: String? = null,
    finishButton: (@Composable () -> Unit)? = null
) {
    val guide = look.guidance
    val tags = guide.tags
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(look.name, fontFamily = Serif) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("点标签优先浏览", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LookSort.entries.filter { it in tags }.forEach { tag ->
                            LookChip(tag.label, sort == tag, enabled = true) { onSort(tag) }
                        }
                    }
                }
                LookGuideSection("参考来源", guide.source)
                LookGuideSection("胶片与色彩特点", guide.character)
                LookGuideSection("适合的环境与场景", guide.scenes)
                finishDescription?.let { Text(it, fontSize = 12.sp) }
            }
        },
        dismissButton = finishButton,
        confirmButton = { TextButton(onClick = onClose) { Text("明白了") } }
    )
}

@Composable
private fun LookGuideSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(content)
    }
}
