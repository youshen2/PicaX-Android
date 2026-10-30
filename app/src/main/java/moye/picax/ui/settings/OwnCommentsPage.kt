package moye.picax.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable fun OwnCommentsPage(platform: Platform) {
    val app = LocalApp.current
    var rows by remember { mutableStateOf(emptyList<Comment>()) }
    var page by remember { mutableIntStateOf(1) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    LaunchedEffect(page, revision) {
        loading = true; error = null
        try {
            val result = app.repository.source(platform).ownCommentPage(page)
            rows = (if (page == 1) result.comments else rows + result.comments).distinctBy { it.id }
            more = result.hasMore
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    Page("我的评论", actions = { IconAction("arrow.clockwise", "刷新") { page = 1; revision++ } }) {
        items(rows, key = { it.id }) { comment -> Group {
            GlasmioListRow(comment.comic?.title ?: comment.author.ifBlank { "漫画" }, onClick = comment.comic?.let { comic -> { app.navigator.detail(comic) } })
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(comment.content, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("${comment.time} · ${comment.likes} 赞 · ${comment.replies} 回复", color = GlasmioTheme.colors.secondaryContent)
            }
        } }
        if (loading) item { Busy() }
        else if (error != null) item { EmptyPage("评论加载失败", error.orEmpty(), action = "重试") { revision++ } }
        else if (rows.isEmpty()) item { EmptyPage("暂无评论", "还没有发布评论", "text.bubble") }
        else if (more) item { GlasmioButton({ page++ }) { Text("加载更多") } }
    }
}
