package moye.picax.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.scaffold.GlasmioPinnedListPage
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.LocalApp

@Composable fun Page(title: String, root: Boolean = false, largeTitle: Boolean = true, state: LazyListState = rememberLazyListState(), titleContent: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: LazyListScope.() -> Unit) {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize()) {
        GlasmioPinnedListPage(title = if(titleContent == null) title else "", onNavigateBack = if(root) null else ({ app.navigator.back() }),
            navigateBackContentDescription = "返回", bottomPadding = if(root) 104.dp else 24.dp, horizontalContentPadding = 20.dp,
            listState = state, showsLargeTitle = largeTitle, actions = actions, content = content)
        if(titleContent != null) Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().height(54.dp).widthIn(max = 170.dp), contentAlignment = Alignment.Center) { titleContent() }
    }
}
@Composable fun IconAction(symbol: String, label: String, onClick: () -> Unit) {
    GlasmioIconButton(onClick = onClick) { SfIcon(symbol, label) }
}
@Composable fun SectionHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GlasmioSectionTitle(title, Modifier.weight(1f))
        actions()
    }
}
@Composable fun Group(title: String = "", footer: String = "", headerActions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if(title.isNotBlank()) SectionHeader(title, headerActions)
        GlasmioGroupedList(content = content)
        if(footer.isNotBlank()) Text(footer, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = GlasmioTheme.colors.secondaryContent, fontSize = 13.sp)
    }
}
@Composable fun EmptyRow(title: String, message: String, symbol: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SfIcon(symbol, null, size = 22.dp)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        Text(message, color = GlasmioTheme.colors.secondaryContent, fontSize = 13.sp)
    }
}
@Composable fun EmptyPage(title: String, message: String, symbol: String = "book", action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SfIcon(symbol, null, size = 54.dp, tint = GlasmioTheme.colors.secondaryContent)
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(message, fontSize = 15.sp, color = GlasmioTheme.colors.secondaryContent)
        if (action != null) GlasmioButton(onClick = onAction) { Text(action) }
    }
}
@Composable fun Busy() { Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable fun Cover(comic: Comic, modifier: Modifier = Modifier) {
    Box(modifier.clip(ContinuousRoundedRectangle(8.dp)).background(GlasmioTheme.colors.separator.copy(alpha = .3f)), contentAlignment = Alignment.Center) {
        if(comic.cover.isEmpty()) SfIcon("book", null, tint = GlasmioTheme.colors.secondaryContent)
        else AsyncImage(comic.cover, contentDescription = "${comic.title} 封面", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
@Composable fun ComicRow(comic: Comic, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, detail: String? = null) {
    val store = LocalApp.current.store
    val presentation by store.libraryPresentation.collectAsState()
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Cover(comic, Modifier.size(82.dp, 112.dp))
        Column(Modifier.weight(1f).heightIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(comic.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(comic.platform.title, Modifier.background(comic.platform.accent(), Capsule()).padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
            if(comic.subtitle.isNotBlank()) Text(comic.subtitle, fontSize = 15.sp, color = GlasmioTheme.colors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if(comic.pages > 0 && store.bool("list.pages", true)) ComicMetaPill("${comic.pages} 页")
                if(detail != null) ComicMetaPill(detail)
                else if(store.bool("list.progress", true)) presentation.history[comic.key]?.let { ComicMetaPill(it.progressText, "book") }
                if(comic.likes > 0 && store.bool("list.popularity", true)) ComicMetaPill("${comic.likes} 喜欢")
                if(store.bool("list.favorite", true) && presentation.favorites.containsKey(comic.key)) ComicMetaPill("已收藏", "star.fill")
            }
            if(comic.tags.isNotEmpty() && store.bool("list.tags", true)) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                comic.tags.take(store.number("list.tagLimit", 5)).forEach { tag ->
                    Text(tag.substringAfter(':'), Modifier.background(comic.platform.accent().copy(alpha = .12f), Capsule()).padding(horizontal = 7.dp, vertical = 3.dp), color = comic.platform.accent(), fontSize = 11.sp)
                }
            }
        }
    }
}
@Composable private fun ComicMetaPill(text: String, symbol: String? = null) {
    Row(Modifier.background(GlasmioTheme.colors.separator.copy(alpha = .2f), Capsule()).padding(horizontal = 7.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if(symbol != null) SfIcon(symbol, null, size = 11.dp, tint = GlasmioTheme.colors.secondaryContent)
        Text(text, color = GlasmioTheme.colors.secondaryContent, fontSize = 11.sp, maxLines = 1)
    }
}
@Composable fun ErrorDialog(error: String?, dismiss: () -> Unit) {
    if (error != null) GlasmioAlertDialog(dismiss, "操作失败", error, buttons = listOf(GlasmioAlertButtonSpec("好", dismiss)))
}
@Composable fun TextPrompt(title: String, placeholder: String = "", initial: String = "", dismiss: () -> Unit, confirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    GlasmioAlertDialog(dismiss, title, buttons = listOf(GlasmioAlertButtonSpec("取消", dismiss, GlasmioAlertButtonRole.Cancel), GlasmioAlertButtonSpec("确定", { confirm(value.trim()); dismiss() }, enabled = value.isNotBlank()))) {
        GlasmioTextField(value, { value = it }, placeholder = placeholder)
    }
}
fun Platform.accent(): Color = when(this) {
    Platform.picacg -> Color(0xFFFF2D55); Platform.jmComic -> Color(0xFFFF9500); Platform.nhentai -> Color(0xFFFF3B30)
    Platform.eHentai -> Color(0xFFAF52DE); Platform.hitomi -> Color(0xFF007AFF); Platform.htManga -> Color(0xFF30B0C7); Platform.local -> Color(0xFF5856D6)
}
@Composable fun PlatformPicker(selected: Platform, onSelected: (Platform) -> Unit) {
    Group("平台") {
        ChoiceMenuRow("平台", selected.name, Platform.online.map { it.name to it.title }, { onSelected(Platform.valueOf(it)) }, selected.symbol)
    }
}
