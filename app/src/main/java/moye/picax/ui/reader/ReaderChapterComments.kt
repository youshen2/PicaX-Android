package moye.picax.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.GlasmioButton
import kotlinx.coroutines.*
import moye.picax.model.Comment
import moye.picax.ui.LocalApp
import moye.picax.ui.components.*
import moye.picax.ui.library.CommentCard

@Composable fun ReaderChapterComments(ref: ReaderPageRef, offline: Boolean) {
    val app = LocalApp.current
    var comments by remember(ref.key) { mutableStateOf(emptyList<Comment>()) }
    var page by remember(ref.key) { mutableIntStateOf(1) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(!offline) }
    LaunchedEffect(ref.key, page, revision) {
        loading = true; error = null
        try {
            val rows = withContext(Dispatchers.IO) {
                if(offline) app.store.data.value.downloads.firstOrNull { it.detail.comic.key == ref.comic.key && it.chapter.id == ref.chapter.id }?.comments.orEmpty()
                else app.repository.source(ref.comic.platform).chapterComments(ref.comic, ref.chapter, page)
            }
            val new = rows.filterNot { row -> comments.any { it.id == row.id } }
            comments = if(page == 1) rows else comments + new
            more = !offline && new.isNotEmpty()
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
        finally { loading = false }
    }
    io.github.neoruaa.glasmio.core.GlasmioTheme(isDark = app.store.bool("reader.dark", true)) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("章节评论 · ${ref.chapter.title}") }
        items(comments, key = { it.id }) { CommentCard(it) }
        if(error != null) item { EmptyPage("评论加载失败", error.orEmpty(), action = "重试") { revision++ } }
        else if(loading) item { Busy() }
        else if(comments.isEmpty()) item { EmptyPage("暂无章节评论", "当前章节还没有可显示的评论。", "text.bubble") }
        else if(more) item { GlasmioButton({ page++ }, Modifier.fillMaxWidth()) { Text("加载更多评论") } }
    }
    }
}
