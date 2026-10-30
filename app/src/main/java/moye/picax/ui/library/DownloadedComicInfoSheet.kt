package moye.picax.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun DownloadedComicInfoSheet(comic: Comic?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val data = appData()
    val records = data.downloads.filter { it.detail.comic.key == comic?.key }.sortedBy { it.chapter.order }
    val readable = records.filter { it.status == "已完成" }
    val history = data.history.firstOrNull { it.comic.key == comic?.key && it.pageCount > 0 }
    fun read(record: DownloadRecord, fromStart: Boolean = false) {
        if(fromStart) app.store.record(record.detail.comic, record.chapter)
        onDismiss()
        app.navigator.reader(record.detail.comic, record.chapter, offline = true)
    }
    AppSheet(comic != null, "已下载", onDismiss, closeIcon = true, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlasmioButton({ comic?.let { onDismiss(); app.navigator.detail(it, offline = true) } }, Modifier.weight(1f)) { Text("查看详情") }
            GlasmioButton({ (readable.firstOrNull { it.chapter.id == history?.chapter?.id } ?: readable.firstOrNull())?.let { read(it) } },
                Modifier.weight(1f), enabled = readable.isNotEmpty(), emphasis = GlassEmphasis.Prominent) { Text(if(history != null) "继续阅读" else "从头开始") }
        }
    }) {
        if(comic != null) item {
            Group {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(comic, Modifier.size(74.dp, 100.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(comic.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
                        if(comic.subtitle.isNotBlank()) Text(comic.subtitle, fontSize = 15.sp, color = GlasmioTheme.colors.secondaryContent, maxLines = 2)
                        Text("${records.size} 章 · ${records.sumOf { it.completed }} 页", fontSize = 12.sp, color = comic.platform.accent())
                    }
                }
            }
        }
        item { SectionHeader("章节") }
        items(records, key = { it.key }) { record ->
            GlasmioGroupedList { GlasmioListRow(record.chapter.title, subtitle = "${record.completed} / ${record.urls.size} 页 · ${record.status}", systemName = "doc.text", enabled = record.status == "已完成", onClick = { read(record, true) }, showDisclosure = false) }
        }
    }
}
