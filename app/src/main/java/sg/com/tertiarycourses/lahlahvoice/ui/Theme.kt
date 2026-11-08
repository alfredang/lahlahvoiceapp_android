package sg.com.tertiarycourses.lahlahvoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The same warm palette as LahLah Voice for Mac and iPhone. */
object Brand {
    val sand = Color(0xFFF3F1EB)
    val panel = Color(0xFFFCFBF8)
    val ink = Color(0xFF1C1B19)
    val muted = Color(0xFF6B6961)
    val ember = Color(0xFFE8743B)
    val emberSoft = Color(0xFFFBE3D6)
    val localBadge = Color(0xFFE6EBFB)
    val localText = Color(0xFF405CCC)
}

@Composable
fun LahLahTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand.ember, onPrimary = Color.White, background = Brand.sand,
            surface = Brand.panel, onSurface = Brand.ink, onBackground = Brand.ink,
            secondary = Brand.ink,
        ),
        content = content,
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.muted,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
fun ScreenTitle(text: String, subtitle: String) {
    Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
        Text(text, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Brand.ink)
        Text(subtitle, color = Brand.muted, fontSize = 15.sp)
    }
}
