package moye.picax.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.rememberSharedGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCanvasBackdrop
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*
import moye.picax.ui.library.DetailCommentsSheet
import kotlin.math.abs

@Composable
fun ReaderPage(route: Route) {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    val initialComic = requireNotNull(route.comic)
    val session = remember(route.instance) { ReaderSession(app.repository, route.offline) }
    val scope = rememberCoroutineScope()
    val visitedBooks = remember { mutableSetOf<String>() }
    val list = rememberLazyListState()
    val pager = rememberPagerState { session.pages.size }
    var chrome by remember { mutableStateOf(true) }
    var auto by remember { mutableStateOf(false) }
    var contentsTab by remember { mutableStateOf<String?>(null) }
    var bookmarks by remember { mutableStateOf(false) }
    var progressSheet by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var lastVisibleKey by remember { mutableStateOf<String?>(null) }
    var activeMode by remember { mutableStateOf(app.store.setting("reader.mode", "continuous")) }
    var currentDetail by remember { mutableStateOf<ComicDetail?>(null) }
    var viewportHeight by remember { mutableFloatStateOf(800f) }
    val mode = preferences["reader.mode"] ?: "continuous"
    val continuous = mode == "continuous"
    val whole = app.store.bool("reader.whole") || continuous && app.store.bool("reader.multi")
    val crossBooks = app.store.bool("reader.nextBook") || continuous && app.store.bool("reader.multi") || route.queue.isNotEmpty() && app.store.bool("reader.listAuto", true)
    val currentIndex by remember(continuous) { derivedStateOf { if(continuous) list.firstVisibleItemIndex else pager.currentPage } }
    val visibleIndex by rememberUpdatedState(currentIndex)
    val wholeNow by rememberUpdatedState(whole)
    val crossBooksNow by rememberUpdatedState(crossBooks)
    val continuousNow by rememberUpdatedState(continuous)
    var jumpJob by remember { mutableStateOf<Job?>(null) }
    val ref = session.pages.getOrNull(currentIndex) ?: session.pages.firstOrNull()
    val comic = ref?.comic ?: initialComic
    val chapter = ref?.chapter ?: requireNotNull(route.chapter)
    val pageCount = ref?.let { session.chapterRange(it).let { range -> range.last - range.first + 1 } } ?: 0
    val page = ref?.index ?: 0
    suspend fun scroll(index: Int) {
        if(session.pages.isNotEmpty()) {
            if(continuous) list.scrollToItem(index.coerceIn(session.pages.indices)) else pager.scrollToPage(index.coerceIn(session.pages.indices))
        }
    }
    fun jump(book: Comic, target: Chapter, index: Int = 0, restore: Boolean = false) { jumpJob?.cancel(); jumpJob = scope.launch {
        ready = false
        val saved = session.open(book, target)
        scroll(if(restore) saved else index)
        ready = true
    } }
    fun move(index: Int) { scope.launch {
        if(index in session.pages.indices) {
            if(continuous) list.animateScrollToItem(index) else pager.animateScrollToPage(index)
        } else if(index >= session.pages.size && ref != null && app.store.bool("reader.autoChapter", true)) {
            session.adjacent(ref, 1, route.queue, crossBooks)?.let { jump(it.first, it.second) }
        }
    } }
    fun step(delta: Int, distance: Int = app.store.number("reader.tapDistance", 85)) {
        if(continuous) scope.launch {
            val consumed = list.animateScrollBy(viewportHeight * distance / 100f * delta)
            if(abs(consumed) < 1 && delta > 0 && app.store.bool("reader.autoChapter", true)) move(session.pages.size)
        } else move(currentIndex + delta)
    }
    fun tap(location: Offset) {
        val fraction = if(continuous || mode == "vertical") location.y else location.x
        val edge = app.store.number("reader.tapEdge", 28) / 100f
        if(app.store.bool("reader.tap") && (fraction < edge || fraction > 1 - edge)) {
            var delta = if(fraction < edge) -1 else 1
            if(mode == "rtl") delta = -delta
            if(app.store.bool("reader.tapInverted")) delta = -delta
            step(delta)
        } else if(app.store.setting("reader.uiToggle", "single") == "single") chrome = !chrome
    }
    LaunchedEffect(route.instance) { ready = false; scroll(session.open(initialComic, requireNotNull(route.chapter))); ready = true }
    LaunchedEffect(preferences["reader.comments"]) { session.refreshComments() }
    LaunchedEffect(mode) {
        if(ready && activeMode != mode) {
            ready = false
            val index = session.pages.indexOfFirst { it.key == lastVisibleKey }.coerceAtLeast(0)
            scroll(index)
            activeMode = mode
            ready = true
        }
    }
    LaunchedEffect(comic.key, ready) { if(ready) currentDetail = session.detail(comic) }
    LaunchedEffect(continuous, session.pages, ready) {
        if(!ready || activeMode != mode || session.pages.isEmpty()) return@LaunchedEffect
        snapshotFlow { if(continuous) list.firstVisibleItemIndex else pager.currentPage }.distinctUntilChanged().collect { index ->
            session.pages.getOrNull(index)?.let { entry ->
                lastVisibleKey = entry.key
                visitedBooks += entry.comic.key
                val range = session.chapterRange(entry)
                if(!route.offline || app.store.bool("downloads.recordHistory", true)) app.store.record(entry.comic, entry.chapter, entry.index, range.last - range.first + 1, entry.chapterCount)
            }
        }
    }
    LaunchedEffect(session) {
        snapshotFlow { listOf(visibleIndex, session.pages.size, wholeNow, ready) }.distinctUntilChanged().collect {
            if(ready && wholeNow && session.pages.isNotEmpty()) {
                val near = app.store.number("reader.nearEnd", 3)
                if(visibleIndex >= session.pages.size - near) session.extend(1, route.queue, crossBooksNow)
                if(visibleIndex < near && session.pages.first().chapter.order > 1) {
                    val index = visibleIndex; val offset = list.firstVisibleItemScrollOffset
                    ready = false
                    try {
                        val added = session.extend(-1, route.queue, crossBooksNow)
                        if(continuousNow) list.scrollToItem(index + added, offset) else pager.scrollToPage(index + added)
                    } finally { ready = true }
                }
            }
        }
    }
    LaunchedEffect(currentIndex, session.pages.size, ready) {
        if(!ready) return@LaunchedEffect
        val count = app.store.number("reader.preload", 3)
        if(!continuous) delay(app.store.number("reader.pagedDelay", 1200).toLong())
        val snapshot = session.pages
        supervisorScope { (currentIndex + 1..minOf(currentIndex + count, snapshot.lastIndex)).map { index -> launch {
            try { if(!snapshot[index].isComments) app.repository.images.file(snapshot[index].url, snapshot[index].comic.platform) }
            catch(e: CancellationException) { throw e } catch(_: Exception) { }
        } }.joinAll() }
        if(!whole && app.store.bool("reader.nextPreload") && currentIndex >= session.pages.size - app.store.number("reader.nearEnd", 3) && ref != null) {
            try { session.adjacent(ref, 1, route.queue, false)?.let { target -> withContext(Dispatchers.IO) { app.repository.pages(target.first, target.second, route.offline).take(count).forEach { url -> app.repository.images.file(url, target.first.platform) } } } }
            catch(e: CancellationException) { throw e } catch(_: Exception) { }
        }
    }
    ReaderEffects(comic, route, chrome, auto, continuous, list, viewportHeight, visitedBooks) { step(1, app.store.number("reader.autoDistance", 85)) }
    val dark = app.store.bool("reader.dark", true)
    val density = LocalDensity.current
    val commentBackdrop = rememberCanvasBackdrop { drawRect(if (dark) Color.Black else Color.White) }
    GlasmioBackdropHost(Modifier.fillMaxSize().background(if(dark) Color.Black else Color.White), sampledContent = {
        CompositionLocalProvider(LocalGlassBackdrop provides commentBackdrop, LocalBlurBackdrop provides commentBackdrop) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            SideEffect { viewportHeight = with(density) { maxHeight.toPx() } }
            when {
                session.loading || session.pages.isEmpty() && session.error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                session.pages.isEmpty() -> EmptyPage("加载失败", session.error.orEmpty(), action = "重试") { jump(comic, chapter, restore = true) }
                continuous -> LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(app.store.number("reader.gap", 0).dp), contentPadding = PaddingValues(top = app.store.number("reader.top", 115).dp, bottom = app.store.number("reader.bottom", 0).dp)) {
                    itemsIndexed(session.pages, key = { _, entry -> entry.key }) { _, entry ->
                        if(entry.isComments) Box(Modifier.fillMaxWidth().height(maxHeight)) { ReaderChapterComments(entry, route.offline) }
                        else ReaderImage(entry, app.repository.images, false, tap = ::tap, doubleTap = { chrome = !chrome })
                    }
                }
                mode == "vertical" -> VerticalPager(pager, Modifier.fillMaxSize()) { index ->
                    if(session.pages[index].isComments) ReaderChapterComments(session.pages[index], route.offline)
                    else ReaderImage(session.pages[index], app.repository.images, true, tap = ::tap, doubleTap = { chrome = !chrome })
                }
                else -> HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = mode == "rtl") { index ->
                    if(session.pages[index].isComments) ReaderChapterComments(session.pages[index], route.offline) else {
                    ReaderImage(session.pages[index], app.repository.images, true, modifier = if(mode == "curl") Modifier.readerPageCurl(pager, index) else Modifier, tap = ::tap, doubleTap = { chrome = !chrome })
                    }
                }
            }
        }
        }
    }, overlayContent = { source ->
        val sharedBackdrop = rememberSharedGlassBackdrop(source, source)
        CompositionLocalProvider(LocalGlassBackdrop provides sharedBackdrop, LocalBlurBackdrop provides sharedBackdrop) {
        if(app.store.bool("reader.status") && (chrome || !app.store.bool("reader.statusFollow"))) ReaderStatusOverlay(app.store.setting("reader.statusPosition", "bottomLeading"), app.store.setting("reader.statusStyle", "compact") == "stacked", app.store.number("reader.statusInset", 0) + if(chrome) 75 else 0, app.store.bool("reader.statusGlass", false))
        if(ref != null && app.store.bool("reader.pageNumber", true) && (chrome || !app.store.bool("reader.progressFollow"))) {
            val alignment = when(app.store.setting("reader.progressPosition", "trailing")) { "leading" -> Alignment.BottomStart; "center" -> Alignment.BottomCenter; else -> Alignment.BottomEnd }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = (app.store.number("reader.progressInset", 0) + if(chrome) 86 else 12).dp), contentAlignment = alignment) {
                ReaderProgressBadge(chapter.order - 1, ref.chapterCount, page, pageCount, app.store.setting("reader.progressStyle", "circular") == "circular", app.store.bool("reader.pageLabel", true), if(app.store.bool("reader.progressTap")) ({ progressSheet = true }) else null, app.store.number("reader.progressOpacity", 78) / 100f, app.store.bool("reader.progressGlass", false))
            }
        }
        if(chrome) CompositionLocalProvider(io.github.neoruaa.glasmio.core.LocalGlassColors provides io.github.neoruaa.glasmio.core.defaultGlassColors(dark, if (dark) Color.White else Color.Black)) {
            GlasmioTopBar(chapter.title, Modifier.statusBarsPadding(), navigation = { IconAction("chevron.left", "返回", app.navigator::back) }, actions = {
                IconAction("bookmark", "书签") { bookmarks = true }
                ReaderOptionsMenu(auto, route.offline, { auto = !auto }, { progressSheet = true }, { comments = true }, { app.navigator.detail(comic, route.offline) })
            })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                val chapters = currentDetail?.chapters.orEmpty(); val index = chapters.indexOfFirst { it.id == chapter.id }; val book = route.queue.indexOfFirst { it.key == comic.key }
                ReaderToolbar(auto, route.queue.isNotEmpty(), index > 0, index in 0 until chapters.lastIndex, book > 0, book in 0 until route.queue.lastIndex,
                    { auto = !auto }, { contentsTab = "chapters" }, { contentsTab = "queue" },
                    { chapters.getOrNull(index - 1)?.let { jump(comic, it) } }, { chapters.getOrNull(index + 1)?.let { jump(comic, it) } },
                    { route.queue.getOrNull(book - 1)?.let { jump(it, Chapter("1", "开始阅读"), restore = true) } },
                    { route.queue.getOrNull(book + 1)?.let { jump(it, Chapter("1", "开始阅读"), restore = true) } })
            }
        }
        if(ref != null && page == pageCount - 1 && app.store.bool("reader.endButton")) Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = app.store.number("reader.endHorizontal", 20).dp, vertical = app.store.number("reader.endVertical", 24).dp).padding(bottom = if(chrome) 86.dp else 0.dp), contentAlignment = when(app.store.setting("reader.endPosition", "bottomTrailing")) {
            "bottomLeading" -> Alignment.BottomStart; "topLeading" -> Alignment.TopStart; "topTrailing" -> Alignment.TopEnd; else -> Alignment.BottomEnd
        }) {
            GlasmioButton({ scope.launch { session.adjacent(ref, 1, route.queue, app.store.bool("reader.endNextBook"))?.let { jump(it.first, it.second) } } }) { Text("下一章") }
        }
        }
    })
    ReaderContentsSheet(contentsTab, currentDetail?.chapters.orEmpty(), chapter, route.queue, comic, { contentsTab = null }, { jump(comic, it) }, { jump(it, Chapter("1", "开始阅读"), restore = true) })
    ReaderBookmarksSheet(bookmarks, comic, chapter, page, ref?.url.orEmpty(), currentDetail?.chapters.orEmpty(), { bookmarks = false }) { bookmark ->
        val index = session.pages.indexOfFirst { it.comic.key == bookmark.comic.key && it.chapter.id == bookmark.chapter.id && it.index == bookmark.page }
        if(index >= 0) move(index) else jump(bookmark.comic, bookmark.chapter, bookmark.page)
    }
    ReaderProgressSheet(progressSheet, chapter, page, pageCount, { progressSheet = false }) { target -> ref?.let { move(session.chapterRange(it).first + target) } }
    DetailCommentsSheet(comments, comic, { comments = false }, chapter, route.offline)
}
