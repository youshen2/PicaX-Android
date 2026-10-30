package moye.picax.ui.reader

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun ReaderContentsSheet(tab: String?, chapters: List<Chapter>, current: Chapter, queue: List<Comic>, comic: Comic, onDismiss: () -> Unit, onSelectChapter: (Chapter) -> Unit, onSelectBook: (Comic) -> Unit) {
    var selected by remember { mutableStateOf("chapters") }
    LaunchedEffect(tab) { if(tab != null) selected = tab }
    AppSheet(tab != null, if(selected == "chapters") "章节" else "阅读列表", onDismiss, closeLabel = "关闭") {
        if(queue.isNotEmpty()) item { GlasmioSegmentedControl(listOf("chapters" to "章节", "queue" to "阅读列表"), selected, { selected = it }, modifier = Modifier.fillMaxWidth()) }
        if(selected == "chapters") items(chapters, key = { it.id }) { chapter ->
            GlasmioGroupedList { GlasmioListRow(chapter.title, detail = if(chapter.id == current.id) "阅读中" else null, systemName = if(chapter.id == current.id) "book.fill" else "book", onClick = { onSelectChapter(chapter); onDismiss() }) }
        } else items(queue, key = { it.key }) { book ->
            GlasmioGroupedList { ComicRow(book, { if(book.key != comic.key) onSelectBook(book); onDismiss() }, detail = if(book.key == comic.key) "阅读中" else book.platform.title) }
        }
    }
}

@Composable
fun ReaderBookmarksSheet(visible: Boolean, comic: Comic, chapter: Chapter, page: Int, image: String, chapters: List<Chapter>, onDismiss: () -> Unit, onSelect: (Bookmark) -> Unit) {
    val app = LocalApp.current
    val data = appData()
    var note by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var editNote by remember { mutableStateOf("") }
    val id = "${comic.key}:${chapter.id}:$page"
    LaunchedEffect(visible, id) { if(visible) note = data.bookmarks.firstOrNull { it.id == id }?.note.orEmpty() }
    AppSheet(visible, "页级书签", onDismiss) {
        item {
            Group("当前位置") {
                GlasmioListRow("${chapter.title} · 第 ${page + 1} 页")
                GlasmioTextField(note, { note = it }, placeholder = "书签备注（可选）")
                GlasmioListRow("保存当前位置", titleColor = GlasmioTheme.colors.accent, onClick = {
                    val bookmark = Bookmark(id, comic, chapter, page, note, image)
                    app.store.update { it.copy(bookmarks = it.bookmarks.filterNot { old -> old.id == id } + bookmark) }
                })
            }
        }
        item { GlasmioSectionTitle("本书书签") }
        val bookmarks = data.bookmarks.filter { it.comic.key == comic.key }.sortedWith(compareBy({ it.chapter.order }, { it.page }))
        if(bookmarks.isEmpty()) item { Group { EmptyRow("暂无书签", "保存当前位置，记录阅读备注。", "bookmark") } }
        items(bookmarks, key = { it.id }) { bookmark ->
            ActionMenu(listOf(
                MenuAction("编辑备注", "pencil", onClick = { editing = bookmark; editNote = bookmark.note }),
                MenuAction("删除书签", "trash", destructive = true, onClick = { app.store.update { it.copy(bookmarks = it.bookmarks - bookmark) } }),
            ), morphFromAnchor = false) { open ->
                GlasmioGroupedList {
                    Row(Modifier.fillMaxWidth().combinedClickable(
                        onClick = { if(chapters.any { it.id == bookmark.chapter.id }) { onSelect(bookmark); onDismiss() } },
                        onLongClick = open,
                    ).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if(bookmark.image.isNotEmpty()) AsyncImage(bookmark.image, null, Modifier.size(42.dp, 58.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${bookmark.chapter.title} · 第 ${bookmark.page + 1} 页", fontSize = 16.sp)
                            if(bookmark.note.isNotBlank()) Text(bookmark.note, fontSize = 13.sp, color = GlasmioTheme.colors.secondaryContent)
                        }
                    }
                }
            }
        }
    }
    AppSheet(editing != null, "编辑书签", { editing = null }, closeLabel = "取消", actions = {
        GlasmioSheetButton({ editing?.let { previous -> app.store.update { it.copy(bookmarks = it.bookmarks.map { old -> if(old.id == previous.id) old.copy(note = editNote) else old }) } }; editing = null }) { Text("保存") }
    }) { item { Group { GlasmioTextField(editNote, { editNote = it }, placeholder = "备注") } } }
}

@Composable
fun ReaderProgressSheet(visible: Boolean, chapter: Chapter, page: Int, count: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    var target by remember { mutableIntStateOf(1) }
    val lastPage = count.coerceAtLeast(1)
    LaunchedEffect(visible, page) { if(visible) target = (page + 1).coerceIn(1, lastPage) }
    GlasmioBottomSheet(onDismiss, visible = visible, fitContent = true, title = "选择阅读进度") {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        io.github.neoruaa.glasmio.icons.SfIcon("slider.horizontal.3", null, size = 18.dp)
                        Text("选择阅读进度", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    }
                    Text(chapter.title, fontSize = 15.sp, color = GlasmioTheme.colors.secondaryContent, maxLines = 2)
                }
                Text("$target/$lastPage", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            if(count > 1) GlasmioSlider((target - 1f) / (count - 1), { target = kotlin.math.round(it * (count - 1)).toInt() + 1 })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("第 $target 页", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                GlasmioStepper(target, { target = it }, range = 1..lastPage, decreaseContentDescription = "上一页", increaseContentDescription = "下一页")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GlasmioButton(onDismiss) { Text("取消") }
                GlasmioButton({ onSelect(target - 1); onDismiss() }, emphasis = GlassEmphasis.Prominent, enabled = count > 0) { Text("跳转") }
            }
        }
    }
}
