package moye.picax.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun SearchTargetMenu(selected: List<Platform>, onSelected: (List<Platform>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var aggregate by remember { mutableStateOf(Platform.online) }
    GlasmioMenu(expanded, { expanded = it }, itemCount = 14, menuWidth = 260.dp, anchor = { open ->
        Row(Modifier.height(44.dp).clickable(onClick = open), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if(selected.size == 1) selected.first().title else "${selected.size} 个平台聚合", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SfIcon("chevron.down", "搜索平台", size = 12.dp)
        }
    }) { backdrop, close ->
        GlasmioMenuItem("多平台聚合", { onSelected(aggregate); close() }, systemName = if(selected.size > 1) "checkmark" else "square.grid.2x2", backdrop = backdrop)
        Platform.online.forEach { platform -> GlasmioMenuItem(platform.title, {
            val next = if(platform in aggregate) aggregate - platform else aggregate + platform
            if(next.isNotEmpty()) { aggregate = next; if(selected.size > 1) onSelected(next) }
        }, systemName = if(platform in aggregate) "checkmark.circle" else "circle", backdrop = backdrop) }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = GlasmioTheme.colors.separator)
        Platform.online.forEach { platform -> GlasmioMenuItem(platform.title, { onSelected(listOf(platform)); close() },
            systemName = if(selected == listOf(platform)) "checkmark" else platform.symbol, backdrop = backdrop) }
    }
}

@Composable
fun SearchOptionsSheet(visible: Boolean, platforms: List<Platform>, sort: String, language: String, onSort: (String) -> Unit, onLanguage: (String) -> Unit, onDismiss: () -> Unit) {
    val sorts = if(platforms == listOf(Platform.picacg)) listOf("dd", "da", "ld", "vd")
        else if(platforms == listOf(Platform.jmComic)) listOf("mr", "mv", "mp", "tf")
        else listOf("date", "popular-today", "popular-week", "popular-month", "popular")
    AppSheet(visible, "高级选项", onDismiss) {
        item { Group("排序") { ChoiceMenuRow("排序方式", sort, sorts.map { it to sortTitle(it) }, onSort) } }
        if(Platform.nhentai in platforms) item { Group("NHentai") {
            ChoiceMenuRow("语言", language, listOf("" to "全部", "chinese" to "中文", "japanese" to "日文", "english" to "英文"), onLanguage)
        } }
    }
}

@Composable
fun SavedSearchesSheet(visible: Boolean, onDismiss: () -> Unit, onAdd: () -> Unit, onSelect: (SearchRecord) -> Unit) {
    val app = LocalApp.current
    val data = appData()
    val records = data.searches.filter { it.savedName.isNotEmpty() }.sortedByDescending { it.pinned }
    AppSheet(visible, "常用搜索", onDismiss, actions = { IconAction("plus", "保存当前搜索", onAdd) }) {
        if(records.isEmpty()) item { Group { EmptyRow("暂无常用搜索", "点击右上角加号保存当前搜索。", "bookmark") } }
        items(records, key = { "${it.savedName}:${it.updatedAt}" }) { record ->
            ActionMenu(listOf(
                MenuAction(if(record.pinned) "取消置顶" else "置顶", "pin", onClick = { app.store.update { it.copy(searches = it.searches.map { r -> if(r == record) r.copy(pinned = !r.pinned) else r }) } }),
                MenuAction("删除", "trash", destructive = true, onClick = { app.store.update { it.copy(searches = it.searches - record) } }),
            ), morphFromAnchor = false) { open ->
                Group { GlasmioListRow(record.savedName, subtitle = record.query, onClick = { onSelect(record); onDismiss() }, trailing = { IconAction("ellipsis", "常用搜索操作", open) }) }
            }
        }
    }
}

internal fun sortTitle(value: String) = when(value) {
    "dd", "date", "mr" -> "最新发布"; "da" -> "最早发布"; "ld", "mp" -> "最多喜欢"; "vd", "mv" -> "最多阅读"; "tf" -> "最多收藏"
    "popular-today" -> "今日热门"; "popular-week" -> "本周热门"; "popular-month" -> "本月热门"; else -> "全部热门"
}
