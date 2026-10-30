package moye.picax.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.*
import moye.picax.model.*
import moye.picax.ui.*
import moye.picax.ui.components.*
import moye.picax.ui.library.DownloadedComicInfoSheet
import java.time.LocalDate

@Composable fun HomePage() {
    val app = LocalApp.current
    val data = appData()
    val nav = app.navigator
    var updatesExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val updated = data.followUpdates.filter { it.hasUpdate }
    var tool by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedDownload by remember { mutableStateOf<Comic?>(null) }
    val historyByComic = remember(data.history) { data.history.associateBy { it.comic.key } }
    val downloadsByComic = remember(data.downloads) { data.downloads.groupBy { it.detail.comic.key } }
    Page("主页", root = true, actions = { IconAction("gearshape", "设置") { nav.open(Route("settings")) } }) {
        val order = normalizedOrder(data.preferences["home.order"], homeSectionTitles.keys)
        order.forEach { section -> item(section) {
            when(section) {
                "history" -> HomeComicSection("历史记录", data.history.take(app.store.number("home.historyLimit", 10)).map { it.comic }, "暂无历史记录", "打开漫画详情后会在这里显示最近阅读。", "clock", "history", subtitle = { comic -> historyByComic[comic.key]?.progressText.orEmpty() })
                "later" -> HomeComicSection("稍后再读", data.later.take(app.store.number("home.laterLimit", 10)), "暂无稍后再读", "长按漫画列表条目，或在漫画详情页加入。", "bookmark", "later", subtitle = { comic -> data.laterAddedAt[comic.key]?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() } ?: "待阅读" })
                "updates" -> Group("追更") {
                    GlasmioListRow("追更", subtitle = if(app.store.bool("follow.enabled")) "自动检查收藏更新" else "启用后检查本地收藏的更新", detail = updated.size.takeIf { it > 0 }?.toString(), systemName = "sparkles", onClick = { nav.open(Route("updates", "追更")) })
                    if(app.store.bool("follow.enabled")) {
                        GlasmioListRow("有更新的漫画（${updated.size}）", systemName = if(updatesExpanded) "chevron.down" else "chevron.right", onClick = { updatesExpanded = !updatesExpanded })
                        androidx.compose.animation.AnimatedVisibility(updatesExpanded) { Column {
                            if(updated.isEmpty()) GlasmioListRow("当前没有更新", systemName = "checkmark.circle")
                            updated.take(10).forEach { entry -> GlasmioListRow(entry.comic.title, subtitle = "${entry.chapterCount} 章", onClick = { nav.detail(entry.comic) }) }
                        } }
                    }
                }
                "time" -> Group("阅读时长", headerActions = { HomeSectionLink("阅读时长", "statistics") }) { HomeReadingCards() }
                "downloads" -> HomeComicSection("下载", data.downloads.sortedByDescending { it.createdAt }.distinctBy { it.detail.comic.key }.take(app.store.number("home.downloadLimit", 8)).map { it.detail.comic }, "暂无下载", "漫画下载完成后会显示在这里。", "arrow.down.circle", "downloads",
                    onOpen = { selectedDownload = it }, subtitle = { comic -> downloadsByComic[comic.key].orEmpty().let { chapters -> "${chapters.size} 章 · ${chapters.sumOf { it.completed }} 页" } })
                "sources" -> Group("漫画源") {
                    if(app.store.bool("home.accounts", true)) GlasmioListRow("管理平台账号", subtitle = "登录、更新或退出平台账号", systemName = "person.2", onClick = { nav.open(Route("accounts")) })
                    data.accounts.forEach { account -> GlasmioListRow(account.platform.title, subtitle = account.username, systemName = account.platform.symbol,
                        onClick = { nav.open(Route("source", platform = account.platform)) }) }
                }
                "tools" -> Group("工具") {
                    GlasmioListRow("打开漫画链接", subtitle = "识别并打开支持的漫画链接", systemName = "link", onClick = { tool = "打开漫画链接" })
                    GlasmioListRow("JM 车牌号", subtitle = "输入编号直达漫画详情", systemName = "number", onClick = { tool = "JM 车牌号" })
                    GlasmioListRow("随机收藏", subtitle = "从本地收藏中随机选择一本", systemName = "shuffle", onClick = {
                        data.favorites.randomOrNull()?.comic?.let(nav::detail) ?: run { error = "本地收藏夹还没有漫画" }
                    })
                }
            }
        } }
    }
    tool?.let { name -> TextPrompt(name, if(name == "JM 车牌号") "例如：123456" else "粘贴漫画链接", dismiss = { tool = null }) { input ->
        runCatching { app.repository.fromLink(input) }.onSuccess(nav::detail).onFailure { error = it.message }
    } }
    ErrorDialog(error) { error = null }
    DownloadedComicInfoSheet(selectedDownload, { selectedDownload = null })
}

@Composable private fun HomeComicSection(title: String, comics: List<Comic>, empty: String, hint: String, symbol: String, route: String, onOpen: ((Comic) -> Unit)? = null, subtitle: (Comic) -> String = { it.platform.title }) {
    val app = LocalApp.current
    val preferences by app.store.preferences.collectAsState()
    Group(title, headerActions = { HomeSectionLink(title, if(route == "downloads") "downloads" else "library", route) }) {
        if(preferences["home.$route"] == "false") {
            GlasmioListRow("查看$title", systemName = symbol, onClick = { app.navigator.open(Route(if(route == "downloads") "downloads" else "library", title, route)) })
        } else if(comics.isEmpty()) {
            Box(Modifier.clickable { app.navigator.open(Route(if(route == "downloads") "downloads" else "library", title, route)) }) { EmptyRow(empty, hint, symbol) }
        } else {
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(comics, key = { it.key }) { comic -> Column(Modifier.width(92.dp).clickable { onOpen?.invoke(comic) ?: app.navigator.detail(comic) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Cover(comic, Modifier.size(92.dp, 124.dp))
                    Text(comic.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(comic), fontSize = 12.sp, color = GlasmioTheme.colors.secondaryContent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } }
            }
        }
    }
}
@Composable private fun HomeSectionLink(title: String, screen: String, parameter: String = "") {
    val nav = LocalApp.current.navigator
    GlasmioIconButton({ nav.open(Route(screen, title, parameter)) }, size = 28.dp) { SfIcon("chevron.right.circle", "查看全部$title", size = 20.dp, tint = GlasmioTheme.colors.secondaryContent) }
}
