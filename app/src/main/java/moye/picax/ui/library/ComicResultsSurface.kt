package moye.picax.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.scaffold.GlasmioPinnedPage
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*

@Composable
fun ComicResultsSurface(title: String, comics: List<Comic>, root: Boolean = false, filters: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}, titleContent: (@Composable () -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit = {}, footer: @Composable ColumnScope.() -> Unit = {}) {
    val app = LocalApp.current; val data = appData()
    val snapshot by produceState(ComicListSnapshot(), comics, data.rules, data.history, data.later, data.preferences) {
        value = withContext(Dispatchers.Default) { evaluateComics(comics, data, filters) }
    }
    var showsBlocked by remember { mutableStateOf(false) }
    val list = rememberLazyListState(); val grid = rememberLazyStaggeredGridState()
    val waterfall = data.preferences["list.layout"] == "waterfall"
    val rows = snapshot.visible
    val scrolled by remember(waterfall) { derivedStateOf {
        if(waterfall) grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 4
        else list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 4
    } }
    GlasmioPinnedPage(title = if(titleContent == null) title else "", onNavigateBack = if(root) null else app.navigator::back,
        navigateBackContentDescription = "返回", bottomPadding = if(root) 104.dp else 24.dp, actions = actions,
        topBarBlurProgress = if(scrolled) 1f else 0f) { padding ->
        val top: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                header()
                if(snapshot.hidden.isNotEmpty()) GlasmioListRow("已屏蔽 ${snapshot.hidden.size} 项 · 查看原因", systemName = "eye.slash", onClick = { showsBlocked = true })
            }
        }
        if(waterfall) LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(148.dp), state = grid, contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalItemSpacing = 12.dp) {
            item(span = StaggeredGridItemSpan.FullLine, key = "header") { top() }
            items(rows, key = { it.key }) { comic -> ComicTile(comic, comic.key in snapshot.crossRead) }
            item(span = StaggeredGridItemSpan.FullLine, key = "footer") { Column { footer() } }
        } else LazyColumn(state = list, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "header") { top() }
            items(rows, key = { it.key }) { comic -> GlasmioGroupedList { ComicContextRow(comic, detail = if(comic.key in snapshot.crossRead) "已在别的平台阅读" else null) } }
            item(key = "footer") { Column { footer() } }
        }
        if(titleContent != null) Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().height(54.dp).widthIn(max = 170.dp), contentAlignment = Alignment.Center) { titleContent() }
    }
    BlockedResultsSheet(showsBlocked, snapshot.hidden) { showsBlocked = false }
}

@Composable
private fun ComicTile(comic: Comic, crossRead: Boolean) {
    val app = LocalApp.current
    var ratio by remember(comic.cover) { mutableFloatStateOf(.73f) }
    ActionMenu(comicMenuActions(comic), morphFromAnchor = false) { open ->
        GlasmioGroupedList {
            Column(Modifier.combinedClickable(onClick = { app.navigator.detail(comic) }, onLongClick = open).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(comic.cover, "${comic.title} 封面", Modifier.fillMaxWidth().aspectRatio(ratio).clip(ContinuousRoundedRectangle(8.dp)), contentScale = ContentScale.Crop,
                    onSuccess = { val size = it.painter.intrinsicSize; if(size.width > 0 && size.height > 0) ratio = (size.width / size.height).coerceIn(.45f, 1.8f) })
                Text(comic.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 3)
                Text(listOf(comic.platform.title, if(comic.pages > 0) "${comic.pages} 页" else "").filter(String::isNotBlank).joinToString(" · "), fontSize = 11.sp, color = GlasmioTheme.colors.secondaryContent)
                if(crossRead) Text("已在别的平台阅读", fontSize = 11.sp, color = comic.platform.accent())
                if(comic.tags.isNotEmpty() && app.store.bool("list.tags", true)) Text(comic.tags.take(app.store.number("list.tagLimit", 5)).joinToString(" · ") { it.substringAfter(':') }, maxLines = 3, fontSize = 11.sp, color = comic.platform.accent())
            }
        }
    }
}

@Composable
fun BlockedResultsSheet(visible: Boolean, hidden: List<HiddenComic>, onDismiss: () -> Unit) {
    val app = LocalApp.current
    var reveal by remember { mutableStateOf(false) }
    AppSheet(visible, "屏蔽说明", onDismiss, actions = { IconAction("gearshape", "管理规则") { onDismiss(); app.navigator.open(Route("blocking")) } }) {
        item { Group { GlasmioListRow("临时显示本页屏蔽内容", trailing = { GlasmioSwitch(reveal, { reveal = it }) }) } }
        items(hidden, key = { it.comic.key }) { match -> Group {
            if(reveal) ComicContextRow(match.comic) else GlasmioListRow(match.comic.title, subtitle = "${match.comic.platform.title} · ${match.reason}", showDisclosure = false)
        } }
    }
}
