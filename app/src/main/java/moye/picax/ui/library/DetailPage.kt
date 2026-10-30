package moye.picax.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun DetailPage(comic: Comic, offline: Boolean = false) {
    val app = LocalApp.current
    val data = appData()
    val scope = rememberCoroutineScope()
    var liking by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ComicDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var translatedTags by remember { mutableStateOf(emptyMap<String, String>()) }
    val item = detail?.comic ?: comic
    val history = data.history.firstOrNull { it.comic.key == comic.key }
    val local = data.downloads.any { it.detail.comic.key == comic.key && it.status == "已完成" }
    val canRead = detail?.chapters?.isNotEmpty() == true
    val chaptersAsSection = data.preferences["detail.chapterSection"] == "true"
    val chaptersDescending = data.preferences["detail.chapterDescending"] == "true"
    val showsTags = data.preferences["detail.tags"] != "false"
    val showsRelated = data.preferences["detail.related"] != "false"
    val copyAction = item.copyAction()
    val sectionOrder = normalizedOrder(data.preferences["detail.order"], detailSectionTitles.keys)
    val coverColor by produceState(item.platform.accent(), item.cover, data.preferences["detail.coverAccent"]) {
        value = item.platform.accent()
        if(data.preferences["detail.coverAccent"] != "false") runCatching { app.repository.coverPalette.color(item) }.getOrNull()?.let { value = Color(it) }
    }
    var copied by remember { mutableStateOf(false) }

    fun read(chapter: Chapter? = null, fromStart: Boolean = false) {
        val loaded = detail ?: return
        val target = chapter ?: loaded.chapters.firstOrNull { it.id == history?.chapter?.id } ?: loaded.chapters.firstOrNull() ?: return
        if(fromStart) app.store.record(item, target, 0, 0)
        app.navigator.reader(item, target, offline = offline)
    }
    fun copyTitle() {
        app.store.context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("漫画标题", item.title))
    }
    LaunchedEffect(comic.key, retry) {
        error = null
        try {
            if(retry > 0) app.repository.invalidateDetail(comic)
            val loaded = withContext(Dispatchers.IO) { app.repository.detail(comic, offline) }
            detail = withContext(Dispatchers.IO) { loaded.copy(tagGroups = loaded.tagGroups.map { it.copy(title = app.repository.tags.groupTitle(it.title)) }) }
            app.store.update { state -> state.copy(followUpdates = state.followUpdates.map { if(it.comic.key == comic.key) it.copy(hasUpdate = false) else it }) }
            translatedTags = withContext(Dispatchers.IO) { (loaded.comic.tags + loaded.tagGroups.flatMap { it.tags }).associateWith { app.repository.tags.translate(it) } }
            if(history == null) app.store.record(loaded.comic, loaded.chapters.firstOrNull() ?: Chapter("1", "阅读"))
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message }
    }
    Page(if(offline) "已下载详情" else "漫画详情", largeTitle = false, actions = {
        if(item.platform != Platform.local) {
            IconAction("square.stack.3d.up", "其他来源与版本") { app.navigator.open(Route("versions", comic = item)) }
            IconAction("arrow.down.circle", "下载") { if(canRead) sheet = "download" }
        }
        if(!offline && item.platform in listOf(Platform.picacg, Platform.jmComic)) GlasmioIconButton(onClick = {
            scope.launch {
                liking = true
                try {
                    withContext(Dispatchers.IO) { app.repository.source(item.platform).like(item) }
                    detail = detail?.let { it.copy(isLiked = !it.isLiked, comic = it.comic.copy(likes = (it.comic.likes + if(it.isLiked) -1 else 1).coerceAtLeast(0))) }
                } catch(e: CancellationException) { throw e } catch(e: Exception) { actionError = e.message }
                finally { liking = false }
            }
        }, enabled = canRead && !liking) { io.github.neoruaa.glasmio.icons.SfIcon(if(detail?.isLiked == true) "hand.thumbsup.fill" else "hand.thumbsup", if(detail?.isLiked == true) "取消点赞" else "点赞") }
        IconAction("heart", "收藏") { if(canRead) sheet = "favorite" }
        ActionMenu(listOf(
            MenuAction("复制标题", "doc.on.doc", onClick = ::copyTitle),
            MenuAction(if(data.later.any { it.key == item.key }) "移出稍后再读" else "稍后再读", "bookmark", onClick = { app.store.later(item) }),
            MenuAction("忽略阅读进度", "arrow.counterclockwise", enabled = canRead, onClick = { read(fromStart = true) }),
            MenuAction("刷新详情", "arrow.clockwise", onClick = { retry++ }),
        )) { open -> IconAction("ellipsis", "详情操作", open) }
    }) {
        item {
            Group {
                ActionMenu(listOf(
                    MenuAction("复制标题", "doc.on.doc", onClick = ::copyTitle),
                    MenuAction("忽略阅读进度", "arrow.counterclockwise", enabled = canRead, onClick = { read(fromStart = true) }),
                ), morphFromAnchor = false) { open ->
                    DetailHeader(item, history, canRead, local, onRead = { read() }, onCover = { sheet = "cover" }, onChapters = { sheet = "chapters" }, onLongClick = open, showsChapterButton = !chaptersAsSection, buttonAccent = coverColor, onLocalRead = {
                        val downloaded = data.downloads.filter { it.detail.comic.key == item.key && it.status == "已完成" }
                        (downloaded.firstOrNull { it.chapter.id == history?.chapter?.id } ?: downloaded.firstOrNull())?.let { app.navigator.reader(item, it.chapter, offline = true) }
                    })
                }
            }
        }
        when {
            error != null -> item { EmptyPage("加载失败", error!!, "exclamationmark.triangle", "重试") { retry++ } }
            detail == null -> item { Busy() }
            else -> sectionOrder.forEach { section -> when(section) {
                "comments" -> if(item.platform in listOf(Platform.picacg, Platform.jmComic, Platform.nhentai, Platform.eHentai)) item {
                    Group { GlasmioListRow("查看评论", systemName = "text.bubble", onClick = { sheet = "comments" }) }
                }
                "actions" -> if(copyAction != null) item { Group("操作") {
                    GlasmioListRow(copyAction.title, subtitle = copyAction.value, systemName = copyAction.symbol, showDisclosure = false, onClick = {
                        app.store.context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(copyAction.title, copyAction.value)); copied = true
                    })
                } }
                "chapters" -> if(chaptersAsSection) item { Column {
                    ChapterSectionHeader(detail!!.chapters.size)
                    Group { (if(chaptersDescending) detail!!.chapters.reversed() else detail!!.chapters).forEach { chapter -> ChapterRow(chapter, onClick = { read(chapter, true) }) } }
                } }
                "description" -> if(detail!!.description.isNotBlank()) item { Group("简介") {
                    SelectionContainer { Text(detail!!.description, Modifier.padding(16.dp), fontSize = 15.sp, lineHeight = 22.sp, color = GlasmioTheme.colors.secondaryContent) }
                } }
                "uploader" -> detail!!.uploader?.let { uploader -> item { Group("上传者") {
                    GlasmioListRow(uploader.name, subtitle = "Lv${uploader.level} ${uploader.title} · Exp ${uploader.experience}", leading = {
                        Box(Modifier.size(42.dp)) {
                            AsyncImage(uploader.avatar, null, Modifier.fillMaxSize())
                            if(uploader.avatarFrame.isNotBlank() && app.store.bool("picacg.avatarFrame", true)) AsyncImage(uploader.avatarFrame, null, Modifier.fillMaxSize())
                        }
                    }, onClick = { app.navigator.open(Route("search", parameter = "picacg:ca:${uploader.id}", platform = item.platform)) })
                    if(uploader.slogan.isNotBlank()) Text(uploader.slogan, Modifier.padding(16.dp), color = GlasmioTheme.colors.secondaryContent, fontSize = 13.sp)
                } } }
                "information" -> item { Group("信息") {
                    DetailInfoRow("来源", item.platform.title)
                    if(item.subtitle.isNotBlank()) DetailInfoRow("作者", item.subtitle)
                    if(item.pages > 0) DetailInfoRow("页数", "${item.pages} 页")
                    if(detail!!.updatedText.isNotBlank()) DetailInfoRow("更新", detail!!.updatedText)
                    DetailInfoRow("编号", item.id, monospaced = true)
                } }
                "tags" -> if(showsTags) (detail!!.tagGroups.ifEmpty { groupedTags(item.tags) }).forEach { group -> item {
                    Group(group.title) { FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.tags.forEach { tag -> GlasmioButton({ app.navigator.open(Route("search", parameter = tag, platform = item.platform)) }, size = GlasmioButtonSize.Small) {
                            Text(translatedTags[tag] ?: tag.substringAfterLast(':'), fontSize = 13.sp)
                        } }
                    } }
                } }
                "related" -> if(showsRelated && detail!!.related.isNotEmpty()) {
                    item { SectionHeader("相关推荐") }
                    items(detail!!.related.take(6), key = { it.key }) { related -> GlasmioGroupedList { ComicContextRow(related) } }
                }
            } }

        }
    }
    DetailChapterSheet(sheet == "chapters", detail?.chapters.orEmpty(), { sheet = null }) { read(it, true); sheet = null }
    DetailDownloadSheet(sheet == "download", detail, { sheet = null })
    DetailFavoriteSheet(sheet == "favorite", item, { sheet = null })
    DetailCoverSheet(sheet == "cover", item, { sheet = null })
    DetailCommentsSheet(sheet == "comments", item, { sheet = null })
    ErrorDialog(actionError) { actionError = null }
    if(copied) GlasmioAlertDialog({ copied = false }, "已复制", copyAction?.value, buttons = listOf(GlasmioAlertButtonSpec("好", { copied = false })))
}
