package moye.picax.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import moye.picax.ui.components.*

@Composable
fun ReaderToolbar(
    automatic: Boolean,
    hasQueue: Boolean,
    canPreviousChapter: Boolean,
    canNextChapter: Boolean,
    canPreviousBook: Boolean,
    canNextBook: Boolean,
    onAutomatic: () -> Unit,
    onChapters: () -> Unit,
    onQueue: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousBook: () -> Unit,
    onNextBook: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        GlasmioBottomToolbar(Modifier.width(if(hasQueue) 136.dp else 96.dp)) {
            ReaderToolbarButton(if(automatic) "timer.circle.fill" else "timer", if(automatic) "停止自动翻页" else "自动翻页", onAutomatic)
            ReaderToolbarButton("list.bullet", "章节", onChapters)
            if(hasQueue) ReaderToolbarButton("list.bullet.rectangle", "阅读列表", onQueue)
        }
        GlasmioBottomToolbar(Modifier.width(if(hasQueue) 176.dp else 96.dp)) {
            if(hasQueue) ReaderToolbarButton("backward.end", "上一本", onPreviousBook, canPreviousBook)
            ReaderToolbarButton("chevron.up", "上一章", onPreviousChapter, canPreviousChapter)
            ReaderToolbarButton("chevron.down", "下一章", onNextChapter, canNextChapter)
            if(hasQueue) ReaderToolbarButton("forward.end", "下一本", onNextBook, canNextBook)
        }
    }
}

@Composable
private fun ReaderToolbarButton(symbol: String, label: String, action: () -> Unit, enabled: Boolean = true) {
    GlasmioIconButton(action, enabled = enabled, size = 36.dp) { SfIcon(symbol, label, size = 22.dp) }
}

@Composable
fun ReaderProgressBadge(chapterIndex: Int, chapterCount: Int, page: Int, pageCount: Int, circular: Boolean, showPage: Boolean, onClick: (() -> Unit)?, opacity: Float = .78f, glass: Boolean = true) {
    val fraction = if(pageCount > 0) (page + 1f) / pageCount else 0f
    val content: @Composable () -> Unit = { Row(
        (if(glass) Modifier else Modifier.background(Color.White.copy(alpha = opacity), Capsule()))
            .then(if(onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if(circular) 7.dp else 13.dp, vertical = if(circular) 5.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if(circular) Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = .72f), trackColor = Color.Black.copy(alpha = .12f), strokeWidth = 3.dp)
            Text("${(fraction * 100).toInt()}%", color = Color.Black.copy(alpha = .82f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        } else SfIcon("book", null, tint = Color.Black.copy(alpha = .78f), size = 18.dp)
        if(showPage) Text("E${chapterIndex + 1}/${chapterCount.coerceAtLeast(1)} · P${page + 1}/$pageCount", color = Color.Black.copy(alpha = .72f), fontSize = if(circular) 11.sp else 14.sp, fontWeight = FontWeight.Medium)
    }
    }
    if(glass) io.github.neoruaa.glasmio.glass.GlassSurface(shape = Capsule(), navigationSurfaceColor = Color.White, navigationSurfaceAlphaMultiplier = opacity) { content() }
    else content()
}
