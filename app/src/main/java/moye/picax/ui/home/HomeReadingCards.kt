package moye.picax.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import moye.picax.ui.*
import moye.picax.ui.components.*
import java.time.LocalDate

@Composable
fun HomeReadingCards() {
    val app = LocalApp.current; val data = appData(); val today = LocalDate.now().toString()
    val grouped = remember(data.readingTime) { data.readingTime.groupBy { it.comic.key }.values.sortedByDescending { rows -> rows.maxOf { it.day } } }
    if(app.store.bool("home.time", true)) {
        Row(Modifier.fillMaxWidth().clickable { app.navigator.open(Route("statistics")) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今日 ${duration(data.readingTime.filter { it.day == today }.sumOf { it.seconds })}", fontSize = 13.sp)
            Text("累计 ${duration(data.readingTime.sumOf { it.seconds })}", fontSize = 13.sp)
        }
        if(grouped.isEmpty()) EmptyRow("暂无阅读时长", "阅读漫画后会在这里显示记录。", "timer")
        else LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(grouped.take(app.store.number("home.timeLimit", 6)), key = { it.first().comic.key }) { records ->
                val comic = records.first().comic
                Column(Modifier.width(92.dp).clickable { app.navigator.open(Route("statisticsDetail", comic = comic)) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Cover(comic, Modifier.size(92.dp, 124.dp))
                    Text(comic.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(duration(records.sumOf { it.seconds }), color = GlasmioTheme.colors.secondaryContent, fontSize = 12.sp)
                }
            }
        }
    } else GlasmioListRow("阅读时长", subtitle = "查看阅读统计", systemName = "timer", onClick = { app.navigator.open(Route("statistics")) })
}
private fun duration(seconds: Long) = if(seconds < 3600) "${seconds / 60} 分钟" else "${seconds / 3600} 小时 ${seconds % 3600 / 60} 分钟"
