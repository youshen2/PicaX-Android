package moye.picax.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.LocalApp
import moye.picax.ui.components.*

@Composable
fun DetailCommentsSheet(visible: Boolean, comic: Comic, onDismiss: () -> Unit, chapter: Chapter? = null, offline: Boolean = false) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var comments by remember(comic.key, chapter?.id) { mutableStateOf(emptyList<Comment>()) }
    var page by remember(comic.key, chapter?.id) { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var composing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue()) }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(visible, comic.key, chapter?.id, page, revision) {
        if(!visible) return@LaunchedEffect
        loading = true; error = null
        try {
            val result = withContext(Dispatchers.IO) {
                if(offline) app.store.data.value.downloads.firstOrNull { it.detail.comic.key == comic.key && (chapter == null || it.chapter.id == chapter.id) }?.comments.orEmpty()
                else if(chapter != null) app.repository.source(comic.platform).chapterComments(comic, chapter, page)
                else app.repository.source(comic.platform).comments(comic, page)
            }
            comments = if(page == 1) result else (comments + result).distinctBy { it.id }
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
        finally { loading = false }
    }
    AppSheet(visible, if(chapter == null) "评论" else "章节评论", onDismiss, closeLabel = "关闭", actions = {
        if(!offline && comic.platform in listOf(Platform.picacg, Platform.jmComic, Platform.eHentai)) IconAction("square.and.pencil", "写评论") { composing = true }
    }) {
        if(loading && comments.isEmpty()) item { Busy() }
        if(error != null) item { EmptyPage("加载失败", error!!, "exclamationmark.triangle", "重试") { revision++ } }
        if(!loading && error == null && comments.isEmpty()) item { EmptyPage("暂无评论", "还没有评论内容", "text.bubble") }
        items(comments, key = { it.id }) { comment ->
            CommentCard(comment)
        }
        if(!offline && comments.isNotEmpty()) item { GlasmioButton({ page++ }, Modifier.fillMaxWidth(), enabled = !loading) { Text(if(loading) "加载中…" else "加载更多") } }
    }
    AppSheet(composing, "写评论", { composing = false }, medium = false, closeLabel = "取消", actions = {
        GlasmioSheetButton(onClick = {
            scope.launch {
                sending = true
                try {
                    withContext(Dispatchers.IO) { app.repository.source(comic.platform).postChapterComment(comic, chapter, text.text.trim()) }
                    text = TextFieldValue(); composing = false; page = 1; revision++
                } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
                finally { sending = false }
            }
        }, enabled = text.text.isNotBlank() && !sending) { Text(if(sending) "发送中" else "发送") }
    }) {
        item { Group { GlasmioEditableText(text, { text = it }, Modifier.fillMaxWidth().height(260.dp).padding(16.dp), singleLine = false) } }
        if(error != null) item { Text(error!!, color = GlasmioTheme.colors.destructive) }
    }
}

@Composable
fun CommentCard(comment: Comment) {
    Group {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if(comment.avatar.isNotBlank()) Box(Modifier.size(38.dp)) {
                    coil3.compose.AsyncImage(comment.avatar, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    if(comment.avatarFrame.isNotBlank() && LocalApp.current.store.bool("picacg.avatarFrame", true)) coil3.compose.AsyncImage(comment.avatarFrame, null, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f)) {
                    Text(comment.author, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if(comment.time.isNotBlank()) Text(comment.time, fontSize = 12.sp, color = GlasmioTheme.colors.secondaryContent)
                }
            }
            androidx.compose.foundation.text.selection.SelectionContainer { Text(comment.content, fontSize = 15.sp, lineHeight = 22.sp) }
            if(comment.likes > 0 || comment.replies > 0) Text("${comment.likes} 赞 · ${comment.replies} 回复", fontSize = 12.sp, color = GlasmioTheme.colors.secondaryContent)
        }
    }
}
