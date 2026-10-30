package moye.picax.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.components.*

@Composable
fun DetailHeader(comic: Comic, history: History?, canRead: Boolean, local: Boolean, onRead: () -> Unit, onCover: () -> Unit, onChapters: () -> Unit, onLongClick: () -> Unit, showsChapterButton: Boolean = true, buttonAccent: androidx.compose.ui.graphics.Color = comic.platform.accent(), onLocalRead: () -> Unit = onRead) {
    val colors = GlasmioTheme.colors
    val accent = buttonAccent
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onCover, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Cover(comic, Modifier.size(102.dp, 136.dp).clickable(onClick = onCover))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(comic.title, fontSize = 20.sp, lineHeight = 25.sp)
            if(comic.subtitle.isNotBlank()) Text(comic.subtitle, fontSize = 15.sp, lineHeight = 20.sp, color = colors.secondaryContent)
            Text(comic.platform.title, fontSize = 12.sp, color = colors.secondaryContent)
            if(comic.pages > 0) Text("${comic.pages} 页", fontSize = 12.sp, color = colors.secondaryContent)
            if(history != null && history.pageCount > 0) {
                val fraction = ((history.page + 1f) / history.pageCount).coerceIn(0f, 1f)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    SfIcon("book.circle", null, size = 13.dp, tint = accent)
                    Text("${history.chapter.title} · P${history.page + 1}", fontSize = 12.sp, color = accent, modifier = Modifier.weight(1f))
                    Text("${(fraction * 100).toInt()}%", fontSize = 12.sp, color = colors.secondaryContent)
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp), color = accent)
            }
            CompositionLocalProvider(LocalGlassColors provides colors.copy(accent = accent, prominentContainer = accent)) {
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlasmioButton(onRead, Modifier.weight(1f).height(40.dp), enabled = canRead, emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Medium) {
                        Text(if((history?.pageCount ?: 0) > 0) "继续阅读" else "阅读", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    if(local && comic.platform != Platform.local) GlasmioIconButton(onLocalRead, size = 40.dp) { SfIcon("internaldrive", "从本地阅读", size = 20.dp) }
                    if(showsChapterButton) GlasmioIconButton(onChapters, enabled = canRead, size = 40.dp) { SfIcon("list.bullet", "章节", size = 20.dp) }
                }
            }
        }
    }
}

@Composable
fun DetailInfoRow(title: String, value: String, monospaced: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.width(42.dp), fontSize = 15.sp, color = GlasmioTheme.colors.secondaryContent)
        Text(value, fontSize = if(monospaced) 13.sp else 15.sp, fontFamily = if(monospaced) FontFamily.Monospace else FontFamily.Default)
    }
}
