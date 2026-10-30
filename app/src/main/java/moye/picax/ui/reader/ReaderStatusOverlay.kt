package moye.picax.ui.reader

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun ReaderStatusOverlay(position: String, stacked: Boolean, bottomInset: Int, glass: Boolean = true) {
    val context = LocalContext.current
    var time by remember { mutableStateOf("") }; var battery by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while(true) {
        time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val state = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        battery = (state?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / (state?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        delay(15_000)
    } }
    val alignment = when(position) { "topLeading" -> Alignment.TopStart; "topTrailing" -> Alignment.TopEnd; "bottomTrailing" -> Alignment.BottomEnd; else -> Alignment.BottomStart }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp).padding(top = 60.dp, bottom = (bottomInset + 12).dp), contentAlignment = alignment) {
        val content: @Composable () -> Unit = { Text(if(stacked) "$time\n$battery%" else "$time · $battery%", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 12.sp) }
        if(glass) io.github.neoruaa.glasmio.glass.GlassSurface(shape = Capsule(), navigationSurfaceColor = Color.Black.copy(alpha = .55f)) { content() }
        else Box(Modifier.background(Color.Black.copy(alpha = .55f), Capsule())) { content() }
    }
}
