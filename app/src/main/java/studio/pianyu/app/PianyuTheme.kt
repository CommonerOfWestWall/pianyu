package studio.pianyu.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

private val PianyuColors = darkColorScheme(
    primary = Color(0xFFC7A269),
    onPrimary = Color(0xFF2A2118),
    secondary = Color(0xFFE7D8C0),
    onSecondary = Color(0xFF2B241D),
    secondaryContainer = Color(0xFF45463F),
    onSecondaryContainer = Color(0xFFE7D8C0),
    background = Color(0xFF20211F),
    onBackground = Color(0xFFF4EBDD),
    surface = Color(0xFF252522),
    onSurface = Color(0xFFF4EBDD),
    surfaceVariant = Color(0xFF363532),
    onSurfaceVariant = Color(0xFFD3C7B5),
    outline = Color(0xFF6D675E)
)

@Composable
fun PianyuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PianyuColors, content = content)
}
