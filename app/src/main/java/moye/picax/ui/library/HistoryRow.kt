package moye.picax.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import io.github.neoruaa.glasmio.core.GlasmioTheme
import moye.picax.model.*
import moye.picax.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun HistoryRow(history: History, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Cover(history.comic, Modifier.size(64.dp, 86.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(history.comic.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if(history.comic.subtitle.isNotBlank()) Text(history.comic.subtitle, fontSize = 15.sp, color = GlasmioTheme.colors.secondaryContent, maxLines = 1)
            Text("${history.comic.platform.title} · 编号 ${history.comic.id}", fontSize = 12.sp, color = GlasmioTheme.colors.secondaryContent, maxLines = 1)
            Text(historyDateFormat.format(Instant.ofEpochMilli(history.updatedAt).atZone(ZoneId.systemDefault())), fontSize = 12.sp, color = GlasmioTheme.colors.secondaryContent)
            Text(history.progressText, fontSize = 12.sp, color = if(history.pageCount > 0) history.comic.platform.accent() else GlasmioTheme.colors.secondaryContent)
        }
    }
}
