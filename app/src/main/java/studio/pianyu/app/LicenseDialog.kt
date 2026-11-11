package studio.pianyu.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LicenseDialog(onClose: () -> Unit) {
    val context=LocalContext.current
    val notices=remember {listOf(
        "GPL-3.0-or-later · 应用与引擎" to "GPL-3.0.txt",
        "SpektraLab · 来源与修改" to "SpektraLab-NOTICE.md",
        "spektrafilm · CC BY-SA 4.0" to "SPEKTRAFILM_LICENSE.txt",
        "AndroidX HeifWriter · Apache-2.0" to "AndroidX-HeifWriter-LICENSE.txt",
        "Material Icons · Apache-2.0" to "Material-Icons-LICENSE.txt",
        "Noto Serif SC · SIL OFL 1.1" to "Noto-Serif-SC-OFL.txt",
        "Android NDK · 运行时声明" to "Android-NDK-NOTICE.txt",
        "volk · MIT" to "volk-LICENSE.md"
    )}
    var selected by remember {mutableStateOf<Pair<String,String>?>(null)}
    val text=remember(selected) {selected?.let {context.assets.open("licenses/${it.second}").bufferedReader().use {reader->reader.readText()}}}
    AlertDialog(onDismissRequest=onClose,title={Text(selected?.first?:"开源许可")},text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            if(text!=null) Text(text,fontSize=12.sp)
            else notices.forEach {notice->TextButton(onClick={selected=notice}) {Text(notice.first)}}
        }
    },dismissButton={if(selected!=null) TextButton(onClick={selected=null}){Text("返回列表")}},
        confirmButton={TextButton(onClick=onClose){Text("关闭")}})
}
